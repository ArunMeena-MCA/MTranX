package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.exception.UnmappableFieldException;
import com.wiredesk.mtmx.llm.GeminiClient;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import com.wiredesk.mtmx.mapping.model.MappingDocument;
import com.wiredesk.mtmx.parser.DecompositionService;
import com.wiredesk.mtmx.parser.ParsedMessage;
import com.wiredesk.mtmx.transform.TransformationEngine;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies every field_mappings entry from the mapping document to the
 * parsed source message, using deterministic code for the simple
 * transformations and the narrowly-scoped LLM paths only where the doc
 * explicitly opts in. Any source field with no matching rule is an
 * error under the default (recommended) unmapped_fields_policy - the
 * engine will not invent a mapping.
 */
@Service
public class ConverterService {

    private final TransformationEngine engine;
    private final DecompositionService decompositionService;
    private final GeminiClient llmClient;
    private final MtRenderer mtRenderer;
    private final MxRenderer mxRenderer;

    public ConverterService(TransformationEngine engine,
                             DecompositionService decompositionService,
                             GeminiClient llmClient,
                             MtRenderer mtRenderer,
                             MxRenderer mxRenderer) {
        this.engine = engine;
        this.decompositionService = decompositionService;
        this.llmClient = llmClient;
        this.mtRenderer = mtRenderer;
        this.mxRenderer = mxRenderer;
    }

