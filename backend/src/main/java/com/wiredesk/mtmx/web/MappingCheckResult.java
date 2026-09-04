package com.wiredesk.mtmx.web;

/**
 * Response body for GET /api/mappings/check - a cheap, no-file-upload
 * preview of whether a given source/target pair already has a mapping
 * doc on disk, so the frontend can show an overwrite notice before the
 * user even attaches files.
 */
public class MappingCheckResult {
    private final boolean exists;
    private final String conversionId;
    private final String filename;

    public MappingCheckResult(boolean exists, String conversionId, String filename) {
        this.exists = exists;
        this.conversionId = conversionId;
        this.filename = filename;
    }

    public boolean isExists() {
        return exists;
    }

    public String getConversionId() {
        return conversionId;
    }

    public String getFilename() {
        return filename;
    }
}
