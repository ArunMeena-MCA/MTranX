package com.wiredesk.mtmx.web;

import java.util.List;

/** Response body for POST /api/mappings/upload. */
public class MappingUploadResult {
    private final String conversionId;
    private final String sourceFormat;
    private final String targetFormat;
    private final String mappingFilename;
    private final String xsdFilename;
    private final boolean overwritten;
    private final List<String> warnings;

    public MappingUploadResult(String conversionId, String sourceFormat, String targetFormat,
                                String mappingFilename, String xsdFilename, boolean overwritten,
                                List<String> warnings) {
        this.conversionId = conversionId;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
        this.mappingFilename = mappingFilename;
        this.xsdFilename = xsdFilename;
        this.overwritten = overwritten;
        this.warnings = warnings;
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

    public String getMappingFilename() {
        return mappingFilename;
    }

    public String getXsdFilename() {
        return xsdFilename;
    }

    public boolean isOverwritten() {
        return overwritten;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
