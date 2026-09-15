package com.wiredesk.mtmx.automation;

/** One row pulled from pmtb_msg_dly_msg_out - see OracleMessageFetchService. */
public class FetchedMtMessage {
    private final String referenceNo;
    private final String message;

    public FetchedMtMessage(String referenceNo, String message) {
        this.referenceNo = referenceNo;
        this.message = message;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getMessage() {
        return message;
    }
}
