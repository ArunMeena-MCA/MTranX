package com.wiredesk.mtmx.web;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/automated/convert: {"reference_no": "...", "message": "..."}. */
public class AutomatedConvertRequest {

    @NotBlank(message = "reference_no must not be blank")
    private String referenceNo;

    @NotBlank(message = "message must not be blank")
    private String message;

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
