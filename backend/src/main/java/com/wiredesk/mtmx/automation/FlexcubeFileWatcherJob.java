package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeProperties;
import com.wiredesk.mtmx.exception.MtmxException;
import com.wiredesk.mtmx.mapping.MappingRegistry;
import com.wiredesk.mtmx.orchestrate.ConversionOrchestrator;
import com.wiredesk.mtmx.orchestrate.ConversionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls the OBPS/FLEXCUBE source folder (FlexcubeProperties.mtOutFolder, "MT_out" by default -
 * see that field's own Javadoc for why the name is configurable, not assumed) every
 * {@code flexcube.poll-interval-ms} (default 5 minutes), translates each MT message found there
 * to its ISO 20022 equivalent through the SAME ConversionOrchestrator pipeline every other entry
 * point uses, and on success moves the source file to mtHistoryFolder and writes the MX output to
 * mxOutFolder.
 *
 * <p>FAILURE POLICY v2 (2026-09-19, user-requested change - supersedes the original 2026-09-16
 * policy): a file that fails translation, or whose MT type has no supported mapping doc, is now
 * LEFT IN PLACE in the source folder rather than moved to mtFailedFolder - it is re-attempted
 * every subsequent cycle. This means a file with a permanent problem (no mapping doc will ever
 * appear on its own; a structurally invalid message will never become valid) stays in the source
 * folder and gets re-flagged as failed and re-counted as "received" FOREVER, with no built-in way
 * to distinguish "stuck forever" from "will succeed once whatever's transient resolves" - an
 * accepted trade-off in exchange for a simple, always-visible "still pending" queue instead of
 * files disappearing into mtFailedFolder. mtFailedFolder itself is still created each cycle
 * (harmless) but nothing writes into it anymore; see processOneFile's own per-branch notes.
 *
 * <p>THROTTLING (2026-09-19, user-requested: "so that LLM does not get overloaded"): a
 * flexcube.conversion-delay-ms pause (default 5s) is inserted after every file that actually
 * reaches ConversionOrchestrator.convert() (success OR failure - either way an LLM call happened),
 * never after a file skipped for being too fresh or having no supported mapping doc (neither
 * calls the LLM, so waiting would only slow the cycle down for no reason). See FileOutcome and
 * FlexcubeProperties.conversionDelayMs.
 *
 * <p>MULTIPLE MT TYPES (2026-09-17, user-reported: the real source folder mixes MT103 with other
 * MT types): the mapping docs actually present under mtmx.mappings-dir determine what's
 * supported, discovered fresh each cycle via MappingRegistry (matching the same
 * "&lt;SOURCE&gt;_TO_&lt;TARGET&gt;.yaml" discovery every other entry point relies on) rather than
 * hardcoding "MT103 only". A file whose block 2 message type has no matching mapping doc is
 * DELIBERATELY NOT sent through the converter at all (no wasted parse/LLM-audit work for a type
 * we already know cannot succeed) - see FAILURE POLICY v2 above for what happens to it now.
 *
 * <p>DASHBOARD (2026-09-17): every stage below reports through FlexcubeStatsService - see that
 * class's own Javadoc. FlexcubeDashboardController exposes the resulting snapshot plus
 * pause/resume/stop/run-now controls; this job only needs to check stats.paused()/
 * isStopRequested() at the right points, it owns none of that control-plane state itself.
 *
 * <p>UNVERIFIED assumption, disclosed rather than silently relied on (same posture as
 * OracleMessageFetchService's own MESSAGE-column assumption): each file in the source folder is
 * assumed to contain one complete raw SWIFT message including its block 1/2/3/4 envelope (the
 * same shape MtParserService.parse() expects everywhere else in this engine) - not just the block
 * 4 body. If that is wrong, every file will fail with a clear parsing error and (per FAILURE
 * POLICY v2) stay in the source folder rather than silently mis-parsing, but the assumption itself
 * needs confirming against a real sample file.
 */
@Component
public class FlexcubeFileWatcherJob {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeFileWatcherJob.class);
    private static final Pattern MT_TYPE_PATTERN = Pattern.compile("\\{2:[IO](\\d{3})");
    private static final Pattern MAPPING_FILENAME_PATTERN = Pattern.compile("^MT(\\d{3})_TO_.+\\.yaml$", Pattern.CASE_INSENSITIVE);

    private final FlexcubeProperties props;
    private final FlexcubeFileStore fileStore;
    private final ConversionOrchestrator orchestrator;
    private final MappingRegistry registry;
    private final FlexcubeStatsService stats;
    private final FlexcubeFileTrackingService tracking;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FlexcubeFileWatcherJob(FlexcubeProperties props, FlexcubeFileStore fileStore,
                                   ConversionOrchestrator orchestrator, MappingRegistry registry,
                                   FlexcubeStatsService stats, FlexcubeFileTrackingService tracking) {
        this.props = props;
        this.fileStore = fileStore;
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.stats = stats;
        this.tracking = tracking;
    }

    @Scheduled(fixedDelayString = "${flexcube.poll-interval-ms:300000}")
    public void poll() {
        if (!props.isEnabled() || stats.paused()) {
            return;
        }
        runIfNotAlreadyRunning();
    }

    /**
     * Runs one cycle immediately, independent of the scheduled timer - the dashboard's "Run now"
     * control. Deliberately bypasses props.isEnabled() and stats.paused() (an explicit manual
     * action should not be blocked by either), but NOT props.isConfigured() - there is nothing to
     * run without valid config regardless of how it was triggered. Runs on its own thread so the
     * calling HTTP request returns immediately; the dashboard observes progress by polling
     * /api/flexcube/status, the same way it observes a normal scheduled cycle.
     */
    public void runNow() {
        if (!props.isConfigured()) {
            stats.logEvent("error", "Run now requested, but the integration is not configured (base-path/"
                    + "access-mode credentials missing) - nothing to run.");
            return;
        }
        new Thread(this::runIfNotAlreadyRunning, "flexcube-run-now").start();
    }

    private void runIfNotAlreadyRunning() {
        if (!props.isConfigured()) {
            log.warn("flexcube.enabled=true but the integration is not fully configured for "
                    + "access-mode={} - set the required FLEXCUBE_* variables in backend/.env "
                    + "(base-path always required; host/username/private-key-path/password only "
                    + "required for access-mode=sftp). Skipping this cycle.", props.getAccessMode());
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("A FLEXCUBE cycle is already running - skipping this trigger rather than running two "
                    + "overlapping cycles.");
            return;
        }
        stats.cycleStarting();
        try {
            runCycle();
        } catch (MtmxException e) {
            // Connection/list/move-level failure (see FlexcubeIntegrationException) - the whole
            // cycle failed before or between per-file processing, not a single message's
            // translation failure (those are caught per-file inside runCycle and never reach here).
            log.error("FLEXCUBE poll cycle failed: {}", e.getMessage(), e);
            stats.logEvent("error", "Cycle failed: " + e.getMessage());
        } finally {
            stats.cycleFinished();
            running.set(false);
        }
    }

    private void runCycle() {
        String mtOut = props.getMtOutFolder();
        String mtHistory = props.getMtHistoryFolder();
        String mxOut = props.getMxOutFolder();
        String mtFailed = props.getMtFailedFolder();
        Map<String, String> supportedTypes = discoverSupportedMtTypes();

        fileStore.withSession(session -> {
            session.ensureDirectory(mtHistory);
            session.ensureDirectory(mxOut);
            // Still created even though nothing writes to it anymore (2026-09-19 policy change -
            // see processOneFile's own notes: failed/unsupported files now stay in the source
            // folder for retry instead of being moved here) - kept available in case this policy
            // is reverted later, or an operator wants to manually triage a file into it themselves.
            session.ensureDirectory(mtFailed);

            List<FlexcubeFileEntry> files = session.listFiles(mtOut);
            if (files.isEmpty()) {
                // 2026-09-19, real production case: a cycle that lists 0 files previously logged
                // NOTHING at all (the summary below is gated on processed/failed/skippedAsFresh all
                // being 0), which is indistinguishable in the logs from the cycle never having run -
                // exactly what was reported as "connects, authenticates, disconnects... then
                // silence, not fetching files". Logging the EXACT resolved remote path checked
                // turns "is anything even being looked at?" into a fact instead of a guess - if
                // this path doesn't match where the real .txt files actually sit on the FLEXCUBE
                // server, that mismatch (not a connection problem) is the real cause.
                log.info("FLEXCUBE poll cycle: 0 files found in '{}' (resolved path: {}) - nothing to "
                        + "convert this cycle.", mtOut, session.resolvePath(mtOut, ""));
            }
            Instant cutoff = Instant.now().minusMillis(props.getMinFileAgeMs());
            int processed = 0;
            int failed = 0;
            int skippedAsFresh = 0;
            int stopped = 0;

            for (FlexcubeFileEntry file : files) {
                if (stats.isStopRequested()) {
                    stopped = files.size() - processed - failed - skippedAsFresh;
                    stats.logEvent("info", "Cycle stopped by user request - " + stopped + " file(s) left for next cycle.");
                    break;
                }
                if (file.mtime().isAfter(cutoff)) {
                    // Too recently modified - FLEXCUBE may still be writing it. Leave it for the
                    // next cycle rather than risk reading a partial file.
                    skippedAsFresh++;
                    continue;
                }
                stats.setCurrentFile(file.name());
                FileOutcome outcome = processOneFile(session, file, mtHistory, mxOut, supportedTypes);
                if (outcome == FileOutcome.CONVERTED) {
                    processed++;
                } else {
                    failed++;
                }
                // Only pause after a file that actually reached the converter (and therefore made
                // an LLM call) - never after an UNSUPPORTED_TYPE/READ_FAILURE skip, which made none
                // and gains nothing from waiting. See FlexcubeProperties.conversionDelayMs's own
                // Javadoc for why this exists.
                if (outcome.calledLlm() && props.getConversionDelayMs() > 0 && !stats.isStopRequested()) {
                    try {
                        Thread.sleep(props.getConversionDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        stats.logEvent("info", "Cycle interrupted during the inter-conversion pause - stopping early.");
                        break;
                    }
                }
            }
            stats.setCurrentFile(null);

            if (processed > 0 || failed > 0 || skippedAsFresh > 0) {
                String summary = String.format("Cycle complete: %d converted, %d failed, %d skipped (too recent) of %d files",
                        processed, failed, skippedAsFresh, files.size());
                log.info("FLEXCUBE poll cycle: {} processed, {} failed, {} skipped as too-recently-modified "
                        + "(of {} total files seen in {})", processed, failed, skippedAsFresh, files.size(), mtOut);
                stats.logEvent("success", summary);
            }
            return null;
        });
    }

    /**
     * Scans mtmx.mappings-dir (via MappingRegistry, same discovery every other entry point uses)
     * for filenames matching "MT&lt;3 digits&gt;_TO_&lt;anything&gt;.yaml" and reads each one's
     * own declared target_format - done fresh every cycle (cheap: a handful of small YAML reads)
     * so a newly-added mapping doc is picked up without restarting this job.
     */
    private Map<String, String> discoverSupportedMtTypes() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String filename : registry.listAvailable()) {
            Matcher m = MAPPING_FILENAME_PATTERN.matcher(filename);
            if (m.matches()) {
                try {
                    Object target = registry.loadRawByFilename(filename).get("target_format");
                    if (target != null) {
                        result.put(m.group(1), target.toString());
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not read target_format from mapping doc {} while discovering supported MT "
                            + "types - excluding it from this cycle's supported set: {}", filename, e.getMessage());
                }
            }
        }
        return result;
    }

    /** Whether a file's processing ever reached ConversionOrchestrator.convert() (and therefore made an LLM call) - see FlexcubeProperties.conversionDelayMs's own Javadoc for why this distinction matters. */
    private enum FileOutcome {
        CONVERTED(true), CONVERSION_FAILED(true), POST_SUCCESS_IO_FAILURE(true),
        UNSUPPORTED_TYPE(false), READ_FAILURE(false);

        private final boolean calledLlm;

        FileOutcome(boolean calledLlm) {
            this.calledLlm = calledLlm;
        }

        boolean calledLlm() {
            return calledLlm;
        }
    }

    private FileOutcome processOneFile(FlexcubeFileSession session, FlexcubeFileEntry file, String mtHistory,
                                        String mxOut, Map<String, String> supportedTypes) {
        String rawText;
        try {
            rawText = session.readFile(file.path());
        } catch (IOException e) {
            log.error("Failed to read {} from the source folder - leaving it in place for the next cycle: {}",
                    file.name(), e.getMessage(), e);
            return FileOutcome.READ_FAILURE;
        }

        Matcher typeMatcher = MT_TYPE_PATTERN.matcher(rawText);
        String mtType = typeMatcher.find() ? typeMatcher.group(1) : "UNKNOWN";
        String targetFormat = supportedTypes.get(mtType);

        // Dedup against the persistent ledger (2026-09-19, user-requested "don't recount same file
        // in each cycle") - a file already known to be failing (or, in the unusual case, already
        // marked successful under this exact name) was already counted as "received" the first
        // time it was seen, so counting it again on every retry would inflate the dashboard's
        // per-type totals for what is really the SAME message sitting in the source folder. Only a
        // genuinely new filename increments "received".
        FlexcubeFileTrackingService.LastStatus previousStatus = tracking.lastStatus(file.name());
        boolean alreadyCounted = previousStatus != FlexcubeFileTrackingService.LastStatus.NEVER_SEEN;
        if (!alreadyCounted) {
            stats.recordReceived(mtType, targetFormat != null);
        } else {
            // Still register the type's supported/unsupported flag even when the counter itself is
            // skipped - otherwise a type whose every occurrence happens to already be in the
            // tracking ledger would never appear as "supported" on the dashboard. See
            // FlexcubeStatsService.noteType()'s own Javadoc.
            stats.noteType(mtType, targetFormat != null);
        }

        if (targetFormat == null) {
            // A real, mapped-but-unsupported MT type (or block 2 didn't match at all) - not sent
            // through the converter at all, per this class's own MULTIPLE MT TYPES note.
            // v2 FAILURE POLICY (2026-09-19, user-requested change): LEFT IN PLACE, not moved to
            // mtFailedFolder - it will be re-attempted (and re-flagged UNSUPPORTED_TYPE, re-counted
            // as "received") every subsequent cycle. There is no possible fix that makes this
            // succeed later on its own (no mapping doc will appear without someone adding one), so
            // this file sits here indefinitely unless a mapping doc is added or it's removed
            // manually - a deliberate trade-off the user chose (a visible, always-in-place pending
            // queue) over the previous "move it out of the way permanently" policy.
            String reason = "UNKNOWN".equals(mtType)
                    ? "Could not identify an MT message type in block 2 (no '{2:I<digits>' found)."
                    : "No mapping document is configured for MT" + mtType + " (looked for MT" + mtType + "_TO_*.yaml "
                            + "under " + "the mappings folder).";
            log.warn("{} - {} - leaving it in the source folder (will be retried next cycle).", file.name(), reason);
            tracking.recordFailure(file.name(), mtType, reason);
            if (!alreadyCounted) {
                stats.recordFailed(mtType, file.name(), FlexcubeStatsService.FailureReason.UNSUPPORTED_TYPE, reason);
            }
            return FileOutcome.UNSUPPORTED_TYPE;
        }

        ConversionResult result;
        try {
            result = orchestrator.convert(rawText, "MT" + mtType, targetFormat);
        } catch (MtmxException e) {
            // A genuine translation failure (bad/incomplete source message, validation failure,
            // etc.) - v2 FAILURE POLICY (2026-09-19): LEFT IN PLACE, not moved to mtFailedFolder -
            // see the UNSUPPORTED_TYPE branch above for the same reasoning/trade-off. Deliberately
            // does NOT write an error-detail file into the source folder either (unlike the old
            // moveToFailed() behavior) - a stray ".error.txt" sitting in the SAME folder this job
            // scans every cycle would itself be picked up next cycle as an unrecognized "UNKNOWN"
            // type and misreported as another failed message.
            log.error("Failed to translate {}: {} - leaving it in the source folder (will be retried next cycle).",
                    file.name(), e.getMessage(), e);
            tracking.recordFailure(file.name(), mtType, e.getMessage());
            if (!alreadyCounted) {
                stats.recordFailed(mtType, file.name(), FlexcubeStatsService.FailureReason.CONVERSION_ERROR, e.getMessage());
            }
            return FileOutcome.CONVERSION_FAILED;
        } catch (RuntimeException e) {
            // Unexpected bug, not a known translation-failure category - same v2 policy: left in
            // place rather than moved, since the user wants every failure retried, not just
            // transient ones. This specific category IS likely to fail identically every retry
            // (it's not a transient error), but that is now an accepted consequence of the chosen
            // policy, not something this method decides on a per-category basis anymore.
            log.error("Unexpected error translating {}: {} - leaving it in the source folder (will be retried next cycle).",
                    file.name(), e.getMessage(), e);
            tracking.recordFailure(file.name(), mtType, e.getMessage());
            if (!alreadyCounted) {
                stats.recordFailed(mtType, file.name(), FlexcubeStatsService.FailureReason.CONVERSION_ERROR, e.getMessage());
            }
            return FileOutcome.CONVERSION_FAILED;
        }

        // Translation succeeded - writing the MX output and moving the source file are I/O, which
        // can fail independently of translation. That failure must NOT route the file to
        // mtFailedFolder (the message translated fine) - leave it in the source folder and retry
        // next cycle instead, since re-translating is harmless and the problem is almost certainly
        // transient, not the message content.
        try {
            String mxOutput = result.getEnvelopeOutput() != null ? result.getEnvelopeOutput() : result.getRenderedOutput();
            String mxFileName = stripExtension(file.name()) + ".xml";
            session.writeFile(session.resolvePath(mxOut, mxFileName), mxOutput);
            session.moveFile(file.path(), session.resolvePath(mtHistory, file.name()));
            log.info("Translated {} -> {}", file.name(), mxFileName);
            // A file that previously failed (present in failure.json) and now converts is a
            // RECOVERY, not a fresh arrival - recordRecovered() moves it out of "failed" and into
            // "converted" without double-counting "received" (already counted the first time this
            // filename failed). A first-time success still goes through the normal
            // recordConverted() path. Either way, tracking.recordSuccess() performs the user's own
            // "move it to successful.json" request (removes from failure.json, adds to
            // successful.json) - see FlexcubeFileTrackingService.
            if (previousStatus == FlexcubeFileTrackingService.LastStatus.FAILED) {
                stats.recordRecovered(mtType, file.name());
            } else {
                stats.recordConverted(mtType);
            }
            tracking.recordSuccess(file.name(), mtType);
            stats.recordSuccess(mtType, file.name(), rawText, mxOutput);
            stats.logEvent("success", "Converted " + file.name() + " (MT" + mtType + ") -> " + mxFileName);
            return FileOutcome.CONVERTED;
        } catch (IOException e) {
            log.error("Translation of {} succeeded but writing MX output/moving to history failed - leaving "
                    + "the source file in place for retry next cycle: {}", file.name(), e.getMessage(), e);
            stats.logEvent("error", "Translated " + file.name() + " but could not write output - will retry: " + e.getMessage());
            return FileOutcome.POST_SUCCESS_IO_FAILURE;
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
