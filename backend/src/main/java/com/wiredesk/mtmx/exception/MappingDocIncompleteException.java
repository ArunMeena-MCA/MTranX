package com.wiredesk.mtmx.exception;

import java.util.List;

/**
 * The mapping document exists but is missing information required to
 * guarantee a correct, non-guessed conversion. `missing` is a
 * structured list of gaps so the operator can fix the doc and re-run.
 */
public class MappingDocIncompleteException extends MtmxException {
    private final String conversionId;
    private final List<String> missing;

    public MappingDocIncompleteException(String conversionId, List<String> missing) {
        super(buildMessage(conversionId, missing));
        this.conversionId = conversionId;
        this.missing = missing;
    }

    private static String buildMessage(String conversionId, List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mapping document for '").append(conversionId)
          .append("' is incomplete - refusing to guess.\n")
          .append("Missing/ambiguous items (").append(missing.size()).append("):\n");
        for (String m : missing) {
            sb.append("  - ").append(m).append("\n");
        }
        return sb.toString();
    }

    public String getConversionId() {
        return conversionId;
    }

    public List<String> getMissing() {
        return missing;
    }
}
