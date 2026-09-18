package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeProperties;
import com.wiredesk.mtmx.exception.FlexcubeIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * FlexcubeFileStore implementation for flexcube.access-mode=local (2026-09-17) - used when this
 * backend runs directly on the same app server that holds the MT_out folder, so the whole
 * exchange is a plain local filesystem path rather than a network protocol. No connection to
 * open/close (withSession's "session" is stateless), and paths are joined with
 * {@link Path#resolve} so the OS-native separator is always used - unlike FlexcubeSftpClient,
 * which must always join with "/" per the SFTP protocol regardless of the local OS.
 */
@Component
@ConditionalOnProperty(prefix = "flexcube", name = "access-mode", havingValue = "local")
public class FlexcubeLocalFileStore implements FlexcubeFileStore {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeLocalFileStore.class);

    private final FlexcubeProperties props;

    public FlexcubeLocalFileStore(FlexcubeProperties props) {
        this.props = props;
    }

    @Override
    public <T> T withSession(FlexcubeFileWork<T> work) {
        try {
            return work.run(new LocalSession());
        } catch (IOException e) {
            throw new FlexcubeIntegrationException(
                    "Local FLEXCUBE file operation failed under " + props.getBasePath() + " - " + e.getMessage(), e);
        }
    }

    private class LocalSession implements FlexcubeFileSession {

        @Override
        public String resolvePath(String subfolder, String fileName) {
            return Path.of(props.getBasePath(), subfolder, fileName).toString();
        }

        @Override
        public void ensureDirectory(String subfolder) throws IOException {
            Path dir = Path.of(props.getBasePath(), subfolder);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                log.info("Created local directory {}", dir);
            }
        }

        @Override
        public List<FlexcubeFileEntry> listFiles(String subfolder) throws IOException {
            Path dir = Path.of(props.getBasePath(), subfolder);
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            List<FlexcubeFileEntry> entries = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    if (Files.isRegularFile(p)) {
                        Instant mtime = Files.getLastModifiedTime(p).toInstant();
                        entries.add(new FlexcubeFileEntry(p.getFileName().toString(), p.toString(), mtime));
                    }
                }
            }
            return entries;
        }

        @Override
        public String readFile(String path) throws IOException {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        }

        @Override
        public void writeFile(String path, String content) throws IOException {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
        }

        @Override
        public void moveFile(String fromPath, String toPath) throws IOException {
            Files.move(Path.of(fromPath), Path.of(toPath));
        }
    }
}
