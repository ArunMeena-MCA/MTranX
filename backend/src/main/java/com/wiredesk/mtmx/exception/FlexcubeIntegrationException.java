package com.wiredesk.mtmx.exception;

/**
 * The OBPS/FLEXCUBE SFTP integration could not connect, list, read, write, or move a file -
 * i.e. a failure of the integration plumbing itself, distinct from a single message failing
 * MT-to-MX translation (which FlexcubeFileWatcherJob routes to MT_failed instead of throwing).
 */
public class FlexcubeIntegrationException extends MtmxException {
    public FlexcubeIntegrationException(String message) {
        super(message);
    }

    public FlexcubeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
