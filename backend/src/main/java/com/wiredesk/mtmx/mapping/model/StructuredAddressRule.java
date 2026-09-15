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

    /**
     * Optional. When set, the repeated-AdrLine target this same decompose_party entry already
     * writes (e.g. "CdtTrfTxInf.Cdtr.PstlAdr.AdrLine") - enables a SEPARATE, opt-in cleanup step
     * (2026-09-15, user-requested "act intelligently wherever confident"): once libpostal
     * confidently resolves street/city, any individual AdrLine occurrence whose value is an
     * EXACT (case-insensitive, trimmed) match to that street or city is now genuinely redundant
     * with the structured field it duplicates - removed and the remaining AdrLine occurrences
     * re-indexed contiguously. Deliberately narrow: only a clean, whole-line, exact match is
     * removed - a line like "India New Delhi" that merely CONTAINS a country name as a substring
     * is left completely untouched, since stripping it would also discard "New Delhi" with
     * nowhere else for that content to go. Country is deliberately NOT deduped this way (the
     * sidecar only returns country_code, not the original text span that was recognized, so
     * there is nothing to exact-match against without guessing). Null (the default, for 50F/59F
     * and the numbered-line 50K/59 variants, which extract Ctry/TwnNm deterministically rather
     * than via this sidecar) means no cleanup runs - AdrLine stays exactly as the main decompose
     * loop wrote it, unchanged from before this field existed.
     */
    private String adrLineTargetPath;

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

    public String getAdrLineTargetPath() {
        return adrLineTargetPath;
    }

    public void setAdrLineTargetPath(String adrLineTargetPath) {
        this.adrLineTargetPath = adrLineTargetPath;
    }
}
