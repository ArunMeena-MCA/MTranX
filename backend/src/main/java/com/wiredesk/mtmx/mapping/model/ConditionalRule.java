package com.wiredesk.mtmx.mapping.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a value derived from the PRESENCE of other source fields, rather
 * than copied/transformed from any single field's value. Generic across
 * any conversion pair - e.g. deriving pacs.008 GrpHdr/SttlmInf/SttlmMtd
 * from whether MT103 correspondent-agent fields (53A/54A/55A) are present,
 * or an analogous envelope decision in a different mapping doc. Keeps that
 * business rule declarative in the YAML instead of hardcoded per-pair in
 * Java, matching the rest of this engine's mapping-doc-driven design.
 *
 * <p>Only checks presence/absence (is the field in the parsed message at
 * all), not value content - deliberately narrow scope. A rule that needs
 * to branch on a field's actual value belongs in code_list_lookup or a
 * new transformation type, not here.
 */
public class ConditionalRule {
    private List<String> checkFields = new ArrayList<>();
    private String ifAnyPresent;
    private String ifNonePresent;

    /** Alternative to the literal ifAnyPresent/ifNonePresent: copy another field's raw value instead of a fixed string. */
    private String ifAnyPresentField;
    private String ifNonePresentField;

    /**
     * When true, this entry produces NOTHING for that branch (not an
     * error, not a written value) - for when a DIFFERENT field_mappings
     * entry already covers that branch and this entry exists only to
     * handle the other branch. Without this, an entry that intentionally
     * covers only one branch would need a placeholder value for the
     * other, which risks writing a bogus/empty element into the output.
     */
    private boolean skipIfAnyPresent = false;
    private boolean skipIfNonePresent = false;

    public List<String> getCheckFields() {
        return checkFields;
    }

    public void setCheckFields(List<String> checkFields) {
        this.checkFields = checkFields;
    }

    public String getIfAnyPresent() {
        return ifAnyPresent;
    }

    public void setIfAnyPresent(String ifAnyPresent) {
        this.ifAnyPresent = ifAnyPresent;
    }

    public String getIfNonePresent() {
        return ifNonePresent;
    }

    public void setIfNonePresent(String ifNonePresent) {
        this.ifNonePresent = ifNonePresent;
    }

    public String getIfAnyPresentField() {
        return ifAnyPresentField;
    }

    public void setIfAnyPresentField(String ifAnyPresentField) {
        this.ifAnyPresentField = ifAnyPresentField;
    }

    public String getIfNonePresentField() {
        return ifNonePresentField;
    }

    public void setIfNonePresentField(String ifNonePresentField) {
        this.ifNonePresentField = ifNonePresentField;
    }

    public boolean isSkipIfAnyPresent() {
        return skipIfAnyPresent;
    }

    public void setSkipIfAnyPresent(boolean skipIfAnyPresent) {
        this.skipIfAnyPresent = skipIfAnyPresent;
    }

    public boolean isSkipIfNonePresent() {
        return skipIfNonePresent;
    }

    public void setSkipIfNonePresent(boolean skipIfNonePresent) {
        this.skipIfNonePresent = skipIfNonePresent;
    }
}
