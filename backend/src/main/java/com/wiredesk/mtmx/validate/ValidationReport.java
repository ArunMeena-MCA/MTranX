package com.wiredesk.mtmx.validate;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    private boolean valid;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String classification = "OK"; // OK | CONVERSION_ERROR | MAPPING_GAP

    /**
     * Whether the LLM semantic audit (Layer 2) actually ran and produced a
     * result, so a caller can tell "clean because both layers passed"
     * apart from "clean because the LLM layer never ran" - a flaky/down
     * LLM API no longer silently reads as "no issues found".
     * "ran" | "skipped_not_reached" (Layer 1 already failed) | "error:&lt;reason&gt;".
     */
    private String llmAuditStatus = "skipped_not_reached";

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getLlmAuditStatus() {
        return llmAuditStatus;
    }

    public void setLlmAuditStatus(String llmAuditStatus) {
        this.llmAuditStatus = llmAuditStatus;
    }
}
