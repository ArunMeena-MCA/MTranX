package com.wiredesk.mtmx.exception;

/** No mapping document exists for the requested (source_format, target_format) pair. */
public class MappingDocNotFoundException extends MtmxException {
    public MappingDocNotFoundException(String message) {
        super(message);
    }
}
