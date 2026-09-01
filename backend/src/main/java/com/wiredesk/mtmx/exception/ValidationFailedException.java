package com.wiredesk.mtmx.exception;

import com.wiredesk.mtmx.validate.ValidationReport;

/** The converted message failed validation and could not be corrected within the retry budget. */
public class ValidationFailedException extends MtmxException {
    private final ValidationReport report;

    public ValidationFailedException(ValidationReport report) {
        super("Validation failed after retries. Errors: " + report.getErrors());
        this.report = report;
    }

    public ValidationReport getReport() {
        return report;
    }
}
