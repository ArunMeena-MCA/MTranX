package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeSftpProperties;
import com.wiredesk.mtmx.exception.MtmxException;
import com.wiredesk.mtmx.orchestrate.ConversionOrchestrator;
import com.wiredesk.mtmx.orchestrate.ConversionResult;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the OBPS/FLEXCUBE MT_out folder every {@code flexcube.poll-interval-ms} (default 5
 * minutes) via SFTP, translates each MT message found there to pacs.008 through the SAME
 * ConversionOrchestrator pipeline every other entry point uses, and on success moves the source
 * file to MT_history and writes the MX output to MX_out; on failure, moves the source file to
 * MT_failed alongside a small error-detail file rather than leaving it in MT_out to be retried
 * forever (2026-09-16, user-specified failure policy - stated as this implementation's default;
 * revisit if a different policy is wanted).
 *
 * <p>SCOPE (2026-09-16, stated default, not yet confirmed against the actual OBPS environment):
 * MT103 only, matching this converter's current, fully-tested scope - a file whose block 2 is not
 * ":I103:" is moved to MT_failed with an explicit "unsupported message type" error rather than
 * guessed at or silently skipped forever.
 *
 * <p>UNVERIFIED assumption, disclosed rather than silently relied on (same posture as
 * OracleMessageFetchService's own MESSAGE-column assumption): each file in MT_out is assumed to
 * contain one complete raw SWIFT message including its block 1/2/3/4 envelope (the same shape
 * MtParserService.parse() expects everywhere else in this engine) - not just the block 4 body. If
 * that is wrong, every file will fail with a clear parsing error routed to MT_failed rather than
 * silently mis-parsing, but the assumption itself needs confirming against a real sample file.
 */
@Component
public class FlexcubeFileWatcherJob {

    private static final Logger log = LoggerFactory.getLogger(FlexcubeFileWatcherJob.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    private final FlexcubeSftpProperties props;
    private final FlexcubeSftpClient sftpClient;
    private final ConversionOrchestrator orchestrator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FlexcubeFileWatcherJob(FlexcubeSftpProperties props, FlexcubeSftpClient sftpClient,
                                   ConversionOrchestrator orchestrator) {
        this.props = props;
        this.sftpClient = sftpClient;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${flexcube.poll-interval-ms:300000}")
    public void poll() {
        if (!props.isEnabled()) {
            return;
        }
        if (!props.isConfigured()) {
            log.warn("flexcube.enabled=true but the integration is not fully configured (host/username/"
                    + "private-key-path/remote-base-path) - set FLEXCUBE_* in backend/.env. Skipping this cycle.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous FLEXCUBE poll cycle is still running (took longer than the {} ms interval) - "
                    + "skipping this cycle rather than running two overlapping cycles.", props.getPollIntervalMs());
            return;
        }
        try {
            runCycle();
        } catch (MtmxException e) {
            // Connection/list/move-level failure (see FlexcubeIntegrationException) - the whole
            // cycle failed before or between per-file processing, not a single message's
            // translation failure (those are caught per-file inside runCycle and never reach here).
            log.error("FLEXCUBE poll cycle failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    private void runCycle() {
        sftpClient.withConnection(sftp -> {
            sftpClient.ensureDirectory(sftp, props.mtHistoryPath());
            sftpClient.ensureDirectory(sftp, props.mxOutPath());
            sftpClient.ensureDirectory(sftp, props.mtFailedPath());

            List<FlexcubeSftpClient.RemoteFileEntry> files = sftpClient.listFiles(sftp, props.mtOutPath());
            Instant cutoff = Instant.now().minusMillis(props.getMinFileAgeMs());
            int processed = 0;
            int failed = 0;
            int skippedAsFresh = 0;

            for (FlexcubeSftpClient.RemoteFileEntry file : files) {
                if (file.mtime().isAfter(cutoff)) {
                    // Too recently modified - FLEXCUBE may still be writing it. Leave it for the
                    // next cycle rather than risk reading a partial file.
                    skippedAsFresh++;
                    continue;
                }
                if (processOneFile(sftp, file)) {
                    processed++;
                } else {
                    failed++;
                }
            }

            if (processed > 0 || failed > 0 || skippedAsFresh > 0) {
                log.info("FLEXCUBE poll cycle: {} processed, {} failed, {} skipped as too-recently-modified "
                        + "(of {} total files seen in {})", processed, failed, skippedAsFresh, files.size(), props.mtOutPath());
            }
            return null;
        });
    }

    /** @return true if the file was successfully translated and moved to MT_history; false if it was routed to MT_failed. */
    private boolean processOneFile(SFTPClient sftp, FlexcubeSftpClient.RemoteFileEntry file) {
        String rawText;
        try {
            rawText = sftpClient.readFile(sftp, file.path());
        } catch (IOException e) {
            log.error("Failed to read {} from MT_out - leaving it in place for the next cycle: {}",
                    file.name(), e.getMessage(), e);
            return false;
        }

        ConversionResult result;
        try {
            if (!rawText.contains(":I103:")) {
                throw new MtmxException("Unsupported message type - this integration currently handles MT103 "
                        + "only (block 2 does not contain ':I103:'). See FlexcubeFileWatcherJob's own SCOPE note.");
            }
            result = orchestrator.convert(rawText, "MT103", "pacs.008.001.08");
        } catch (MtmxException e) {
            // A genuine translation failure (bad/incomplete source message, validation failure,
            // unsupported message type, etc.) - route to MT_failed per the stated failure policy.
            handleFailure(sftp, file, e);
            return false;
        } catch (RuntimeException e) {
            // Unexpected bug, not a known translation-failure category - still routed to
            // MT_failed (the same input would fail identically every retry, so leaving it in
            // MT_out to loop forever is worse than surfacing it for manual attention).
            log.error("Unexpected error translating {} - routing to MT_failed: {}", file.name(), e.getMessage(), e);
            handleFailure(sftp, file, e);
            return false;
        }

        // Translation succeeded - writing the MX output and moving the source file are SFTP I/O,
        // which can fail independently of translation. That failure must NOT route the file to
        // MT_failed (the message translated fine) - leave it in MT_out and retry next cycle
        // instead, since re-translating is harmless and the problem is almost certainly transient
        // connectivity, not the message content.
        try {
            String mxOutput = result.getEnvelopeOutput() != null ? result.getEnvelopeOutput() : result.getRenderedOutput();
            String mxFileName = stripExtension(file.name()) + ".xml";
            sftpClient.writeFile(sftp, props.mxOutPath() + "/" + mxFileName, mxOutput);
            sftpClient.moveFile(sftp, file.path(), props.mtHistoryPath() + "/" + file.name());
            log.info("Translated {} -> {}", file.name(), mxFileName);
            return true;
        } catch (IOException e) {
            log.error("Translation of {} succeeded but writing MX_out/moving to MT_history failed - leaving "
                    + "the source file in MT_out for retry next cycle: {}", file.name(), e.getMessage(), e);
            return false;
        }
    }

    private void handleFailure(SFTPClient sftp, FlexcubeSftpClient.RemoteFileEntry file, Exception e) {
        log.error("Failed to translate {}: {}", file.name(), e.getMessage(), e);
        try {
            sftpClient.moveFile(sftp, file.path(), props.mtFailedPath() + "/" + file.name());
            String errorDetail = "Failed at " + TS.format(Instant.now()) + "\nFile: " + file.name() + "\n\n"
                    + e.getClass().getSimpleName() + ": " + e.getMessage();
            sftpClient.writeFile(sftp, props.mtFailedPath() + "/" + stripExtension(file.name()) + ".error.txt",
                    errorDetail);
        } catch (IOException moveError) {
            // The file could not even be moved to MT_failed - it stays in MT_out and will be
            // re-attempted (and fail again) next cycle. Logged loudly since this needs manual
            // attention (likely an SFTP permissions or connectivity issue on MT_failed itself).
            log.error("ALSO failed to move {} to MT_failed after its translation failure - it remains in "
                    + "MT_out and will be retried next cycle: {}", file.name(), moveError.getMessage(), moveError);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
