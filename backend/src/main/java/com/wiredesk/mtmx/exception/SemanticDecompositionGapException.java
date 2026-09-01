package com.wiredesk.mtmx.exception;

/**
 * A source field was parsed at the syntactic level (e.g. Prowide found
 * tag :59:) but the mapping doc does not define how to decompose its
 * free-text content into the structured target elements required. The
 * engine refuses to guess the split.
 */
public class SemanticDecompositionGapException extends MtmxException {
    private final String sourceField;
    private final String rawValue;

    public SemanticDecompositionGapException(String sourceField, String rawValue, String reason) {
        super("Cannot semantically decompose field '" + sourceField + "' (value=" + rawValue + "): " + reason);
        this.sourceField = sourceField;
        this.rawValue = rawValue;
    }

    public String getSourceField() {
        return sourceField;
    }

    public String getRawValue() {
        return rawValue;
    }
}
