package com.wiredesk.mtmx.exception;

/** The automated dashboard's Oracle source (pmtb_msg_dly_msg_out) isn't configured, or the fetch query failed. */
public class OracleFetchException extends MtmxException {
    public OracleFetchException(String message) {
        super(message);
    }

    public OracleFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
