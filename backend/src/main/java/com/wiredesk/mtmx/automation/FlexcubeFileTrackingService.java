package com.wiredesk.mtmx.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wiredesk.mtmx.config.FlexcubeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent, filename-keyed record of which source files have already been seen failing or
 * succeeding - so FlexcubeFileWatcherJob doesn't re-count the SAME file as a fresh "received"/
 * "failed" on every single cycle just because the v2 failure policy (2026-09-19) leaves it in
 * place in the source folder for retry (user-requested 2026-09-19: "i don't want to recount same
 * file in each cycle"). Backed by two flat JSON files, not a database - this integration has no
 * other persistent store, and a handful of small JSON reads/writes per cycle is cheap - so the
 * record survives a backend restart, unlike FlexcubeStatsService's own in-memory-only counters.
 *
 * <p>"Move it to successful.json" (the user's own words) is implemented literally: a file that
 * transitions from a tracked failure to a success is REMOVED from failure.json and ADDED to
 * successful.json in the same operation - see recordSuccess(). See FlexcubeFileWatcherJob's own
 * v3 FAILURE POLICY note for exactly how the two services (this one and FlexcubeStatsService)
 * divide responsibility: this one is the durable "have I already counted this filename" ledger;
 * FlexcubeStatsService remains the live, in-memory dashboard view.
 */
@Component
public class FlexcubeFileTrackingService {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeFileTrackingService.class);

    public enum LastStatus { NEVER_SEEN, FAILED, SUCCEEDED }

    public record FailureRecord(String fileName, String mtType, String reason, Instant firstFailedAt, Instant lastAttemptAt) {
    }

    public record SuccessRecord(String fileName, String mtType, Instant convertedAt) {
    }

    private final FlexcubeProperties props;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // Loaded lazily (on first use) rather than at construction, since flexcube.state-dir is only
    // meaningful once the integration is actually enabled/configured - avoids touching the
    // filesystem at all for a deployment that never turns this feature on.
    private Map<String, FailureRecord> failures;
    private Map<String, SuccessRecord> successes;

    public FlexcubeFileTrackingService(FlexcubeProperties props) {
        this.props = props;
    }

    public synchronized LastStatus lastStatus(String fileName) {
        ensureLoaded();
        if (successes.containsKey(fileName)) {
            return LastStatus.SUCCEEDED;
        }
        if (failures.containsKey(fileName)) {
            return LastStatus.FAILED;
        }
        return LastStatus.NEVER_SEEN;
    }

    public synchronized void recordFailure(String fileName, String mtType, String reason) {
        ensureLoaded();
        Instant now = Instant.now();
        FailureRecord existing = failures.get(fileName);
        failures.put(fileName, new FailureRecord(fileName, mtType, reason,
                existing != null ? existing.firstFailedAt() : now, now));
        persist();
    }

    /** Adds to successful.json and removes from failure.json in one step - the "move" the user asked for. */
    public synchronized void recordSuccess(String fileName, String mtType) {
        ensureLoaded();
        failures.remove(fileName);
        successes.put(fileName, new SuccessRecord(fileName, mtType, Instant.now()));
        persist();
    }

    private void ensureLoaded() {
        if (failures != null && successes != null) {
            return;
        }
        failures = readMap(failureFile(), FailureRecord.class);
        successes = readMap(successFile(), SuccessRecord.class);
    }

    private <T> Map<String, T> readMap(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            var javaType = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, type);
            Map<String, T> loaded = mapper.readValue(file.toFile(), javaType);
            return loaded != null ? loaded : new LinkedHashMap<>();
        } catch (IOException e) {
            // Starting empty rather than failing the whole job - the ON-DISK file itself is left
            // untouched (not overwritten) until the next successful write, so a merely-corrupt
            // file's content isn't destroyed by this, only not loaded into memory this run.
            log.warn("Could not read {} - starting with an empty record for this run: {}", file, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void persist() {
        writeMap(failureFile(), failures);
        writeMap(successFile(), successes);
    }

    private void writeMap(Path file, Map<String, ?> map) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), map);
        } catch (IOException e) {
            log.error("Could not write {} - this cycle's dedup record was NOT persisted, so a stuck file may be "
                    + "re-counted once more next cycle: {}", file, e.getMessage(), e);
        }
    }

    private Path failureFile() {
        return Path.of(props.getStateDir(), "failure.json");
    }

    private Path successFile() {
        return Path.of(props.getStateDir(), "successful.json");
    }
}
