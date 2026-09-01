package com.wiredesk.mtmx.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValidationRule {
    private String ruleId;
    private String description;
    private List<String> appliesTo = new ArrayList<>();
    private String severity = "error";
    private String logic;

    /**
     * Identifies which machine-checkable shape this rule uses, matching
     * one of ValidatorService's known evaluators (e.g.
     * "conditional_currency_mismatch_requires", "presence_requires_value",
     * "mutual_exclusion", "source_alternative_group_required",
     * "currency_precision_check", "structured_address_required"). Absent
     * (null) means the rule is descriptive-only - recorded for
     * human/LLM-reviewer judgement, same as this schema's original
     * design, not auto-enforced. Deliberately NOT a fixed Java enum with
     * one field per shape: params below stays a generic map so adding a
     * new rule shape only means adding one new evaluator method plus
     * whatever params keys it reads, not a model-class migration.
     */
    private String ruleType;

    /** rule_type-specific parameters, read generically by the matching evaluator in ValidatorService. */
    private Map<String, Object> params = new LinkedHashMap<>();

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(List<String> appliesTo) {
        this.appliesTo = appliesTo;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getLogic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
