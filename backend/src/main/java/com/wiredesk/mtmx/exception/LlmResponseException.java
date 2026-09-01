package com.wiredesk.mtmx.exception;

/** The LLM did not return a well-formed structured response (missing tool call, invalid JSON, HTTP error, etc.). */
public class LlmResponseException extends MtmxException {
    public LlmResponseException(String message) {
        super(message);
    }
}
