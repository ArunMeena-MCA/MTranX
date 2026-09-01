package com.wiredesk.mtmx.mapping;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic completeness auditor for mapping documents. Runs BEFORE
 * the parser/converter/validator touch a single message. Never calls
 * the LLM - completeness is a structural, checkable property, not a
 * judgement call. Direct behavioural port of the original engine's
 * completeness_auditor.py.
 *
 * IMPORTANT - about BASELINE_MANDATORY_FIELDS below: these lists are a
 * convenience sanity-check only (common MT mandatory fields per the
 * public SWIFT Category 1/2 message reference). They are NOT a
 * substitute for the authoritative SWIFT FIN / MyStandards field
 * specification for the exact message type + network you're
 * integrating with. A clean audit means "internally consistent", not
 * "SWIFT-certified complete".
 */
@Component
public class CompletenessAuditor {

    private static final List<String> REQUIRED_TOP_LEVEL_KEYS = List.of(
            "conversion_id", "source_format", "target_format", "version", "last_updated", "field_mappings"
    );

    private static final Map<String, List<String>> BASELINE_MANDATORY_FIELDS = Map.of(
            "MT103", List.of("20", "23B", "32A", "50", "59", "71A"),
            "MT202", List.of("20", "21", "32A", "58"),
            "MT202COV", List.of("20", "21", "32A", "50", "59", "58"),
            "MT205", List.of("20", "21", "32A", "58"),
            "MT940", List.of("20", "25", "28C", "60F", "62F"),
            "MT950", List.of("20", "25", "60F", "62F")
    );

    @SuppressWarnings("unchecked")
    public AuditResult audit(Map<String, Object> rawDoc) {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String key : REQUIRED_TOP_LEVEL_KEYS) {
            Object v = rawDoc.get(key);
            boolean empty = v == null
                    || (v instanceof String s && s.isEmpty())
                    || (v instanceof List<?> l && l.isEmpty());
            if (empty) {
                missing.add("Top-level key '" + key + "' is missing or empty.");
            }
        }

        Object fmObj = rawDoc.get("field_mappings");
        List<Map<String, Object>> fieldMappings = (fmObj instanceof List<?>)
                ? (List<Map<String, Object>>) fmObj
                : List.of();
        if (fieldMappings.isEmpty()) {
            missing.add("field_mappings is empty - no field can be converted.");
        }

