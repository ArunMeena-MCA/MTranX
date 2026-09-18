package com.wiredesk.mtmx.automation;

import java.io.IOException;

/**
 * Opens one {@link FlexcubeFileSession} and runs a unit of work against it -
 * FlexcubeFileWatcherJob depends only on this interface, never on a concrete access mode, so
 * switching flexcube.access-mode between "sftp" and "local" (see FlexcubeSftpClient and
 * FlexcubeLocalFileStore, selected via @ConditionalOnProperty) requires no change to the polling
 * job itself.
 */
public interface FlexcubeFileStore {

    <T> T withSession(FlexcubeFileWork<T> work);

    interface FlexcubeFileWork<T> {
        T run(FlexcubeFileSession session) throws IOException;
    }
}
