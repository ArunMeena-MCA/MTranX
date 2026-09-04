package com.wiredesk.mtmx.mapping.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines how to split a multiline/free-text MT field into structured MX
 * sub-elements, or vice versa.
 *
 * <p><b>Optional leading account line:</b> many SWIFT party fields (50a,
 * 59a, and others) follow the shape "optional /account line, then
 * positional name/address content". Setting {@code stripAccountLinePrefix}
 * handles this deterministically and correctly: if the first line matches
 * {@code accountLinePattern}, it's extracted into {@code accountSubElement}
 * and removed from the line list BEFORE the {@code line:N} indices in
 * {@code subElements} are resolved - so "line:0" always means "the first
 * name/address line", regardless of whether an account line preceded it.
 * This is deliberately a separate mechanism from a generic "optional"
 * flag on individual sub-elements: a flat optional flag alone can't
 * express the required re-indexing, so it would silently produce wrong
 * results (e.g. mapping the account number in as the name) rather than
 * either the right answer or a clean failure.
 */
public class DecompositionRule {
    private String patternDescription;
    private Map<String, Object> subElements = new LinkedHashMap<>();
    private String fallbackIfUnparseable = "raise_error";

    private boolean stripAccountLinePrefix = false;
    private String accountLinePattern = "^/(.+)$";
    private String accountSubElement = "AccountId";
    private Map<String, String> subElementTargets = new LinkedHashMap<>();

    /**
     * Sub-element name -> {pattern, if_match_target, else_target}. Checked
     * BEFORE subElementTargets for a given key: if present, the extracted
     * value's destination is chosen by matching pattern (full-string match)
     * against the value itself, instead of using a single fixed path. Absent
     * for a key (the common case) means unchanged, existing behavior - this
     * is purely additive. See ConditionalSubElementTarget's own Javadoc for
     * why this exists instead of a second field_mappings entry.
     */
    private Map<String, ConditionalSubElementTarget> conditionalSubElementTargets = new LinkedHashMap<>();

    /**
     * Optional: enriches this decomposition's free-text address lines with
     * structured street/city/country via the libpostal sidecar. Null (the
     * default, for every existing entry) means no change to current
     * behavior at all. See StructuredAddressRule's own Javadoc.
     */
    private StructuredAddressRule structuredAddress;

    public String getPatternDescription() {
        return patternDescription;
    }

    public void setPatternDescription(String patternDescription) {
        this.patternDescription = patternDescription;
    }

    public Map<String, Object> getSubElements() {
        return subElements;
    }

    public void setSubElements(Map<String, Object> subElements) {
        this.subElements = subElements;
    }

    public String getFallbackIfUnparseable() {
        return fallbackIfUnparseable;
    }

    public void setFallbackIfUnparseable(String fallbackIfUnparseable) {
        this.fallbackIfUnparseable = fallbackIfUnparseable;
    }

    public boolean isStripAccountLinePrefix() {
        return stripAccountLinePrefix;
    }

    public void setStripAccountLinePrefix(boolean stripAccountLinePrefix) {
        this.stripAccountLinePrefix = stripAccountLinePrefix;
    }

    public String getAccountLinePattern() {
        return accountLinePattern;
    }

    public void setAccountLinePattern(String accountLinePattern) {
        this.accountLinePattern = accountLinePattern;
    }

    public String getAccountSubElement() {
        return accountSubElement;
    }

    public void setAccountSubElement(String accountSubElement) {
        this.accountSubElement = accountSubElement;
    }

    /**
     * Sub-element name -> absolute target_path override. Most sub-elements
     * land under {@code <field_mapping.target_path>.<sub_element_name>} by
     * default (e.g. "Nm" under "CdtTrfTxInf.Dbtr" -> "CdtTrfTxInf.Dbtr.Nm").
     * Use this when a sub-element needs to land somewhere else entirely -
     * most commonly, routing an extracted account number to a SIBLING
     * element (e.g. "CdtTrfTxInf.DbtrAcct.Id") rather than nesting it
     * under the party name/address element, which is how real ISO 20022
     * structures Dbtr vs DbtrAcct.
     */
    public Map<String, String> getSubElementTargets() {
        return subElementTargets;
    }

    public void setSubElementTargets(Map<String, String> subElementTargets) {
        this.subElementTargets = subElementTargets;
    }

    public Map<String, ConditionalSubElementTarget> getConditionalSubElementTargets() {
        return conditionalSubElementTargets;
    }

    public void setConditionalSubElementTargets(Map<String, ConditionalSubElementTarget> conditionalSubElementTargets) {
        this.conditionalSubElementTargets = conditionalSubElementTargets;
    }

    public StructuredAddressRule getStructuredAddress() {
        return structuredAddress;
    }

    public void setStructuredAddress(StructuredAddressRule structuredAddress) {
        this.structuredAddress = structuredAddress;
    }
}