        Set<String> seenSourceFields = new HashSet<>();
        for (int i = 0; i < fieldMappings.size(); i++) {
            Map<String, Object> fm = fieldMappings.get(i);
            String loc = "field_mappings[" + i + "]";

            Object sourceFieldObj = fm.get("source_field");
            String sourceField = sourceFieldObj == null ? null : String.valueOf(sourceFieldObj);
            if (sourceField == null || sourceField.isBlank()) {
                missing.add(loc + ".source_field is missing.");
            } else {
                seenSourceFields.add(sourceField);
            }

            if (fm.get("target_path") == null) {
                missing.add(loc + " (source_field=" + sourceField + "): target_path is missing.");
            }

            Object transformationObj = fm.get("transformation");
            String transformation = transformationObj == null ? null : String.valueOf(transformationObj);
            if (transformation == null) {
                missing.add(loc + " (source_field=" + sourceField + "): transformation is missing.");
            }

            if ("code_list_lookup".equals(transformation) && fm.get("code_list") == null) {
                missing.add(loc + " (source_field=" + sourceField + "): transformation is 'code_list_lookup' "
                        + "but no code_list table is provided - engine cannot guess valid code mappings.");
            }
            if ("constant".equals(transformation)) {
                Object cv = fm.get("constant_value");
                if (cv == null || String.valueOf(cv).isEmpty()) {
                    missing.add(loc + " (source_field=" + sourceField + "): transformation is 'constant' "
                            + "but constant_value is not set.");
                }
            }
            if ("generated".equals(transformation)) {
                Object gen = fm.get("generator");
                if (gen == null || String.valueOf(gen).isEmpty()) {
                    missing.add(loc + " (source_field=" + sourceField + "): transformation is 'generated' "
                            + "but generator is not set (expected 'uuid' or 'timestamp').");
                }
            }
            if ("conditional".equals(transformation)) {
                Object condObj = fm.get("conditional");
                if (!(condObj instanceof Map<?, ?> cond) || !(cond.get("check_fields") instanceof List<?> cf)
                        || cf.isEmpty()) {
                    missing.add(loc + " (source_field=" + sourceField + "): transformation is 'conditional' "
                            + "but conditional.check_fields is missing or empty.");
                } else {
                    boolean anyBranchConfigured = cond.get("if_any_present") != null
                            || cond.get("if_any_present_field") != null
                            || Boolean.TRUE.equals(cond.get("skip_if_any_present"));
                    boolean noneBranchConfigured = cond.get("if_none_present") != null
                            || cond.get("if_none_present_field") != null
                            || Boolean.TRUE.equals(cond.get("skip_if_none_present"));
                    // Leaving ONE branch unconfigured is a valid, intentional
                    // pattern - same status as decompose_party's
                    // fallback_if_unparseable=raise_error - meaning "reject
                    // with a clear error rather than invent a value" for that
                    // branch. Only flag it when NEITHER branch is configured,
                    // since then the entry can never produce anything at all,
                    // which is never intentional.
                    if (!anyBranchConfigured && !noneBranchConfigured) {
                        missing.add(loc + " (source_field=" + sourceField + "): transformation is 'conditional' "
                                + "but neither branch (if_any_present[_field] / if_none_present[_field] / "
                                + "skip_if_..._present) is configured for either outcome - this entry can never "
                                + "produce a value under any input.");
                    }
                }
            }
            if ("decompose_party".equals(transformation) && fm.get("decomposition") == null) {
                missing.add(loc + " (source_field=" + sourceField + "): transformation is 'decompose_party' "
                        + "but no decomposition rule is defined. Free-text party fields cannot be safely split "
                        + "into structured MX name/address elements without an explicit rule.");
            }

            boolean mandatory = Boolean.TRUE.equals(fm.get("mandatory"));
            if (mandatory && transformation == null) {
                missing.add(loc + " (source_field=" + sourceField + "): field is mandatory but has no "
                        + "transformation rule.");
            }
            boolean hasEdgeCases = fm.get("edge_cases") instanceof List<?> l1 && !l1.isEmpty();
            boolean hasConditional = fm.get("conditional_rules") instanceof List<?> l2 && !l2.isEmpty();
            if (mandatory && !hasEdgeCases && !hasConditional) {
                warnings.add(loc + " (source_field=" + sourceField + "): mandatory field has no documented "
                        + "edge_cases. Confirm this field truly has no absent/short/malformed-value scenarios "
                        + "to handle.");
            }
        }

        String unmappedPolicy = String.valueOf(rawDoc.getOrDefault("unmapped_fields_policy", "error"));
        if (!"error".equals(unmappedPolicy)) {
            warnings.add("unmapped_fields_policy is '" + unmappedPolicy + "', not the recommended 'error'. "
                    + "Any source field not explicitly listed in field_mappings will be "
                    + ("ignore".equals(unmappedPolicy) ? "silently dropped" : "passed through unmapped")
                    + " instead of raising - confirm this is intentional.");
        }

        String sourceFormat = String.valueOf(rawDoc.getOrDefault("source_format", ""))
                .toUpperCase().replace(".", "").replace("-", "");
        String baselineKey = null;
        for (String k : BASELINE_MANDATORY_FIELDS.keySet()) {
            if (sourceFormat.contains(k) || k.contains(sourceFormat)) {
                baselineKey = k;
                break;
            }
        }
        if (baselineKey != null) {
            List<String> missingBaseline = new ArrayList<>();
            for (String base : BASELINE_MANDATORY_FIELDS.get(baselineKey)) {
                boolean covered = seenSourceFields.stream().anyMatch(sf -> sf.equals(base) || sf.startsWith(base));
                if (!covered) {
                    missingBaseline.add(base);
                }
            }
            if (!missingBaseline.isEmpty()) {
                missing.add("Baseline sanity check: " + baselineKey + " normally carries mandatory fields "
                        + missingBaseline + " which have no entry in field_mappings at all. "
                        + "(Baseline list is illustrative only - verify against your authoritative SWIFT spec, "
                        + "then either add the mapping or explicitly document why it is not applicable.)");
            }
        }

        Object vr = rawDoc.get("validation_rules");
        boolean validationRulesEmpty = !(vr instanceof List<?> l) || l.isEmpty();
        if (validationRulesEmpty) {
            warnings.add("validation_rules is empty. The Validator will still run structural/charset/length "
                    + "checks, but no cross-field business rules will be enforced. Recommended before "
                    + "production use.");
        }

        return new AuditResult(missing.isEmpty(), missing, warnings);
    }
}
