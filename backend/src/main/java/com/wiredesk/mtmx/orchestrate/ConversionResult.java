package com.wiredesk.mtmx.orchestrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConversionResult {
    private String sourceFormat;
    private String targetFormat;
    private String renderedOutput;
    private Map<String, String> parsedSourceFields;
    private Map<String, String> convertedTree;
    private List<String> validationWarnings;
    private String llmAuditStatus;
    private int attempts;
    private List<String> auditWarnings = new ArrayList<>();
    private List<Map<String, Object>> fieldTrace = new ArrayList<>();
    private List<Map<String, Object>> pipelineSteps = new ArrayList<>();

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getRenderedOutput() {
        return renderedOutput;
    }

    public void setRenderedOutput(String renderedOutput) {
        this.renderedOutput = renderedOutput;
    }

    public Map<String, String> getParsedSourceFields() {
        return parsedSourceFields;
    }

    public void setParsedSourceFields(Map<String, String> parsedSourceFields) {
        this.parsedSourceFields = parsedSourceFields;
    }

    public Map<String, String> getConvertedTree() {
        return convertedTree;
    }

    public void setConvertedTree(Map<String, String> convertedTree) {
        this.convertedTree = convertedTree;
    }

    public List<String> getValidationWarnings() {
        return validationWarnings;
    }

    public void setValidationWarnings(List<String> validationWarnings) {
        this.validationWarnings = validationWarnings;
    }

    public String getLlmAuditStatus() {
        return llmAuditStatus;
    }

    public void setLlmAuditStatus(String llmAuditStatus) {
        this.llmAuditStatus = llmAuditStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public List<String> getAuditWarnings() {
        return auditWarnings;
    }

    public void setAuditWarnings(List<String> auditWarnings) {
        this.auditWarnings = auditWarnings;
    }

    public List<Map<String, Object>> getFieldTrace() {
        return fieldTrace;
    }

    public void setFieldTrace(List<Map<String, Object>> fieldTrace) {
        this.fieldTrace = fieldTrace;
    }

    public List<Map<String, Object>> getPipelineSteps() {
        return pipelineSteps;
    }

    public void setPipelineSteps(List<Map<String, Object>> pipelineSteps) {
        this.pipelineSteps = pipelineSteps;
    }
}
