package com.wiredesk.mtmx.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiredesk.mtmx.config.AppProperties;
import com.wiredesk.mtmx.exception.LlmResponseException;
import com.wiredesk.mtmx.exception.SemanticDecompositionGapException;
import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.DecompositionRule;
import com.wiredesk.mtmx.mapping.model.EdgeCase;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the configured Gemini or Groq API, forcing every call
 * to return a structured JSON object, so
 * the rest of the pipeline never has to parse free-form model prose.
 *
 * This is used ONLY for the three narrow, explicitly-opted-into cases the
 * mapping doc can request:
 *   - decompose(): fallback for decompose_party when the deterministic
 *     line/regex rules in the doc don't fully resolve a free-text field
 *   - convertLlmAssisted(): fields whose transformation is "llm_assisted"
 *   - semanticAudit(): the Validator's independent cross-check pass
 *
 * Everything else in the pipeline is deterministic Java code.
 */
@Component
public class GeminiClient {

    private final AppProperties props;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

        public GeminiClient(AppProperties props) {
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> callStructured(String systemPrompt, String userContent, String toolName,
                                               String toolDescription, Map<String, Object> inputSchema) {
        try {
                        if ("groq".equalsIgnoreCase(props.getProvider())) {
                                return callGroq(systemPrompt, userContent, toolName, inputSchema);
                        }
                        return callGemini(systemPrompt, userContent, toolName, inputSchema);
                } catch (LlmResponseException e) {
                        throw e;
                } catch (Exception e) {
                        throw new LlmResponseException(props.getProvider() + " API call failed: " + e.getMessage());
                }
        }

        private Map<String, Object> callGroq(String systemPrompt, String userContent, String toolName,
                                                                                  Map<String, Object> inputSchema) throws Exception {
            // Symmetric with callGemini's retry-on-malformed-JSON below -
            // previously only the Gemini path retried a parse failure,
            // leaving Groq strictly less robust for no reason other than
            // history.
            JsonProcessingException lastParseError = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                String retryInstruction = attempt == 0 ? "" : "\n\nIMPORTANT RETRY: Return one complete, valid JSON "
                        + "object only. Escape every double quote inside string values and include the closing brace.";
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", props.getModelName());
                body.put("messages", List.of(
                        Map.of("role", "system", "content", systemPrompt + retryInstruction),
                        Map.of("role", "user", "content", userContent + "\n\nReturn JSON matching this schema: "
                                + json.writeValueAsString(inputSchema))));
                body.put("max_tokens", props.getMaxTokens());
                body.put("temperature", 0.0);
                body.put("response_format", Map.of("type", "json_object"));

                String payload = json.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                        .header("authorization", "Bearer " + props.getGroqApiKey())
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    throw new LlmResponseException("Groq API returned HTTP " + response.statusCode() + ": " + response.body());
                }

                Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<Map<String, Object>>() {
                });
                Object choicesObj = parsed.get("choices");
                if (choicesObj instanceof List<?> choices && !choices.isEmpty()
                        && choices.get(0) instanceof Map<?, ?> choice
                        && choice.get("message") instanceof Map<?, ?> message
                        && message.get("content") instanceof String text) {
                    try {
                        return json.readValue(text, new TypeReference<Map<String, Object>>() {
                        });
                    } catch (JsonProcessingException e) {
                        lastParseError = e;
                    }
                }
            }
            if (lastParseError != null) {
                throw lastParseError;
            }
            throw new LlmResponseException("Groq returned no JSON response for '" + toolName + "'.");
        }

        private Map<String, Object> callGemini(String systemPrompt, String userContent, String toolName,
                                                                                        Map<String, Object> inputSchema) throws Exception {
                JsonProcessingException lastParseError = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                        String retryInstruction = attempt == 0 ? "" : "\n\nIMPORTANT RETRY: Return one complete, valid JSON object only. "
                                        + "Escape every double quote inside string values, including XML attributes, and include the closing brace.";
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("model", props.getModelName());
                        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt + retryInstruction))));
                        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userContent)))));
                        body.put("generationConfig", Map.of(
                                        "maxOutputTokens", props.getMaxTokens(),
                                        "temperature", 0.0,
                                        "responseMimeType", "application/json",
                                        "responseSchema", inputSchema));

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                                                        + props.getModelName() + ":generateContent?key=" + props.getGeminiApiKey()))
                                        .header("content-type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                                        .build();

                        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 300) {
                                throw new LlmResponseException("Gemini API returned HTTP " + response.statusCode() + ": " + response.body());
                        }
                        Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<Map<String, Object>>() {
                        });
                        Object candidatesObj = parsed.get("candidates");
                        if (candidatesObj instanceof List<?> candidates && !candidates.isEmpty()
                                        && candidates.get(0) instanceof Map<?, ?> candidate
                                        && candidate.get("content") instanceof Map<?, ?> content
                                        && content.get("parts") instanceof List<?> parts
                                        && !parts.isEmpty() && parts.get(0) instanceof Map<?, ?> part
                                        && part.get("text") instanceof String text) {
                                try {
                                        return json.readValue(text, new TypeReference<Map<String, Object>>() {
                                        });
                                } catch (JsonProcessingException e) {
                                        lastParseError = e;
                                }
                        }
        }
                if (lastParseError != null) {
                        throw lastParseError;
                }
                throw new LlmResponseException("Gemini returned no JSON response for '" + toolName + "'.");
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> decompose(String sourceField, String rawValue, DecompositionRule rule) {
        String system = "You are a strict field-decomposition assistant for SWIFT financial messages. "
                + "You are given a free-text field value and a fixed list of target sub-element names. "
                + "Map lines/segments of the value to those sub-elements ONLY if you are highly confident. "
                + "If you are not highly confident for ANY sub-element, set matched=false and explain why - "
                + "do not partially guess. Never invent values not present in the source text.";
        String user = "Source field: " + sourceField + "\n"
                + "Pattern description from reference doc: " + rule.getPatternDescription() + "\n"
                + "Target sub-elements required: " + rule.getSubElements().keySet() + "\n"
                + "Raw value:\n" + rawValue;

        Map<String, Object> subElementProperties = new LinkedHashMap<>();
        for (String subElement : rule.getSubElements().keySet()) {
            subElementProperties.put(subElement, Map.of("type", "string"));
        }

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "matched", Map.of("type", "boolean"),
                        "sub_elements", Map.of(
                                "type", "object",
                                "properties", subElementProperties,
                                "required", List.copyOf(subElementProperties.keySet())),
                        "reason_if_not_matched", Map.of("type", "string")
                ),
                "required", List.of("matched")
        );

        Map<String, Object> output = callStructured(system, user, "report_decomposition",
                "Report the decomposition result for the given field.", schema);

        if (Boolean.TRUE.equals(output.get("matched")) && output.get("sub_elements") instanceof Map<?, ?> se) {
            Map<String, String> result = new LinkedHashMap<>();
            se.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
            return result;
        }

        String fallback = rule.getFallbackIfUnparseable() == null ? "raise_error" : rule.getFallbackIfUnparseable();
        if (!"raise_error".equals(fallback)) {
            throw new SemanticDecompositionGapException(sourceField, rawValue,
                    "fallback_if_unparseable is set to '" + fallback + "', but this engine build only "
                            + "implements 'raise_error'.");
        }
        throw new SemanticDecompositionGapException(sourceField, rawValue,
                String.valueOf(output.getOrDefault("reason_if_not_matched", "Could not confidently decompose the field.")));
    }

    public String convertLlmAssisted(String sourceField, String targetPath, String notes, List<EdgeCase> edgeCases, String rawValue) {
        String system = "You are a strict, conservative field-conversion assistant for SWIFT MT/MX financial "
                + "messages. Convert the given source value to the target field using ONLY the notes and edge "
                + "cases provided. If you are not highly confident, set confidence='low' and explain why - the "
                + "caller treats low confidence as a hard failure, not a soft warning. Never invent information "
                + "not derivable from the source value or the provided notes.";
        String user = "Source field: " + sourceField + "\n"
                + "Target path: " + targetPath + "\n"
                + "Notes from reference doc: " + (notes == null ? "(none)" : notes) + "\n"
                + "Edge cases from reference doc: " + edgeCases + "\n"
                + "Raw source value: " + rawValue;

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of("type", "string"),
                        "confidence", Map.of("type", "string", "enum", List.of("high", "low")),
                        "explanation", Map.of("type", "string")
                ),
                "required", List.of("value", "confidence", "explanation")
        );

        Map<String, Object> output = callStructured(system, user, "report_conversion", "Report the converted value.", schema);
        if (!"high".equals(output.get("confidence"))) {
            throw new TransformationException("LLM-assisted conversion for field " + sourceField
                    + " returned low confidence: " + output.get("explanation")
                    + ". Refusing to use a low-confidence value - tighten the mapping doc's notes/edge_cases for this field.");
        }
        return String.valueOf(output.get("value"));
    }

    public Map<String, Object> semanticAudit(Map<String, String> parsedFields, List<FieldMapping> rules, Map<String, String> convertedTree) {
        String system = "You are a meticulous SWIFT MT/MX conversion auditor. You are given the original parsed "
                + "source fields, the reference mapping document's field rules, and the converted target fields. "
                + "Check every mapped field for correctness against the stated rule. Classify each problem as "
                + "CONVERSION_ERROR (the mapping rule was correct but not applied correctly) or MAPPING_GAP (the "
                + "mapping rule itself is ambiguous/insufficient). Do not flag stylistic preferences. Do not "
                + "invent problems not supported by the given data. IMPORTANT: for each finding's 'field', use the "
                + "EXACT source_field or target_path string as given in the field mapping rules list below (e.g. "
                + "'32A' or 'CdtTrfTxInf.IntrBkSttlmAmt'), not a paraphrase or a made-up path - the caller "
                + "cross-checks this string against the mapping doc and flags anything it can't match.";
        String user = "Source fields:\n" + parsedFields + "\n\n"
                + "Field mapping rules applied:\n" + describeRules(rules) + "\n\n"
                + "Converted target fields:\n" + convertedTree;

        Map<String, Object> findingSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "field", Map.of("type", "string"),
                        "issue", Map.of("type", "string"),
                        "classification", Map.of("type", "string", "enum", List.of("CONVERSION_ERROR", "MAPPING_GAP")),
                        "severity", Map.of("type", "string", "enum", List.of("error", "warning"))
                ),
                "required", List.of("field", "issue", "classification", "severity")
        );
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "is_valid", Map.of("type", "boolean"),
                        "findings", Map.of("type", "array", "items", findingSchema)
                ),
                "required", List.of("is_valid", "findings")
        );

        return callStructured(system, user, "report_audit", "Report the semantic audit result.", schema);
    }

    /**
     * Deliberately terse - just the rule shape, not the full essay. This
     * mapping doc's notes fields have grown into multi-paragraph changelog
     * histories (version-by-version bug-fix rationale, citations, etc.)
     * which are valuable for a human reader but are pure noise for an
     * audit prompt: they inflate token cost/latency for every single
     * conversion and dilute the model's attention away from the actual
     * rule it's meant to be checking. Only the first sentence of notes is
     * included, which is consistently where this doc's authors put the
     * actual rule statement (e.g. the UHB/CBPR+ citation) before the
     * changelog commentary starts.
     */
    private String describeRules(List<FieldMapping> rules) {
        StringBuilder sb = new StringBuilder();
        for (FieldMapping fm : rules) {
            sb.append("- ").append(fm.getSourceField()).append(" -> ").append(fm.getTargetPath())
                    .append(" [").append(fm.getTransformation()).append("]");
            if (fm.getNotes() != null && !fm.getNotes().isBlank()) {
                sb.append(" notes: ").append(firstSentence(fm.getNotes()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String firstSentence(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        int idx = collapsed.indexOf(". ");
        int cutoff = idx >= 0 ? idx + 1 : collapsed.length();
        cutoff = Math.min(cutoff, 220); // hard cap even if no sentence boundary found early
        return collapsed.length() > cutoff ? collapsed.substring(0, cutoff) + "..." : collapsed;
    }
}
