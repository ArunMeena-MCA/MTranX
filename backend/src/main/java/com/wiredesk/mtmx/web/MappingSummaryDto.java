package com.wiredesk.mtmx.web;

/** Serializes to {"conversion_id": ..., "source_format": ..., "target_format": ...} - what the frontend expects. */
public class MappingSummaryDto {
    private final String conversionId;
    private final String sourceFormat;
    private final String targetFormat;

    public MappingSummaryDto(String conversionId, String sourceFormat, String targetFormat) {
        this.conversionId = conversionId;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public String getConversionId() {
        return conversionId;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }
}
