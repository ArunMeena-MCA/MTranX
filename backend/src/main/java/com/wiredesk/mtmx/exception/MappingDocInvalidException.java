package com.wiredesk.mtmx.exception;

/** The mapping document fails basic structural/schema validation (bad YAML, wrong types, etc.). */
public class MappingDocInvalidException extends MtmxException {
    public MappingDocInvalidException(String message) {
        super(message);
    }
}
