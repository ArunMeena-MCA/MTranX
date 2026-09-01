package com.wiredesk.mtmx.parser;

import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParsedMessage {
    private final String declaredFormat;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final List<String> fieldOrder = new ArrayList<>();
    private final String rawText;
    private Document xmlDocument; // populated for MX sources only

    public ParsedMessage(String declaredFormat, String rawText) {
        this.declaredFormat = declaredFormat;
        this.rawText = rawText;
    }

    /** Repeated tags are merged (newline-joined), mirroring how the earlier engine handled them. */
    public void addField(String tag, String value) {
        fields.merge(tag, value, (a, b) -> a + "\n" + b);
        fieldOrder.add(tag);
    }

    public String getDeclaredFormat() {
        return declaredFormat;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public List<String> getFieldOrder() {
        return fieldOrder;
    }

    public String getRawText() {
        return rawText;
    }

    public Document getXmlDocument() {
        return xmlDocument;
    }

    public void setXmlDocument(Document xmlDocument) {
        this.xmlDocument = xmlDocument;
    }
}
