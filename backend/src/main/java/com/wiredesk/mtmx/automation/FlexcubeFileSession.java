package com.wiredesk.mtmx.automation;

import java.io.IOException;
import java.util.List;

/**
 * One open session's worth of file operations against a FLEXCUBE integration folder tree
 * (MT_out/MT_history/MX_out/MT_failed under one base path), for either access mode -
 * FlexcubeSftpClient's SFTP-backed session or FlexcubeLocalFileStore's local-filesystem-backed
 * session. All paths passed in/out are logical subfolder names ("MT_out" etc.) or opaque
 * already-resolved path strings from {@link #resolvePath} / a {@link FlexcubeFileEntry#path()} -
 * callers never build a path by hand (SFTP always joins with "/" regardless of the remote OS;
 * local mode must use the OS-native separator - this interface exists specifically so
 * FlexcubeFileWatcherJob never has to know or care which).
 */
public interface FlexcubeFileSession {

    /** Builds the full, store-appropriate path for a file with this name inside this logical subfolder. */
    String resolvePath(String subfolder, String fileName);

    /** Creates the subfolder if it does not already exist. No-op (not an error) if it's already there. */
    void ensureDirectory(String subfolder) throws IOException;

    /** Lists the regular files directly inside this logical subfolder (no recursion). */
    List<FlexcubeFileEntry> listFiles(String subfolder) throws IOException;

    /** Reads a file's full text content (UTF-8), by an opaque path from listFiles/resolvePath. */
    String readFile(String path) throws IOException;

    /** Writes (creating or overwriting) a file's full text content (UTF-8), by an opaque path from resolvePath. */
    void writeFile(String path, String content) throws IOException;

    /** Moves/renames a file - both paths must already be under the same base path/session. */
    void moveFile(String fromPath, String toPath) throws IOException;
}
