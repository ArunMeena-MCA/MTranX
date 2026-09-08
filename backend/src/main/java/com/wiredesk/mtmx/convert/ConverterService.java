package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.address.AddressParserClient;
import com.wiredesk.mtmx.address.ParsedAddress;
import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.exception.UnmappableFieldException;
import com.wiredesk.mtmx.llm.GeminiClient;
import com.wiredesk.mtmx.mapping.model.ConditionalSubElementTarget;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import com.wiredesk.mtmx.mapping.model.MappingDocument;
import com.wiredesk.mtmx.mapping.model.StructuredAddressRule;
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
    private final AddressParserClient addressParserClient;

    public ConverterService(TransformationEngine engine,
                             DecompositionService decompositionService,
                             GeminiClient llmClient,
                             MtRenderer mtRenderer,
                             MxRenderer mxRenderer,
                             AddressParserClient addressParserClient) {
        this.engine = engine;
        this.decompositionService = decompositionService;
        this.llmClient = llmClient;
        this.mtRenderer = mtRenderer;
        this.mxRenderer = mxRenderer;
        this.addressParserClient = addressParserClient;
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
                if (fm.isRepeatLines()) {
                    applyRepeatedConditional(fm, parsed.getFields(), tree, trace);
                } else {
                    String value = engine.normalizeCase(engine.conditional(fm, parsed.getFields()), fm.getNormalizeCase());
                    if (value != null) {
                        tree.put(targetPath, value);
                        trace.add(traceRow(sourceField, targetPath, value, transformation));
                    }
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

            // BUG FIX (2026-09-04, TC41-44): deterministically skip this ENTIRE entry
            // when its own gate (e.g. a 13C/23E codeword) isn't even present in the raw
            // value - see FieldMapping.gatePattern's Javadoc. Checked before repeat_lines/
            // extract_pattern/the transformation switch so it applies uniformly to every
            // transformation type, not just llm_assisted (extract_pattern's own
            // extractSubstring() throws unconditionally on no match, with no "optional"
            // concept of its own - this gate has to run before that, not just before the
            // LLM call, or a deterministic entry sharing a source_field would hit the
            // exact same class of whole-conversion failure via a different code path).
            if (fm.getGatePattern() != null
                    && !java.util.regex.Pattern.compile(fm.getGatePattern(), java.util.regex.Pattern.MULTILINE)
                            .matcher(rawValue).find()) {
                continue;
            }

            if (fm.isRepeatLines()) {
                applyRepeatedSimpleTransformation(fm, sourceField, targetPath, transformation, rawValue, tree, trace);
                continue;
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
                    String value = engine.normalizeCase(engine.directCopy(rawValue), fm.getNormalizeCase());
                    tree.put(targetPath, value);
                    trace.add(traceRow(sourceField, targetPath, value, transformation));
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
                    String converted = engine.decimalCommaToDot(rawValue, fm);
                    // BUG FIX (2026-09-07, TC66): zero-pad to the currency's real minor-unit
                    // count when this entry opts in via currency_from_target_path - see that
                    // field's own Javadoc. Purely cosmetic (never changes the xs:decimal VALUE),
                    // opt-in so entries without a paired currency (e.g. field 36's exchange
                    // rate, which has no currency of its own) are unaffected.
                    if (fm.getCurrencyFromTargetPath() != null) {
                        converted = engine.padDecimalToCurrencyPrecision(converted, tree.get(fm.getCurrencyFromTargetPath()));
                    }
                    tree.put(targetPath, converted);
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "date_format" -> {
                    tree.put(targetPath, engine.dateFormat(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "decompose_party" -> {
                    Map<String, String> sub = decompositionService.decompose(sourceField, rawValue, fm.getDecomposition());
                    Map<String, String> overrides = fm.getDecomposition().getSubElementTargets();
                    Map<String, ConditionalSubElementTarget> conditionals =
                            fm.getDecomposition().getConditionalSubElementTargets();
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
                        String fullPath;
                        ConditionalSubElementTarget cond = conditionals == null ? null : conditionals.get(baseName);
                        if (cond != null && cond.getSkipPattern() != null
                                && java.util.regex.Pattern.matches(cond.getSkipPattern(), e.getValue())) {
                            // BUG FIX (2026-09-04, from live test case TC19): this value is
                            // already, correctly, someone else's responsibility (e.g. a
                            // clearing-system-code entry) - see skipPattern's own Javadoc.
                            // Written NOWHERE, not even elseTarget, so it doesn't ALSO show
                            // up duplicated under a semantically wrong element.
                            continue;
                        }
                        if (cond != null) {
                            // Route THIS SAME extracted value (not a second,
                            // independently re-parsed one) to one of two
                            // destinations based on its own shape - e.g. an
                            // IBAN-shaped account to .../IBAN, anything else
                            // to .../Othr/Id. Deliberately full-match
                            // (matches(), not find()) - a value that merely
                            // CONTAINS an IBAN-shaped substring is not itself
                            // an IBAN.
                            boolean isMatch = java.util.regex.Pattern.matches(cond.getPattern(), e.getValue());
                            fullPath = (isMatch ? cond.getIfMatchTarget() : cond.getElseTarget()) + suffix;
                        } else if (overrides != null && overrides.containsKey(baseName)) {
                            String override = overrides.get(baseName);
                            // BUG FIX (2026-09-08, from live test cases TC105/136): previously ALWAYS
                            // appended suffix at the very end of the override string - correct for a
                            // repeatable LEAF (e.g. "...PstlAdr.AdrLine" + "#0" = "...PstlAdr.AdrLine#0",
                            // still one AdrLine per index under the SAME parent), but wrong when the
                            // REPEATABLE element is a CONTAINER partway through the path (e.g. 23E's
                            // Cd entry: InstrForCdtrAgt itself repeats, maxOccurs=unbounded, with Cd as
                            // its own non-repeating child) - two Cd-eligible codes on the same message
                            // (e.g. "TELB\nPHOB", both matching the SAME regex) need
                            // "InstrForCdtrAgt#0.Cd" and "InstrForCdtrAgt#1.Cd", not
                            // "InstrForCdtrAgt.Cd#0"/"InstrForCdtrAgt.Cd#1" (which would try to put TWO
                            // Cd children under ONE InstrForCdtrAgt, invalid, and previously silently
                            // discarded everything past the first match instead). An override string
                            // that already contains a literal "#0" placeholder now has the suffix
                            // substituted IN PLACE of it (mirrors resolveRepeatedTargetPath's identical
                            // "#0" -> "#i" substitution for the repeat_lines mechanism), rather than
                            // appended - opt-in via the override author writing "#0" into the target
                            // path itself, so every existing leaf-suffix override (none of which
                            // contain "#0") is completely unaffected.
                            fullPath = override.contains("#0") ? override.replace("#0", suffix) : override + suffix;
                        } else {
                            fullPath = targetPath + "." + baseName + suffix;
                        }
                        // BUG FIX (2026-09-07, from live test cases TC37/50/100): a decompose_party
                        // sub-element previously wrote its value unconditionally, even when it was a
                        // blank string - unlike llm_assisted just below, which has ALWAYS skipped a
                        // blank/null result for exactly this reason (see its own comment: an empty
                        // element/attribute fails XSD minLength validation). This mattered nowhere
                        // before because every existing regex either matched real content or didn't
                        // match at all - but field 70's Ustrd entry (v2.22, concat: true) uses a
                        // catch-everything pattern ("^(?:/ROC/[^/\n]*)?(.*)$") that ALWAYS matches each
                        // line, even producing an empty capture when the entire line was consumed by
                        // the optional /ROC/ prefix (e.g. a field 70 that is ONLY "/ROC/value", nothing
                        // else) - concat then joined a single empty string into "", which sailed
                        // through into RmtInf/Ustrd as an empty element and failed XSD validation.
                        // Skip writing entirely when the value is blank, matching decompose_party's own
                        // existing "unmatched optional sub-element produces no entry at all" semantics.
                        if (e.getValue() == null || e.getValue().isBlank()) {
                            continue;
                        }
                        // Opt-in per-sub-element case fixup (e.g. BICFI -> upper, distinct from a
                        // sibling account sub-element that must NOT be forced to any case) - see
                        // DecompositionRule.subElementCaseNormalize's Javadoc. Written back into
                        // the entry itself (not just a local variable) so the trace row built from
                        // `sub` below reflects the actual written value, not the pre-normalization
                        // one - every other transformation type's trace row already shows the real
                        // written value, so decompose_party's own diagnostic trace should not be the
                        // one place a debugger sees a value that never actually reached the tree.
                        String subValue = engine.normalizeCase(e.getValue(),
                                fm.getDecomposition().getSubElementCaseNormalize().get(baseName));
                        e.setValue(subValue);
                        tree.put(fullPath, subValue);
                    }
                    enrichWithStructuredAddress(fm.getDecomposition().getStructuredAddress(), sub, tree, trace, sourceField);
                    trace.add(traceRow(sourceField, targetPath, sub, transformation));
                }
                case "time_offset_format" -> {
                    tree.put(targetPath, engine.timeOffsetFormat(rawValue, fm));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
                }
                case "settlement_datetime_from_time_offset" -> {
                    String datePart = fm.getDateFromTargetPath() == null ? null : tree.get(fm.getDateFromTargetPath());
                    tree.put(targetPath, engine.settlementDateTimeFromTimeOffset(rawValue, fm, datePart));
                    trace.add(traceRow(sourceField, targetPath, tree.get(targetPath), transformation));
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
                        // BUG FIX (2026-09-08, from live test case TC129): field 72 permits up
                        // to 210 chars of uncoded content with no codeword at all, but this
                        // entry's target (InstrForNxtAgt/InstrInf) is Max140Text - a
                        // legitimately long, entirely valid value previously failed XSD
                        // validation outright. When maxChunkLength is set, split the LLM's
                        // result into whitespace-boundary chunks and write one per repeatable
                        // InstrForNxtAgt occurrence instead of force-fitting one oversized
                        // value into a single element - see FieldMapping.maxChunkLength's
                        // Javadoc and TransformationEngine.chunkText.
                        if (fm.getMaxChunkLength() != null) {
                            List<String> chunks = engine.chunkText(value, fm.getMaxChunkLength());
                            for (int i = 0; i < chunks.size(); i++) {
                                String chunkPath = targetPath.replace("#0", "#" + i);
                                tree.put(chunkPath, chunks.get(i));
                                trace.add(traceRow(sourceField, chunkPath, chunks.get(i), transformation));
                            }
                        } else {
                            tree.put(targetPath, value);
                            trace.add(traceRow(sourceField, targetPath, value, transformation));
                        }
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

    /**
     * Repeat_lines variant for the simple value-in/value-out transformation
     * types. Treats each newline-separated occurrence of a repeated MT tag
     * (see ParsedMessage.addField's merge-with-newline-join - e.g. field
     * 71F appearing 2+ times in one message) as its own independent value,
     * applying this entry's own extract_pattern + transformation to EACH
     * line separately rather than to the whole joined multi-line string
     * (which is what previously threw TransformationException: a
     * single-line extract_pattern like "^[A-Z]{3}(.+)$" can never match
     * text containing an embedded newline). Deliberately narrow: only the
     * transformation types below fit this "value in, value out" shape -
     * decompose_party already has its own, different repeat mechanism
     * (lines_from), and conditional/constant/generated/llm_assisted/
     * unsupported/no_op/skip_with_warning don't fit here at all -
     * repeat_lines on one of those is a mapping-doc mistake, surfaced as
     * an error rather than silently ignored.
     */
    private void applyRepeatedSimpleTransformation(FieldMapping fm, String sourceField, String targetPath,
                                                    String transformation, String rawValue,
                                                    Map<String, String> tree, List<Map<String, Object>> trace) {
        Set<String> supported = Set.of("direct_copy", "code_list_lookup", "truncate", "uppercase",
                "decimal_comma_to_dot", "date_format");
        if (!supported.contains(transformation)) {
            throw new TransformationException(
                    "repeat_lines is not supported for transformation '" + transformation + "' (field " + sourceField
                            + ") - only " + supported + " support per-line repetition.");
        }
        String[] lines = rawValue.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (fm.getExtractPattern() != null) {
                line = engine.extractSubstring(line, fm);
            }
            String resolvedPath = resolveRepeatedTargetPath(targetPath, i);
            String value = switch (transformation) {
                case "direct_copy" -> engine.directCopy(line);
                case "code_list_lookup" -> engine.codeListLookup(line, fm);
                case "truncate" -> engine.truncate(line, fm);
                case "uppercase" -> engine.uppercase(line);
                case "decimal_comma_to_dot" -> {
                    String converted = engine.decimalCommaToDot(line, fm);
                    // BUG FIX (2026-09-07, TC66): same currency_from_target_path opt-in as the
                    // non-repeated case (see ConverterService.convert()'s own comment) - the
                    // referenced path also needs the SAME "#0" -> "#i" resolution this repeated
                    // amount's own target_path just got, since it's a per-occurrence currency
                    // (e.g. ChrgsInf#0.Amt.@Ccy, ChrgsInf#1.Amt.@Ccy for 71F/71G).
                    if (fm.getCurrencyFromTargetPath() != null) {
                        String resolvedCcyPath = resolveRepeatedTargetPath(fm.getCurrencyFromTargetPath(), i);
                        converted = engine.padDecimalToCurrencyPrecision(converted, tree.get(resolvedCcyPath));
                    }
                    yield converted;
                }
                case "date_format" -> engine.dateFormat(line, fm);
                default -> throw new IllegalStateException(transformation); // unreachable, guarded above
            };
            value = engine.normalizeCase(value, fm.getNormalizeCase());
            tree.put(resolvedPath, value);
            trace.add(traceRow(sourceField, resolvedPath, value, transformation));
        }
    }

    /**
     * Repeat_lines variant of the "conditional" transformation: fires once
     * per line of the FIRST check_fields entry's raw value (e.g. field 71F
     * repeated N times), instead of once for the whole message - so a
     * paired "agent" entry (e.g. 71F's charging-agent, which doesn't read
     * its OWN field's value at all, just checks 71F's presence) still
     * produces one Agt per ChrgsInf occurrence, not just one for the
     * whole message. Each firing lands on the same "#N" index the paired
     * currency/amount entries for the SAME source field independently
     * derive - see resolveRepeatedTargetPath's Javadoc for why that's safe
     * without shared mutable state between separate field_mappings
     * entries. Falls straight through to the ordinary (non-repeated)
     * conditional() behavior when the check field is absent, so
     * skip_if_none_present / if_none_present(_field) still work unchanged.
     */
    private void applyRepeatedConditional(FieldMapping fm, Map<String, String> allFields,
                                           Map<String, String> tree, List<Map<String, Object>> trace) {
        var rule = fm.getConditional();
        if (rule == null || rule.getCheckFields().isEmpty()) {
            throw new TransformationException(
                    "conditional transformation for target " + fm.getTargetPath()
                            + " has no conditional.check_fields defined.");
        }
        String repeatField = rule.getCheckFields().get(0);
        String repeatRaw = allFields.get(repeatField);
        if (repeatRaw == null) {
            String value = engine.normalizeCase(engine.conditional(fm, allFields), fm.getNormalizeCase());
            if (value != null) {
                tree.put(fm.getTargetPath(), value);
                trace.add(traceRow(fm.getSourceField(), fm.getTargetPath(), value, "conditional"));
            }
            return;
        }
        String[] lines = repeatRaw.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String resolvedPath = resolveRepeatedTargetPath(fm.getTargetPath(), i);
            String value = rule.getIfAnyPresentField() != null
                    ? allFields.get(rule.getIfAnyPresentField())
                    : rule.getIfAnyPresent();
            value = engine.normalizeCase(value, fm.getNormalizeCase());
            if (value != null) {
                tree.put(resolvedPath, value);
                trace.add(traceRow(fm.getSourceField(), resolvedPath, value, "conditional"));
            }
        }
    }

    /**
     * Resolves a target_path's literal "#0" occurrence marker to
     * "#&lt;lineIndex&gt;" for repeat_lines entries. Line 0 keeps "#0" -
     * byte-for-byte identical output to the pre-repeat_lines single
     * -occurrence case, so an existing message with only one occurrence of
     * a now-repeat_lines-flagged field is completely unaffected. Multiple
     * SEPARATE field_mappings entries that share a source field (e.g.
     * 71F's currency/amount/agent trio, three distinct entries) each
     * independently re-derive the same index from the same line position,
     * so they land on the same "#N" for the same occurrence without any
     * counter shared between them.
     */
    private String resolveRepeatedTargetPath(String targetPath, int lineIndex) {
        return targetPath.replace("#0", "#" + lineIndex);
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

    /**
     * Opt-in structured-address enrichment (see StructuredAddressRule's
     * Javadoc). No-op if the entry doesn't request it, if
     * mtmx.address-parser-enabled=false, or if the sidecar call didn't come
     * back confident - in every one of those cases the tree already has the
     * normal AdrLine writes from the loop above, so skipping here never
     * loses data, it just means this message doesn't ALSO get the
     * structured fields.
     */
    private void enrichWithStructuredAddress(StructuredAddressRule rule, Map<String, String> sub,
                                              Map<String, String> tree, List<Map<String, Object>> trace, String sourceField) {
        if (rule == null || rule.getSourceSubElement() == null) {
            return;
        }
        // Sub-element repetitions (from lines_from:) are keyed "Name#0",
        // "Name#1", ... - collect them in that numeric order, since word
        // order within the address matters for the parser.
        List<String> lines = sub.entrySet().stream()
                .filter(e -> {
                    String k = e.getKey();
                    int hashIdx = k.indexOf('#');
                    String baseName = hashIdx < 0 ? k : k.substring(0, hashIdx);
                    return baseName.equals(rule.getSourceSubElement());
                })
                .sorted((a, b) -> {
                    int ai = a.getKey().indexOf('#');
                    int bi = b.getKey().indexOf('#');
                    int an = ai < 0 ? 0 : Integer.parseInt(a.getKey().substring(ai + 1));
                    int bn = bi < 0 ? 0 : Integer.parseInt(b.getKey().substring(bi + 1));
                    return Integer.compare(an, bn);
                })
                .map(Map.Entry::getValue)
                .toList();
        if (lines.isEmpty()) {
            return;
        }

        ParsedAddress parsed = addressParserClient.parse(lines);
        if (parsed == null || !parsed.isConfident()) {
            return;
        }

        Map<String, String> targets = rule.getTargets();
        putIfPresent(tree, targets.get("street"), parsed.getStreet());
        putIfPresent(tree, targets.get("city"), parsed.getCity());
        putIfPresent(tree, targets.get("postcode"), parsed.getPostcode());
        putIfPresent(tree, targets.get("country"), parsed.getCountryCode());
        trace.add(traceRow(sourceField, rule.getSourceSubElement(), parsed, "structured_address"));
    }

    private void putIfPresent(Map<String, String> tree, String targetPath, String value) {
        if (targetPath != null && value != null && !value.isBlank()) {
            tree.put(targetPath, value);
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