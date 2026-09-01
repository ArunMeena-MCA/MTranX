package com.wiredesk.mtmx.validate;

import com.wiredesk.mtmx.config.AppProperties;
import com.wiredesk.mtmx.convert.ConvertedMessage;
import com.wiredesk.mtmx.convert.XsdIndexRegistry;
import com.wiredesk.mtmx.llm.GeminiClient;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import com.wiredesk.mtmx.mapping.model.MappingDocument;
import com.wiredesk.mtmx.mapping.model.ValidationRule;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs two independent layers of checks against the converter's output:
 *
 * 1) Deterministic checks - mandatory fields, length/pattern rules, SWIFT
 *    character-set conformance, well-formed XML plus real XSD validation
 *    if configured, and the mapping doc's own validation_rules where the
 *    logic string matches a supported deterministic shape.
 *
 * 2) An LLM semantic cross-check that classifies any problem as
 *    CONVERSION_ERROR (retry) or MAPPING_GAP (the doc itself needs fixing
 *    - retrying won't help).
 *
 * Deterministic errors always win; the LLM pass cannot override a hard
 * structural failure.
 */
@Service
public class ValidatorService {

    private static final Pattern SWIFTX_CHARSET = Pattern.compile("^[A-Za-z0-9/\\-?:().,'+ \\n\\r]*$");
    private static final Pattern SIMPLE_EQUALS_RULE = Pattern.compile("^([\\w.]+)\\s+equals\\s+([\\w.]+)$");

    /**
     * Standard ISO 4217 minor-unit exceptions, for the currency_precision_check
     * rule shape. Any currency not listed defaults to 2 decimal places (the
     * vast majority - USD, EUR, GBP, INR, AED, CAD, AUD, CHF, CNY, ...).
     * These are well-established, published ISO 4217 facts, not a guess.
     */
    private static final Map<String, Integer> CURRENCY_MINOR_UNITS = Map.ofEntries(
            // Zero decimal places
            Map.entry("JPY", 0), Map.entry("KRW", 0), Map.entry("VND", 0), Map.entry("CLP", 0),
            Map.entry("ISK", 0), Map.entry("XOF", 0), Map.entry("XAF", 0), Map.entry("XPF", 0),
            Map.entry("GNF", 0), Map.entry("RWF", 0), Map.entry("UGX", 0), Map.entry("PYG", 0),
            Map.entry("VUV", 0), Map.entry("DJF", 0), Map.entry("KMF", 0), Map.entry("BIF", 0),
            // Three decimal places
            Map.entry("BHD", 3), Map.entry("KWD", 3), Map.entry("OMR", 3), Map.entry("JOD", 3),
            Map.entry("TND", 3), Map.entry("LYD", 3), Map.entry("IQD", 3)
    );

    private final AppProperties props;
    private final GeminiClient llmClient;
    private final XsdIndexRegistry xsdIndexRegistry;

    public ValidatorService(AppProperties props, GeminiClient llmClient, XsdIndexRegistry xsdIndexRegistry) {
        this.props = props;
        this.llmClient = llmClient;
        this.xsdIndexRegistry = xsdIndexRegistry;
    }

    public ValidationReport validate(Map<String, String> parsedFields, ConvertedMessage converted, MappingDocument doc) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>(converted.getConversionWarnings());

        deterministicChecks(parsedFields, converted, doc, errors, warnings);

        if (!errors.isEmpty()) {
            // A structural/schema failure (SAX XSD validation, or the
            // proactive completeness check) is DETERMINISTIC - the same
            // mapping doc against the same input produces the exact same
            // missing/invalid structure every time, so retrying the
            // converter (the CONVERSION_ERROR path) just burns
            // max-converter-retries attempts for nothing. Classifying
            // these as MAPPING_GAP fails fast with a clear message
            // instead. This was a pre-existing gap - "XSD validation
            // error"/"Schema completeness check" never matched the
            // original substring list at all.
            boolean mappingGap = errors.stream().anyMatch(e ->
                    e.contains("No XSD found") || e.contains("code_list") || e.contains("decomposition")
                            || e.contains("XSD validation error") || e.contains("Schema completeness check")
                            || e.contains("not well-formed XML"));
            ValidationReport report = new ValidationReport();
            report.setValid(false);
            report.setErrors(errors);
            report.setWarnings(warnings);
            report.setClassification(mappingGap ? "MAPPING_GAP" : "CONVERSION_ERROR");
            return report;
        }

        String classification = "OK";
        String llmAuditStatus;
        Set<String> knownFields = new HashSet<>();
        for (FieldMapping fm : doc.getFieldMappings()) {
            if (fm.getSourceField() != null) {
                knownFields.add(fm.getSourceField());
            }
            if (fm.getTargetPath() != null) {
                knownFields.add(fm.getTargetPath());
            }
        }

        try {
            Map<String, Object> audit = llmClient.semanticAudit(parsedFields, doc.getFieldMappings(), converted.getTree());
            llmAuditStatus = "ran";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>) audit.getOrDefault("findings", List.of());

            // Sanity cross-check: is_valid=false with zero findings is an
            // internally inconsistent response (the schema forces the
            // model to explain itself via findings if it thinks something
            // is wrong) - surface that as its own warning rather than
            // silently treating an inconsistent audit as "clean".
            if (Boolean.FALSE.equals(audit.get("is_valid")) && findings.isEmpty()) {
                warnings.add("LLM semantic audit reported is_valid=false but returned no specific findings - "
                        + "treating as inconclusive rather than silently passing.");
            }

            for (Map<String, Object> f : findings) {
                String fieldRef = String.valueOf(f.get("field"));
                // Lightweight hallucination guard: if the audit references
                // a field that doesn't correspond to any known
                // source_field/target_path in this mapping doc, flag the
                // finding as unverified rather than either discarding it
                // (could hide a real issue phrased slightly differently
                // than the exact path string) or trusting it at full
                // face value.
                boolean knownField = knownFields.contains(fieldRef)
                        || knownFields.stream().anyMatch(k -> fieldRef.contains(k) || k.contains(fieldRef));
                String tag = knownField ? "" : " [unverified field reference - not found in this mapping doc]";
                String msg = "[" + fieldRef + "] " + f.get("issue") + " (" + f.get("classification") + ")" + tag;
                if ("error".equals(f.get("severity"))) {
                    errors.add(msg);
                    classification = String.valueOf(f.get("classification"));
                } else {
                    warnings.add(msg);
                }
            }
        } catch (Exception e) {
            // The LLM semantic audit is a cross-check ON TOP of the
            // deterministic layer above (which already passed, or this
            // code wouldn't have been reached) - not the sole gate. A
            // transient API failure here should not fail an otherwise
            // structurally-valid, XSD-verified conversion; it should be
            // visible (not silently swallowed either), so callers can
            // tell "clean because both layers passed" apart from "clean
            // because the LLM layer never actually ran" via
            // llmAuditStatus.
            llmAuditStatus = "error: " + e.getMessage();
            warnings.add("LLM semantic audit could not be completed (" + e.getMessage() + ") - deterministic "
                    + "checks (mandatory fields, XSD structure, validation_rules) passed, but this conversion has "
                    + "NOT had the independent LLM cross-check applied. See llmAuditStatus.");
        }

        ValidationReport report = new ValidationReport();
        report.setValid(errors.isEmpty());
        report.setErrors(errors);
        report.setWarnings(warnings);
        report.setClassification(errors.isEmpty() ? "OK" : classification);
        report.setLlmAuditStatus(llmAuditStatus);
        return report;
    }

    private void deterministicChecks(Map<String, String> parsedFields, ConvertedMessage converted, MappingDocument doc,
                                      List<String> errors, List<String> warnings) {
        Map<String, String> tree = converted.getTree();

        for (FieldMapping fm : doc.getFieldMappings()) {
            String targetPath = fm.getTargetPath();
            String value = tree.entrySet().stream()
                    .filter(e -> e.getKey().equals(targetPath) || e.getKey().startsWith(targetPath + "."))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);

            if (fm.isMandatory() && value == null) {
                errors.add("Mandatory target field '" + targetPath + "' (from source '" + fm.getSourceField()
                        + "') is missing from the converted output.");
                continue;
            }
            if (value == null) {
                continue;
            }
            if (fm.getMaxLength() != null && value.length() > fm.getMaxLength()) {
                errors.add("Target field '" + targetPath + "' value length " + value.length()
                        + " exceeds max_length=" + fm.getMaxLength() + " declared in mapping doc.");
            }
            if (fm.getAllowedPattern() != null && !value.matches(fm.getAllowedPattern())) {
                errors.add("Target field '" + targetPath + "' value '" + value + "' does not match "
                        + "allowed_pattern " + fm.getAllowedPattern() + ".");
            }
        }

        String charset = doc.getCharacterSet() == null ? "SWIFT-X" : doc.getCharacterSet();
        boolean isMt = doc.getTargetFormat().toUpperCase().startsWith("MT");
        if ("SWIFT-X".equals(charset)) {
            List<String> valuesToCheck = isMt ? List.of(converted.getRenderedText()) : new ArrayList<>(tree.values());
            for (String value : valuesToCheck) {
                if (!SWIFTX_CHARSET.matcher(value).matches()) {
                    TreeSet<Character> offending = value.chars()
                            .mapToObj(c -> (char) c)
                            .filter(c -> !SWIFTX_CHARSET.matcher(String.valueOf(c)).matches())
                            .collect(Collectors.toCollection(TreeSet::new));
                    errors.add("Value '" + value + "' contains characters outside the SWIFT-X character set: " + offending);
                }
            }
        }

        if (isMt) {
            for (String line : converted.getRenderedText().split("\n")) {
                String content = line.startsWith(":") && line.indexOf(':', 1) >= 0
                        ? line.substring(line.indexOf(':', 1) + 1)
                        : line;
                if (content.length() > 35) {
                    warnings.add("MT line exceeds the conventional 35-character limit: "
                            + line.substring(0, Math.min(50, line.length())) + "...");
                }
            }
        } else {
            try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new ByteArrayInputStream(converted.getRenderedText().getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                errors.add("Rendered MX output is not well-formed XML: " + e.getMessage());
                return; // can't XSD-validate broken XML
            }

            // Proactive, schema-driven completeness check: BEFORE relying
            // solely on the reactive SAX-based XSD pass below, walk the
            // schema itself (via the same XsdOrderingIndex MxRenderer uses
            // for element ordering) for mandatory elements/attributes that
            // simply aren't in the tree at all. This catches exactly the
            // class of bug this engine kept hitting one field at a time
            // over many rounds (missing GrpHdr envelope, missing Dbtr,
            // missing Ccy attribute, ...) with a clear field-path message
            // instead of SAX's sometimes-cryptic "expected one of [...]"
            // wording - and it still runs even without an XSD configured
            // being the reason SAX validation itself was skipped, since it
            // only needs XsdIndexRegistry's parsed structure, not a live
            // validator. Does not replace the SAX pass below, which
            // remains the authoritative final gate (this check
            // deliberately does not attempt full choice-cardinality or
            // datatype/pattern validation - see XsdOrderingIndex's own
            // documented scope).
            xsdIndexRegistry.get(doc.getTargetFormat()).ifPresent(xsdIndex -> {
                for (String missing : xsdIndex.findMissingMandatory(tree)) {
                    errors.add("Schema completeness check: missing mandatory " + missing + ".");
                }
            });

            if (props.getXsdDir() != null && !props.getXsdDir().isBlank()) {
                File xsdFile = new File(props.getXsdDir(), doc.getTargetFormat() + ".xsd");
                if (xsdFile.exists()) {
                    try {
                        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                        Schema schema = sf.newSchema(xsdFile);
                        Validator validator = schema.newValidator();
                        validator.validate(new StreamSource(new StringReader(converted.getRenderedText())));
                    } catch (SAXException | java.io.IOException e) {
                        errors.add("XSD validation error: " + e.getMessage());
                    }
                } else {
                    warnings.add("No XSD found at " + xsdFile.getPath() + " - skipping real schema validation. "
                            + "Structural correctness against the official ISO 20022 schema is NOT guaranteed "
                            + "without this. Supply the official XSD for production confidence.");
                }
            } else {
                warnings.add("No xsd-dir configured (mtmx.xsd-dir) - skipping real ISO 20022 XSD validation. "
                        + "This is the single biggest confidence gap in the pipeline; configure it before "
                        + "treating output as SWIFT-ready.");
            }
        }

        for (ValidationRule rule : doc.getValidationRules()) {
            String failureDetail = evaluateRule(rule, parsedFields, tree);
            if (failureDetail != null) {
                String msg = "[" + rule.getRuleId() + "] " + rule.getDescription() + " - " + failureDetail;
                if ("error".equals(rule.getSeverity())) {
                    errors.add(msg);
                } else {
                    warnings.add(msg);
                }
            }
        }
    }

    /**
     * Dispatches to a known machine-checkable rule shape by rule_type, or
     * falls back to the legacy free-text "A equals B" pattern for rules
     * without one. Returns null when the rule passed, doesn't apply to
     * this message (its trigger condition wasn't met), or couldn't be
     * evaluated (missing params/data) - a rule that can't fire is not a
     * failure. Returns a specific, human-readable failure description
     * otherwise. An unrecognized rule_type also returns null - it stays
     * descriptive-only rather than blocking on something this engine
     * doesn't know how to check, same "extend deliberately, don't guess"
     * stance the original design used.
     */
    private String evaluateRule(ValidationRule rule, Map<String, String> parsedFields, Map<String, String> tree) {
        String type = rule.getRuleType();
        if (type == null || type.isBlank()) {
            Boolean legacy = evaluateSimpleRule(rule, parsedFields, tree);
            return Boolean.FALSE.equals(legacy) ? "condition not satisfied (legacy 'A equals B' rule)" : null;
        }
        Map<String, Object> p = rule.getParams();
        return switch (type) {
            case "conditional_currency_mismatch_requires" -> evalCurrencyMismatchRequires(p, tree);
            case "presence_requires_value" -> evalPresenceRequiresValue(p, tree);
            case "mutual_exclusion" -> evalMutualExclusion(p, tree);
            case "source_alternative_group_required" -> evalSourceAlternativeGroup(p, parsedFields);
            case "currency_precision_check" -> evalCurrencyPrecision(p, tree);
            case "structured_address_required" -> evalStructuredAddress(p, tree);
            case "presence_requires_presence" -> evalPresenceRequiresPresence(p, parsedFields, tree);
            default -> null;
        };
    }

    /**
     * VR-202-05 shape (pacs.009): "target_path must be present in the
     * converted output IF AND ONLY IF source_trigger_field was present in
     * the SOURCE message" - a bidirectional presence check spanning both
     * domains (source field, target tree), unlike the schema-completeness
     * check (which only knows about the target side and can't express "a
     * whole optional sub-structure must be entirely ABSENT because of a
     * source-side flag" - an optional schema branch being unpopulated is
     * always schema-valid on its own). Generic: reusable for any similar
     * "does this source flag correctly gate this whole optional
     * sub-structure" rule in any mapping doc, not hardcoded to
     * COV_FLAG/UndrlygCstmrCdtTrf.
     */
    private String evalPresenceRequiresPresence(Map<String, Object> p, Map<String, String> parsedFields, Map<String, String> tree) {
        String triggerField = (String) p.get("source_trigger_field");
        String targetPath = (String) p.get("target_path");
        if (triggerField == null || targetPath == null) {
            return null;
        }
        boolean triggerPresent = parsedFields.containsKey(triggerField);
        boolean targetPresent = presentUnder(tree, targetPath);
        if (triggerPresent && !targetPresent) {
            return "source field '" + triggerField + "' is present but " + targetPath + " was not populated";
        }
        if (!triggerPresent && targetPresent) {
            return targetPath + " is populated but source field '" + triggerField + "' is absent - should not have been produced";
        }
        return null;
    }

    /**
     * Only a small, explicit, auditable set of deterministic rule shapes is
     * supported out of the box. Anything more complex should be expressed
     * via field-level edge_cases instead, or this method should be
     * extended deliberately - not worked around with a guess. Returns null
     * if the logic string doesn't match a recognised shape (surfaced only
     * via the LLM semantic pass, never silently skipped here).
     */
    private Boolean evaluateSimpleRule(ValidationRule rule, Map<String, String> parsedFields, Map<String, String> tree) {
        String logic = rule.getLogic() == null ? "" : rule.getLogic().trim();
        Matcher m = SIMPLE_EQUALS_RULE.matcher(logic);
        if (m.matches()) {
            String a = tree.getOrDefault(m.group(1), parsedFields.get(m.group(1)));
            String b = tree.getOrDefault(m.group(2), parsedFields.get(m.group(2)));
            if (a == null || b == null) {
                return null;
            }
            return a.equals(b);
        }
        return null;
    }

    /** True if the tree has a value AT this exact path, nested under it, or as a repeated "path#N" leaf. */
    private boolean presentUnder(Map<String, String> tree, String path) {
        return tree.keySet().stream().anyMatch(k -> k.equals(path) || k.startsWith(path + ".") || k.startsWith(path + "#"));
    }

    /**
     * VR001 shape: "if trigger_field is present and its currency differs
     * from compare_field's currency, required_field must be present."
     * Generic - reusable for any similar cross-amount-field currency rule
     * in any mapping doc, not hardcoded to IntrBkSttlmAmt/InstdAmt/XchgRate.
     */
    @SuppressWarnings("unchecked")
    private String evalCurrencyMismatchRequires(Map<String, Object> p, Map<String, String> tree) {
        String triggerField = (String) p.get("trigger_field");
        String compareField = (String) p.get("compare_field");
        String requiredField = (String) p.get("required_field");
        if (triggerField == null || compareField == null || requiredField == null) {
            return null;
        }
        if (!presentUnder(tree, triggerField)) {
            return null; // rule's trigger condition not met on this message
        }
        String triggerCcy = tree.get(triggerField + ".@Ccy");
        String compareCcy = tree.get(compareField + ".@Ccy");
        if (triggerCcy == null || compareCcy == null || triggerCcy.equals(compareCcy)) {
            return null; // can't compare, or currencies match - rule not triggered
        }
        if (!presentUnder(tree, requiredField)) {
            return requiredField + " is required because " + triggerField + " (Ccy=" + triggerCcy + ") differs "
                    + "from " + compareField + " (Ccy=" + compareCcy + "), but it is absent";
        }
        return null;
    }

    /** VR003 shape: "if any of trigger_fields is present, gate_field must equal gate_value." */
    @SuppressWarnings("unchecked")
    private String evalPresenceRequiresValue(Map<String, Object> p, Map<String, String> tree) {
        List<String> triggerFields = (List<String>) p.get("trigger_fields");
        String gateField = (String) p.get("gate_field");
        String gateValue = (String) p.get("gate_value");
        if (triggerFields == null || gateField == null || gateValue == null) {
            return null;
        }
        boolean anyTriggered = triggerFields.stream().anyMatch(f -> presentUnder(tree, f));
        if (!anyTriggered) {
            return null;
        }
        String actual = tree.get(gateField);
        if (!gateValue.equals(actual)) {
            return gateField + " should be '" + gateValue + "' because one of " + triggerFields + " is present, "
                    + "but is '" + actual + "'";
        }
        return null;
    }

    /** VR004 shape: "if ALL of requires_all are present, excludes must NOT be present." */
    @SuppressWarnings("unchecked")
    private String evalMutualExclusion(Map<String, Object> p, Map<String, String> tree) {
        List<String> requiresAll = (List<String>) p.get("requires_all");
        String excludes = (String) p.get("excludes");
        if (requiresAll == null || excludes == null) {
            return null;
        }
        boolean allPresent = requiresAll.stream().allMatch(f -> presentUnder(tree, f));
        if (!allPresent) {
            return null;
        }
        if (presentUnder(tree, excludes)) {
            return excludes + " should not be present because all of " + requiresAll + " are already populated";
        }
        return null;
    }

    /**
     * VR005 shape: per-message (not per-doc, unlike CompletenessAuditor's
     * static baseline check) source-side presence rule - "at least one
     * field from each alternative_groups list must be present in THIS
     * message, plus every field in required_source_fields."  Checked
     * against parsedFields (source), not tree (target) - this is about
     * what the incoming MT103 itself must supply, not what got produced.
     */
    @SuppressWarnings("unchecked")
    private String evalSourceAlternativeGroup(Map<String, Object> p, Map<String, String> parsedFields) {
        List<List<String>> groups = (List<List<String>>) (List<?>) p.get("alternative_groups");
        List<String> requiredSourceFields = (List<String>) p.get("required_source_fields");
        List<String> problems = new ArrayList<>();
        if (groups != null) {
            for (List<String> group : groups) {
                if (group.stream().noneMatch(parsedFields::containsKey)) {
                    problems.add("none of " + group + " present in the source message");
                }
            }
        }
        if (requiredSourceFields != null) {
            for (String f : requiredSourceFields) {
                if (!parsedFields.containsKey(f)) {
                    problems.add("required source field '" + f + "' is absent");
                }
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /**
     * VR007 shape: standard ISO 4217 minor-unit precision - an amount's
     * fractional digit count must not exceed its currency's defined
     * minor unit (e.g. JPY allows 0 decimal places; most currencies allow
     * 2; a handful of Middle Eastern currencies allow 3). Only flags TOO
     * MANY fractional digits - a round-number amount with FEWER digits
     * than the currency's standard (e.g. "100" for USD) is legitimate,
     * not a violation.
     */
    @SuppressWarnings("unchecked")
    private String evalCurrencyPrecision(Map<String, Object> p, Map<String, String> tree) {
        List<String> amountFields = (List<String>) p.get("amount_fields");
        if (amountFields == null) {
            return null;
        }
        List<String> problems = new ArrayList<>();
        for (String field : amountFields) {
            String value = tree.get(field);
            String ccy = tree.get(field + ".@Ccy");
            if (value == null || ccy == null) {
                continue;
            }
            int allowed = CURRENCY_MINOR_UNITS.getOrDefault(ccy, 2);
            int dot = value.indexOf('.');
            int actualDecimals = dot < 0 ? 0 : value.length() - dot - 1;
            if (actualDecimals > allowed) {
                problems.add(field + "=" + value + " " + ccy + " has " + actualDecimals + " fractional digit(s), "
                        + "but " + ccy + " allows at most " + allowed);
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /**
     * VR008 shape: a PstlAdr populated only with free-text AdrLine (no
     * structured TwnNm/Ctry) is flagged - forward-looking reminder for
     * SWIFT's move toward structured/hybrid address requirements. Only
     * checks presence of the structured fields, not their correctness -
     * this engine doesn't attempt to derive TwnNm/Ctry from free text
     * (see the 50K/59 Nm+AdrLine entries' own notes on why not).
     *
     * <p>DATE-GATED: this rule's own description states it applies "if
     * converted after 14 November 2026" - a genuine bug shipped in v2.2's
     * first implementation ignored that entirely and enforced it
     * unconditionally, firing months before its own stated effective
     * date. params.effective_date (ISO yyyy-MM-dd) controls this: before
     * that date, the rule doesn't apply at all (not even as a warning -
     * it isn't in effect yet, so there is nothing to warn about); no
     * effective_date configured means always-enforced (today, for a rule
     * with no forward-looking condition at all).
     */
    @SuppressWarnings("unchecked")
    private String evalStructuredAddress(Map<String, Object> p, Map<String, String> tree) {
        List<String> addressFields = (List<String>) p.get("address_fields");
        if (addressFields == null) {
            return null;
        }
        Object effectiveDateStr = p.get("effective_date");
        if (effectiveDateStr instanceof String s && !s.isBlank()) {
            try {
                java.time.LocalDate effectiveDate = java.time.LocalDate.parse(s);
                if (java.time.LocalDate.now().isBefore(effectiveDate)) {
                    return null; // not in effect yet
                }
            } catch (java.time.format.DateTimeParseException e) {
                // Malformed effective_date in the mapping doc - fail safe
                // by treating the rule as not-yet-active rather than
                // either crashing validation or silently enforcing a rule
                // whose activation date couldn't even be parsed.
                return null;
            }
        }
        List<String> problems = new ArrayList<>();
        for (String field : addressFields) {
            boolean hasAdrLine = tree.keySet().stream().anyMatch(k -> k.startsWith(field + ".AdrLine"));
            boolean hasTwnNm = tree.containsKey(field + ".TwnNm");
            boolean hasCtry = tree.containsKey(field + ".Ctry");
            if (hasAdrLine && (!hasTwnNm || !hasCtry)) {
                problems.add(field + " has free-text AdrLine but no structured TwnNm/Ctry");
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }
}
