package com.wiredesk.mtmx.exception;

/**
 * A mapping/XSD upload would overwrite an existing file (or the typed
 * source/target format strings don't match the uploaded file's own
 * name) and the request didn't set confirm=true. Thrown before any
 * write happens - the upload is a no-op until the caller resubmits
 * with confirmation.
 */
public class MappingUploadConflictException extends MtmxException {
    public MappingUploadConflictException(String message) {
        super(message);
    }
}
