package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.config.FlexcubeProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory, thread-safe state for the FLEXCUBE dashboard (frontend/src/pages/FlexcubeDashboard) -
 * per-MT-type counters, the currently-running cycle's live state (for the "in progress" animation
 * and next-cycle countdown), a bounded recent-events log, and a bounded recent-failures list (the
 * "see the list of failed ones" the user asked for). Read by FlexcubeDashboardController, written
 * by FlexcubeFileWatcherJob.
 *
 * <p>Deliberately in-memory only, matching this engine's existing "stateless-ish" posture
 * elsewhere (see AutomatedConversionController's own Javadoc) - restarting the backend resets all
 * counters and history. Acceptable for a live-operations dashboard (it is not an audit log/system
 * of record - the actual moved/archived files under MT_history/MX_out/MT_failed remain the real,
 * durable record); revisit with real persistence if these counts need to survive a restart.
 */
@Component
public class FlexcubeStatsService {

    public enum JobState { IDLE, RUNNING, PAUSED }

    public enum FailureReason { UNSUPPORTED_TYPE, CONVERSION_ERROR, IO_ERROR }

    private static final int MAX_EVENTS = 200;
    private static final int MAX_FAILURES = 500;
    /**
     * Cap on how many successful conversions' full MT+MX text is kept in memory (2026-09-17,
     * user-requested "show the list of successful conversions... show the panel of MT and MX
     * both for each"). Deliberately smaller than MAX_FAILURES since each entry now carries two
     * full message bodies rather than a short error string - 1000 recent conversions is enough
     * to page through (at 25/page, 40 pages) without unbounded memory growth on a long-running
     * process. Same "in-memory only, not a system of record" posture as the rest of this class.
     */
    private static final int MAX_CONVERSIONS = 1000;

    public record TypeCounters(String mtType, boolean supported, int received, int converted, int failed) {
    }

    public record FailedItem(String fileName, String mtType, FailureReason reason, String detail, Instant at) {
    }

    public record Event(Instant at, String level, String message) {
    }

    /** Lightweight row for the paginated conversions list - no MT/MX content, so listing a page of 25 stays cheap. */
    public record ConversionSummary(long id, String fileName, String mtType, Instant at) {
    }

    /** Full record, including both message bodies - fetched only when a row is clicked, per the user's own "on click show the panel" request. */
    public record ConversionDetail(long id, String fileName, String mtType, Instant at, String mtContent, String mxContent) {
    }

    public record ConversionPage(List<ConversionSummary> items, int page, int size, long total) {
    }

    public record Snapshot(JobState state, String currentFile, Instant lastCycleStartedAt,
                            Instant lastCycleFinishedAt, Instant nextCycleAt, long pollIntervalMs,
                            List<TypeCounters> typeStats, List<FailedItem> recentFailures,
                            List<Event> recentEvents) {
    }

    private static class Counters {
        final AtomicInteger received = new AtomicInteger();
        final AtomicInteger converted = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
    }

    private final FlexcubeProperties props;

    public FlexcubeStatsService(FlexcubeProperties props) {
        this.props = props;
    }

    private final Map<String, Counters> countersByType = new ConcurrentHashMap<>();
    private final Map<String, Boolean> supportedByType = new ConcurrentHashMap<>();
    private final List<FailedItem> failures = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<ConversionDetail> conversions = new ArrayList<>();
    private final AtomicLong nextConversionId = new AtomicLong(1);

    private final AtomicReference<JobState> state = new AtomicReference<>(JobState.IDLE);
    private final AtomicBoolean pausedFlag = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile String currentFile;
    private volatile Instant lastCycleStartedAt;
    private volatile Instant lastCycleFinishedAt;
    private volatile Instant nextCycleAt;

    public void recordReceived(String mtType, boolean supported) {
        noteType(mtType, supported);
        countersFor(mtType).received.incrementAndGet();
    }

    /**
     * Registers a type's supported/unsupported flag for the dashboard's per-type breakdown without
     * touching the received/converted/failed counters (2026-09-19) - needed because the dedup
     * ledger (FlexcubeFileTrackingService) makes FlexcubeFileWatcherJob skip recordReceived()
     * entirely for a file it already counted, which would otherwise leave a type that only ever
     * reappears as a recovery (e.g. every occurrence of it happened to already be in failure.json)
     * permanently mis-shown as "supported: false" even while it is actively converting.
     */
    public void noteType(String mtType, boolean supported) {
        supportedByType.putIfAbsent(mtType, supported);
    }

    public void recordConverted(String mtType) {
        countersFor(mtType).converted.incrementAndGet();
    }

    public void recordFailed(String mtType, String fileName, FailureReason reason, String detail) {
        countersFor(mtType).failed.incrementAndGet();
        addBounded(failures, new FailedItem(fileName, mtType, reason, detail, Instant.now()), MAX_FAILURES);
    }

    /**
     * A file that was PREVIOUSLY counted as failed just succeeded (2026-09-19, user-requested
     * "move it to successful" - see FlexcubeFileTrackingService, which detects this transition).
     * "received" is deliberately NOT incremented again here - it was already counted the first
     * time this filename failed; this call only moves it out of "failed" and into "converted",
     * matching the user's own "move" wording rather than double-counting the same file as a
     * second distinct arrival. Also removes any of this file's OWN entries from the recent-
     * failures list shown on the dashboard, since it is no longer a currently-failing message.
     */
    public void recordRecovered(String mtType, String fileName) {
        Counters c = countersFor(mtType);
        c.failed.updateAndGet(v -> Math.max(0, v - 1));
        c.converted.incrementAndGet();
        synchronized (failures) {
            failures.removeIf(f -> f.fileName().equals(fileName));
        }
    }

    public void recordSuccess(String mtType, String fileName, String mtContent, String mxContent) {
        long id = nextConversionId.getAndIncrement();
        addBounded(conversions, new ConversionDetail(id, fileName, mtType, Instant.now(), mtContent, mxContent), MAX_CONVERSIONS);
    }

    /** Newest first, per the user's own "show the latest conversions first" request. */
    public ConversionPage listConversions(int page, int size) {
        List<ConversionDetail> snapshot;
        synchronized (conversions) {
            snapshot = List.copyOf(conversions);
        }
        int total = snapshot.size();
        List<ConversionSummary> pageItems = new ArrayList<>();
        // conversions is stored oldest-first (append-only); newest-first means walking backward
        // from the end, offset by page*size.
        int startFromEnd = page * size;
        for (int i = total - 1 - startFromEnd; i >= 0 && pageItems.size() < size; i--) {
            ConversionDetail c = snapshot.get(i);
            pageItems.add(new ConversionSummary(c.id(), c.fileName(), c.mtType(), c.at()));
        }
        return new ConversionPage(pageItems, page, size, total);
    }

    public ConversionDetail getConversionDetail(long id) {
        synchronized (conversions) {
            for (int i = conversions.size() - 1; i >= 0; i--) {
                if (conversions.get(i).id() == id) {
                    return conversions.get(i);
                }
            }
        }
        return null;
    }

    public void logEvent(String level, String message) {
        addBounded(events, new Event(Instant.now(), level, message), MAX_EVENTS);
    }

    public void cycleStarting() {
        state.set(JobState.RUNNING);
        lastCycleStartedAt = Instant.now();
        stopRequested.set(false);
    }

    /** Called when a cycle ends for any reason (ran to completion, was stopped, or was skipped because paused). */
    public void cycleFinished() {
        currentFile = null;
        lastCycleFinishedAt = Instant.now();
        boolean paused = pausedFlag.get();
        nextCycleAt = paused ? null : lastCycleFinishedAt.plusMillis(props.getPollIntervalMs());
        state.set(paused ? JobState.PAUSED : JobState.IDLE);
    }

    public void setCurrentFile(String fileName) {
        currentFile = fileName;
    }

    public void pause() {
        pausedFlag.set(true);
        if (state.get() == JobState.IDLE) {
            state.set(JobState.PAUSED);
            nextCycleAt = null;
        }
    }

    public void resume() {
        pausedFlag.set(false);
        if (state.get() == JobState.PAUSED) {
            state.set(JobState.IDLE);
            nextCycleAt = Instant.now().plusMillis(props.getPollIntervalMs());
        }
    }

    public boolean paused() {
        return pausedFlag.get();
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public JobState currentState() {
        return state.get();
    }

    public Snapshot snapshot() {
        List<TypeCounters> typeStats = countersByType.entrySet().stream()
                .map(e -> new TypeCounters(e.getKey(),
                        supportedByType.getOrDefault(e.getKey(), false),
                        e.getValue().received.get(), e.getValue().converted.get(), e.getValue().failed.get()))
                .sorted((a, b) -> a.mtType().compareTo(b.mtType()))
                .toList();
        List<FailedItem> failuresCopy;
        List<Event> eventsCopy;
        synchronized (failures) {
            failuresCopy = List.copyOf(failures);
        }
        synchronized (events) {
            eventsCopy = List.copyOf(events);
        }
        return new Snapshot(state.get(), currentFile, lastCycleStartedAt, lastCycleFinishedAt, nextCycleAt,
                props.getPollIntervalMs(), typeStats, failuresCopy, eventsCopy);
    }

    private Counters countersFor(String mtType) {
        return countersByType.computeIfAbsent(mtType, k -> new Counters());
    }

    private static <T> void addBounded(List<T> list, T item, int max) {
        synchronized (list) {
            list.add(item);
            while (list.size() > max) {
                list.remove(0);
            }
        }
    }
}
