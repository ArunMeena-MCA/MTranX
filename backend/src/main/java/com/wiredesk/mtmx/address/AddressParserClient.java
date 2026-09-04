package com.wiredesk.mtmx.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wiredesk.mtmx.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the libpostal Python sidecar service to split free-text address
 * lines (e.g. MT103 50K/59's unstructured content) into street/city/country
 * components, for the pacs.008 "hybrid address" model Swift requires from 14
 * November 2026 (structured StrtNm/TwnNm/Ctry populated ALONGSIDE, not
 * instead of, the existing free-text AdrLine - see this document's ADDRESS
 * POLICY note).
 *
 * <p>Deliberately fails SOFT, not hard: if the sidecar is unreachable,
 * misconfigured, or returns something unexpected, this returns null and the
 * caller simply skips structured enrichment for that message - conversion
 * proceeds with AdrLine-only, exactly today's behavior. A missing "nice to
 * have" enrichment step should never take down a conversion that would
 * otherwise succeed; this is intentionally NOT the same failure posture as
 * GeminiClient's llm_assisted path, which fails hard because THAT value is
 * the entry's only reason to exist.
 */
@Component
public class AddressParserClient {

    private static final Logger log = LoggerFactory.getLogger(AddressParserClient.class);

    private final AppProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();

    public AddressParserClient(AppProperties props) {
        this.props = props;
    }

    /**
     * @param lines the free-text address lines AFTER the name line (e.g.
     *              50K's AdrLine content), in original order.
     * @return the parsed address, or null if parsing is disabled, the
     *         sidecar is unreachable, or the response couldn't be understood.
     *         Never throws - see class Javadoc for why.
     */
    public ParsedAddress parse(List<String> lines) {
        if (!props.isAddressParserEnabled() || lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            String payload = json.writeValueAsString(Map.of("lines", lines));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getAddressParserUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Address parser sidecar returned HTTP {} - skipping structured address enrichment for this "
                        + "message; falling back to AdrLine-only.", response.statusCode());
                return null;
            }
            JsonNode node = json.readTree(response.body());
            ParsedAddress result = new ParsedAddress();
            result.setStreet(textOrNull(node, "street"));
            result.setCity(textOrNull(node, "city"));
            result.setPostcode(textOrNull(node, "postcode"));
            result.setCountryCode(textOrNull(node, "country_code"));
            result.setConfident(node.path("confident").asBoolean(false));
            return result;
        } catch (Exception e) {
            // Network error, timeout, malformed JSON, sidecar not running -
            // all treated the same way: this is an enrichment step, not a
            // required one. Logged (not silently swallowed) so an operator
            // who enabled this feature can tell it isn't actually working,
            // but it must never fail a conversion that would otherwise
            // succeed on AdrLine alone.
            log.warn("Address parser sidecar call failed ({}) - skipping structured address enrichment for this "
                    + "message; falling back to AdrLine-only.", e.toString());
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
