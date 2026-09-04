package com.wiredesk.mtmx.mapping.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in add-on to a decompose_party entry: after the normal sub_elements
 * extraction runs (producing AdrLine content, unchanged), ALSO feed that
 * same free-text content through the libpostal address-parser sidecar and
 * write street/city/country to their own structured pacs.008 elements when
 * the parser is confident - implementing the "hybrid address" model Swift
 * requires from 14 November 2026 (structured fields ALONGSIDE, not instead
 * of, AdrLine - see MT103_TO_PACS00800108.yaml's ADDRESS POLICY note).
 *
 * <p>Has no effect at all unless mtmx.address-parser-enabled=true - see
 * AddressParserClient's Javadoc for the fail-soft behavior when the sidecar
 * is unreachable.
 */
public class StructuredAddressRule {
    /** Which extracted sub-element (by base name, e.g. "AdrLine") holds the raw address lines to parse. */
    private String sourceSubElement;

    /** Logical key ("street", "city", "postcode", "country") -> absolute target_path. Any key may be omitted. */
    private Map<String, String> targets = new LinkedHashMap<>();

    public String getSourceSubElement() {
        return sourceSubElement;
    }

    public void setSourceSubElement(String sourceSubElement) {
        this.sourceSubElement = sourceSubElement;
    }

    public Map<String, String> getTargets() {
        return targets;
    }

    public void setTargets(Map<String, String> targets) {
        this.targets = targets;
    }
}
