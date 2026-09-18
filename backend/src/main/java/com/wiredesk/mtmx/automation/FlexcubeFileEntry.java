package com.wiredesk.mtmx.automation;

import java.time.Instant;

/**
 * One file seen in a FLEXCUBE folder (MT_out, MT_history, etc.) - mode-agnostic: {@code path} is
 * whatever opaque, fully-resolved identifier the active FlexcubeFileStore needs to read/move it
 * again later (an SFTP remote path when flexcube.access-mode=sftp, a local filesystem path when
 * access-mode=local). The polling job never constructs or parses this string itself - see
 * FlexcubeFileSession.resolvePath().
 */
public record FlexcubeFileEntry(String name, String path, Instant mtime) {
}
