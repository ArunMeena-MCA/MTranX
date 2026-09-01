package com.wiredesk.mtmx.exception;

/**
 * Base class for every engine error. Design principle carried over from
 * the original engine: every exception here represents a case where the
 * engine refused to guess. If information needed to complete a
 * conversion correctly is missing from the reference mapping document,
 * the engine stops and says exactly what is missing - it never falls
 * back to a best-effort guess for a financial message field.
 */
public class MtmxException extends RuntimeException {
    public MtmxException(String message) {
        super(message);
    }

    public MtmxException(String message, Throwable cause) {
        super(message, cause);
    }
}
