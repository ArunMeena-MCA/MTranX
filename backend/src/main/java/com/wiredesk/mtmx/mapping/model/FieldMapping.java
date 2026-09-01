package com.wiredesk.mtmx.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FieldMapping {
    private String sourceField;
    private String sourceName;
    private String targetPath;
    private boolean mandatory;

    /**
     * One of: direct_copy, constant, date_format, code_list_lookup,
     * truncate, uppercase, decompose_party, llm_assisted, generated.
     * Kept as a plain String (validated by CompletenessAuditor /
     * ConverterService) rather than an enum, so an unrecognised value
     * in the YAML surfaces as a clear "unknown transformation" error
     * instead of a YAML-binding failure with a less useful message.
     */
    private String transformation;

    private Integer maxLength;
    private Integer minLength;
    private String allowedPattern;
    private Map<String, String> codeList = new LinkedHashMap<>();
    private String constantValue;

    /**
     * Only used when transformation=generated. One of: uuid, timestamp.
     * For values that must be synthesized fresh per message (e.g. a
     * message ID or creation timestamp) rather than copied from any
     * source field or held as one fixed value across all conversions -
     * that's what 'constant' is for.
     */
    private String generator;

    /**
     * Only used when transformation=conditional. Derives a value from
     * whether OTHER source fields are present, not from this entry's own
     * source_field's value. See ConditionalRule for when this applies.
     */
    private ConditionalRule conditional;
    private String sourceDateFormat;
    private String targetDateFormat;
    private String extractPattern;
    private DecompositionRule decomposition;
    private List<EdgeCase> conditionalRules = new ArrayList<>();
    private List<EdgeCase> edgeCases = new ArrayList<>();
    private String notes;

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getTransformation() {
        return transformation;
    }

    public void setTransformation(String transformation) {
        this.transformation = transformation;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public String getAllowedPattern() {
        return allowedPattern;
    }

    public void setAllowedPattern(String allowedPattern) {
        this.allowedPattern = allowedPattern;
    }

    public Map<String, String> getCodeList() {
        return codeList;
    }

    public void setCodeList(Map<String, String> codeList) {
        this.codeList = codeList;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public ConditionalRule getConditional() {
        return conditional;
    }

    public void setConditional(ConditionalRule conditional) {
        this.conditional = conditional;
    }

    public String getSourceDateFormat() {
        return sourceDateFormat;
    }

    public void setSourceDateFormat(String sourceDateFormat) {
        this.sourceDateFormat = sourceDateFormat;
    }

    public String getTargetDateFormat() {
        return targetDateFormat;
    }

    public void setTargetDateFormat(String targetDateFormat) {
        this.targetDateFormat = targetDateFormat;
    }

    public String getExtractPattern() {
        return extractPattern;
    }

    public void setExtractPattern(String extractPattern) {
        this.extractPattern = extractPattern;
    }

    public DecompositionRule getDecomposition() {
        return decomposition;
    }

    public void setDecomposition(DecompositionRule decomposition) {
        this.decomposition = decomposition;
    }

    public List<EdgeCase> getConditionalRules() {
        return conditionalRules;
    }

    public void setConditionalRules(List<EdgeCase> conditionalRules) {
        this.conditionalRules = conditionalRules;
    }

    public List<EdgeCase> getEdgeCases() {
        return edgeCases;
    }

    public void setEdgeCases(List<EdgeCase> edgeCases) {
        this.edgeCases = edgeCases;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
