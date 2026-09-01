package com.wiredesk.mtmx.exception;

/** The raw input text could not be parsed as a syntactically valid message of the declared format. */
public class ParsingException extends MtmxException {
    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
