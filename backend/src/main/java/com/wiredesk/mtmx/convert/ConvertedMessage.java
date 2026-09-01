package com.wiredesk.mtmx.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConvertedMessage {
    private String targetFormat;
    private Map<String, String> tree = new LinkedHashMap<>();
    private String renderedText;
    private List<Map<String, Object>> fieldTrace = new ArrayList<>();
    private List<String> conversionWarnings = new ArrayList<>();

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public Map<String, String> getTree() {
        return tree;
    }

    public void setTree(Map<String, String> tree) {
        this.tree = tree;
    }

    public String getRenderedText() {
        return renderedText;
    }

    public void setRenderedText(String renderedText) {
        this.renderedText = renderedText;
    }

    public List<Map<String, Object>> getFieldTrace() {
        return fieldTrace;
    }

    public void setFieldTrace(List<Map<String, Object>> fieldTrace) {
        this.fieldTrace = fieldTrace;
    }

    public List<String> getConversionWarnings() {
        return conversionWarnings;
    }

    public void setConversionWarnings(List<String> conversionWarnings) {
        this.conversionWarnings = conversionWarnings;
    }
}
