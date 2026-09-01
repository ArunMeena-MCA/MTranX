package com.wiredesk.mtmx.exception;

/**
 * A field/value was found in the parsed source message for which the
 * mapping document defines no rule, and unmapped_fields_policy is
 * "error" (the recommended, safe default). The engine will not invent
 * a mapping.
 */
public class UnmappableFieldException extends MtmxException {
    private final String fieldId;

    public UnmappableFieldException(String fieldId, String context) {
        super("No mapping rule for source field '" + fieldId + "'. " + context);
        this.fieldId = fieldId;
    }

    public String getFieldId() {
        return fieldId;
    }
}
