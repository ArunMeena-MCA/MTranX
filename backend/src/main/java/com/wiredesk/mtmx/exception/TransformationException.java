package com.wiredesk.mtmx.exception;

/** A declared transformation (date_format, code_list_lookup, etc.) failed to execute or had no matching rule. */
public class TransformationException extends MtmxException {
    public TransformationException(String message) {
        super(message);
    }
}