    public ConvertedMessage convert(ParsedMessage parsed, MappingDocument doc) {
        checkUnmappedFields(parsed, doc);

        Map<String, String> tree = new LinkedHashMap<>();
        List<Map<String, Object>> trace = new java.util.ArrayList<>();
        List<String> conversionWarnings = new java.util.ArrayList<>();

        for (FieldMapping fm : doc.getFieldMappings()) {
            String sourceField = fm.getSourceField();
            String targetPath = fm.getTargetPath();
            String transformation = fm.getTransformation();

            // These two don't read from the source message at all - a
            // fixed value and a freshly-generated value respectively -
            // so they must run unconditionally, before any source-field
            // lookup/absence handling below.
            if ("constant".equals(transformation)) {
                String value = engine.constant(fm);
                tree.put(targetPath, value);
                trace.add(traceRow(sourceField, targetPath, value, transformation));
                continue;
            }
            if ("generated".equals(transformation)) {
                String value = engine.generate(fm);
                tree.put(targetPath, value);
                trace.add(traceRow(sourceField, targetPath, value, transformation));
                continue;
            }
            if ("conditional".equals(transformation)) {
                String value = engine.conditional(fm, parsed.getFields());
                if (value != null) {
                    tree.put(targetPath, value);
                    trace.add(traceRow(sourceField, targetPath, value, transformation));
                }
                continue;
            }

            String rawValue = parsed.getFields().get(sourceField);

            if (rawValue == null) {
                if (fm.isMandatory()) {
                    String def = doc.getDefaultValues().get(sourceField);
                    if (def != null) {
                        tree.put(targetPath, def);
                        trace.add(traceRow(sourceField, targetPath, def, "default_value"));
                        continue;
                    }
                    throw new UnmappableFieldException(sourceField,
                            "Field is declared mandatory in mapping doc but absent from the input message, "
                                    + "and no default_values entry exists. Refusing to invent a value.");
                }
                continue; // optional and absent - nothing to do
            }

            // Optional deterministic pre-processing: extract part of a composite
            // field (e.g. MT 32A's date/currency/amount) BEFORE the named
            // transformation runs, so composite fields don't need llm_assisted
            // just to pull out a fixed-position substring.
            if (fm.getExtractPattern() != null) {
                rawValue = engine.extractSubstring(rawValue, fm);
            }

            switch (transformation) {
                case "unsupported" -> engine.unsupported(fm); // always throws - see TransformationEngine
                case "no_op" -> {
                    // Pure gating/marker field: exists in field_mappings
                    // purely so checkUnmappedFields recognizes the source
                    // field as "handled" (e.g. a Block-3 validation flag
                    // like pacs.009's COV_FLAG that controls whether a
                    // whole optional sub-structure applies, enforced by a
                    // validation_rules entry, not by writing a value here).
                    // Writes nothing to the tree - target_path is
                    // typically a container element, and writing a leaf
                    // value into it would be structurally invalid the same
                    // way the earlier ChrgsInf bug was.
                    trace.add(traceRow(sourceField, targetPath, null, "no_op"));
                }
                case "skip_with_warning" -> {
                    // Like "unsupported" (a field the mapping doc's own
                    // notes document as genuinely unmappable in its
                    // current form), but for a target that's OPTIONAL in
                    // the schema - so dropping just this field and
                    // continuing the rest of the conversion is preferable
                    // to rejecting the whole message. Surfaced as a
                    // warning, not silently - the caller should always be
                    // able to see what was dropped and why.
                    conversionWarnings.add("Field " + sourceField + " ('" + rawValue + "') was present but not "
                            + "mapped to " + targetPath + " - skipped rather than reject the whole conversion or "
                            + "produce invalid output. See this entry's notes in the mapping doc for why.");
                    trace.add(traceRow(sourceField, targetPath, null, "skipped_with_warning"));
                }
                case "direct_copy" -> {
                    tree.put(targetPath, engine.directCopy(rawValue));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "code_list_lookup" -> {
                    tree.put(targetPath, engine.codeListLookup(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "truncate" -> {
                    tree.put(targetPath, engine.truncate(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "uppercase" -> {
                    tree.put(targetPath, engine.uppercase(rawValue));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "decimal_comma_to_dot" -> {
                    tree.put(targetPath, engine.decimalCommaToDot(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "date_format" -> {
                    tree.put(targetPath, engine.dateFormat(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "decompose_party" -> {
                    Map<String, String> sub = decompositionService.decompose(sourceField, rawValue, fm.getDecomposition());
                    Map<String, String> overrides = fm.getDecomposition().getSubElementTargets();
                    for (Map.Entry<String, String> e : sub.entrySet()) {
                        // A "#N" suffix (from decompose_party's lines_from:
                        // rule, for repeated elements like AdrLine) marks
                        // which repetition this is but isn't part of the
                        // sub-element's own name - strip it before matching
                        // sub_element_targets, then re-append to the
                        // resolved path so each repetition still gets its
                        // own unique tree key.
                        String subKey = e.getKey();
                        int hashIdx = subKey.indexOf('#');
                        String baseName = hashIdx < 0 ? subKey : subKey.substring(0, hashIdx);
                        String suffix = hashIdx < 0 ? "" : subKey.substring(hashIdx);
                        String fullPath = (overrides != null && overrides.containsKey(baseName))
                                ? overrides.get(baseName) + suffix
                                : targetPath + "." + baseName + suffix;
                        tree.put(fullPath, e.getValue());
                    }
                    trace.add(traceRow(sourceField, targetPath, sub, transformation));
                }
                case "llm_assisted" -> {
                    String value = llmClient.convertLlmAssisted(sourceField, targetPath, fm.getNotes(), fm.getEdgeCases(), rawValue);
                    // A blank/null result is a legitimate "no value" outcome
                    // for entries whose own decision procedure says so (e.g.
                    // the account-portion entries: "no leading '/' line ->
                    // output nothing, this is normal, not an error") - NOT
                    // "write an empty string into the tree", which would
                    // create an empty element/attribute that then fails XSD
                    // minLength validation. Skip writing entirely, same as
                    // decompose_party already does for an unmatched optional
                    // sub-element.
                    if (value != null && !value.isBlank()) {
                        tree.put(targetPath, value);
                        trace.add(traceRow(sourceField, targetPath, value, transformation));
                    }
                }
                default -> throw new TransformationException(
                        "Unknown transformation '" + transformation + "' for field " + sourceField);
            }
        }

        for (Map.Entry<String, String> def : doc.getTargetDefaults().entrySet()) {
            String key = def.getKey();
            // Not just an exact-key check: a target_default can also be a
            // "this container element must exist" placeholder (e.g. an
            // empty CdtTrfTxInf.Dbtr when no field populated it at all).
            // If some OTHER entry already populated a path UNDER this key
            // (e.g. CdtTrfTxInf.Dbtr.Nm), applying the default here would
            // set empty text on that same element in MxRenderer and wipe
            // out the child it already has - so skip whenever anything at
            // or under this path is already present, not just an exact match.
            boolean alreadyPopulated = tree.keySet().stream().anyMatch(k -> k.equals(key) || k.startsWith(key + "."));
            if (!alreadyPopulated) {
                tree.put(key, def.getValue());
                trace.add(traceRow(null, key, def.getValue(), "target_default"));
            }
        }

        String rendered = doc.getTargetFormat().toUpperCase().startsWith("MT")
                ? mtRenderer.render(tree)
                : mxRenderer.render(doc.getTargetFormat(), tree);

        ConvertedMessage result = new ConvertedMessage();
        result.setTargetFormat(doc.getTargetFormat());
        result.setTree(tree);
        result.setRenderedText(rendered);
        result.setFieldTrace(trace);
        result.setConversionWarnings(conversionWarnings);
        return result;
    }

    private void checkUnmappedFields(ParsedMessage parsed, MappingDocument doc) {
        Set<String> mappedSources = doc.getFieldMappings().stream()
                .map(FieldMapping::getSourceField)
                .collect(Collectors.toSet());
        String policy = doc.getUnmappedFieldsPolicy() == null ? "error" : doc.getUnmappedFieldsPolicy();

        List<String> unmapped = parsed.getFields().keySet().stream()
                .filter(f -> !mappedSources.contains(f))
                .toList();

        if (!unmapped.isEmpty() && "error".equals(policy)) {
            throw new UnmappableFieldException(String.join(", ", unmapped),
                    "These source field(s) were found in the input but have no entry in field_mappings for "
                            + "conversion '" + doc.getConversionId() + "'. Add explicit rules for them, or set "
                            + "unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.");
        }
    }

    private Map<String, Object> traceRow(String sourceField, String targetPath, Object result, String method) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source_field", sourceField);
        row.put("target_path", targetPath);
        row.put("result", result);
        row.put("method", method);
        return row;
    }
}