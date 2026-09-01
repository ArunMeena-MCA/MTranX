package com.wiredesk.mtmx.parser;

import com.wiredesk.mtmx.exception.SemanticDecompositionGapException;
import com.wiredesk.mtmx.llm.GeminiClient;
import com.wiredesk.mtmx.mapping.model.DecompositionRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a free-text field into structured sub-elements using the mapping
 * doc's decomposition rule. Tries deterministic regex/line-index rules
 * first (fast, exact, zero hallucination risk); falls back to a tightly
 * scoped LLM call ONLY if the rule explicitly allows it. Raises
 * SemanticDecompositionGapException if no rule is provided at all, or if
 * neither the deterministic pass nor the guided LLM pass can produce a
 * confident result - the caller must NOT proceed with a guessed split.
 */
@Service
public class DecompositionService {

    private final GeminiClient llmClient;

    public DecompositionService(GeminiClient llmClient) {
        this.llmClient = llmClient;
    }

    public Map<String, String> decompose(String sourceField, String rawValue, DecompositionRule rule) {
        if (rule == null) {
            throw new SemanticDecompositionGapException(sourceField, rawValue,
                    "No decomposition rule defined in mapping doc for this field. Add a `decomposition` block "
                            + "with `sub_elements` and `fallback_if_unparseable`.");
        }

        Map<String, Object> subElementsRules = rule.getSubElements() == null ? Map.of() : rule.getSubElements();
        List<String> lines = new ArrayList<>(List.of(rawValue.split("\\r?\\n")).stream()
                .filter(l -> !l.isBlank())
                .toList());

        Map<String, String> result = new LinkedHashMap<>();

        // Strip an optional leading "/account" (or similar) line BEFORE
        // resolving line:N indices below, so "line:0" always means "the
        // first name/address line" regardless of whether an account line
        // preceded it - see DecompositionRule's own Javadoc for why this
        // has to be a separate pass rather than a per-sub-element flag.
        if (rule.isStripAccountLinePrefix() && !lines.isEmpty()) {
            Matcher accountMatch = Pattern.compile(rule.getAccountLinePattern()).matcher(lines.get(0));
            if (accountMatch.matches()) {
                result.put(rule.getAccountSubElement(),
                        accountMatch.groupCount() > 0 ? accountMatch.group(1) : accountMatch.group());
                lines.remove(0);
            }
        }

        boolean allMatched = true;

        for (Map.Entry<String, Object> entry : subElementsRules.entrySet()) {
            String name = entry.getKey();
            String ruleSpec = ruleSpec(entry.getValue());
            boolean optional = isOptional(entry.getValue());

            if (ruleSpec.startsWith("line:")) {
                int idx = Integer.parseInt(ruleSpec.substring("line:".length()));
                if (idx < lines.size()) {
                    result.put(name, lines.get(idx));
                } else if (!optional) {
                    allMatched = false;
                }
            } else if (ruleSpec.startsWith("lines_from:")) {
                // Every remaining line from idx onward, each as its OWN
                // result entry ("name#0", "name#1", ...) rather than joined
                // into one string - for schema elements that allow repeated
                // occurrences (e.g. PostalAddress24/AdrLine, maxOccurs=7).
                // ConverterService strips the "#N" suffix when resolving
                // sub_element_targets and MxRenderer strips it again when
                // choosing the actual XML tag name, so each becomes a
                // separate sibling element with the same tag.
                int idx = Integer.parseInt(ruleSpec.substring("lines_from:".length()));
                if (idx < lines.size()) {
                    for (int i = idx; i < lines.size(); i++) {
                        result.put(name + "#" + (i - idx), lines.get(i));
                    }
                } else if (!optional) {
                    allMatched = false;
                }
            } else if (ruleSpec.startsWith("regex:")) {
                Pattern pattern = Pattern.compile(ruleSpec.substring("regex:".length()), Pattern.MULTILINE);
                Matcher m = pattern.matcher(rawValue);
                if (m.find()) {
                    result.put(name, m.groupCount() > 0 ? m.group(1) : m.group());
                } else if (!optional) {
                    allMatched = false;
                }
            } else {
                allMatched = false; // unrecognised rule syntax - needs LLM or is a doc-authoring error
            }
        }

        if (allMatched && !subElementsRules.isEmpty()) {
            return result;
        }

        // Guided LLM fallback - only reached because a rule exists.
        Map<String, String> llmResult = llmClient.decompose(sourceField, rawValue, rule);
        if (llmResult != null && !llmResult.isEmpty()) {
            return llmResult;
        }

        throw new SemanticDecompositionGapException(sourceField, rawValue, "Could not confidently decompose the field.");
    }

    private String ruleSpec(Object definition) {
        if (definition instanceof String text) {
            return text;
        }
        if (definition instanceof Map<?, ?> details && details.get("rule") instanceof String text) {
            return text;
        }
        return "";
    }

    private boolean isOptional(Object definition) {
        return definition instanceof Map<?, ?> details && Boolean.TRUE.equals(details.get("optional"));
    }
}
