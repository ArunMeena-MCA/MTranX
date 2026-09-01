package com.wiredesk.mtmx.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Maps to the exact JSON body the existing React frontend already sends:
 * {"raw_text": "...", "source_format": "MT103", "target_format": "PACS008"}
 * (snake_case <-> camelCase handled globally via
 * spring.jackson.property-naming-strategy=SNAKE_CASE in application.yml).
 */
public class ConvertRequest {

    @NotBlank(message = "raw_text must not be blank")
    private String rawText;

    @NotBlank(message = "source_format must not be blank")
    private String sourceFormat;

    @NotBlank(message = "target_format must not be blank")
    private String targetFormat;

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }
}
