package com.wiredesk.mtmx.web;

import com.wiredesk.mtmx.automation.FlexcubeFileWatcherJob;
import com.wiredesk.mtmx.automation.FlexcubeStatsService;
import com.wiredesk.mtmx.config.FlexcubeProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Read/control surface for the FLEXCUBE dashboard (frontend/src/pages/FlexcubeDashboard.jsx) -
 * one status snapshot endpoint the page polls, plus the pause/resume/stop/run-now controls behind
 * its top-right button group. All state actually lives in FlexcubeStatsService/
 * FlexcubeFileWatcherJob; this controller is a thin pass-through, same "no business logic in the
 * web layer" convention as every other controller in this package.
 */
@RestController
@RequestMapping("/api/flexcube")
public class FlexcubeDashboardController {

    public record DashboardStatus(boolean enabled, boolean configured, String accessMode,
                                    FlexcubeStatsService.Snapshot snapshot) {
    }

    public record ActionResult(boolean ok, String message) {
    }

    private final FlexcubeProperties props;
    private final FlexcubeStatsService stats;
    private final FlexcubeFileWatcherJob job;

    public FlexcubeDashboardController(FlexcubeProperties props, FlexcubeStatsService stats, FlexcubeFileWatcherJob job) {
        this.props = props;
        this.stats = stats;
        this.job = job;
    }

    @GetMapping("/status")
    public DashboardStatus status() {
        return new DashboardStatus(props.isEnabled(), props.isConfigured(), props.getAccessMode(), stats.snapshot());
    }

    @PostMapping("/pause")
    public ActionResult pause() {
        stats.pause();
        stats.logEvent("info", "Paused by user - the automatic cycle will not run again until resumed.");
        return new ActionResult(true, "Paused.");
    }

    @PostMapping("/resume")
    public ActionResult resume() {
        stats.resume();
        stats.logEvent("info", "Resumed by user.");
        return new ActionResult(true, "Resumed.");
    }

    /** Requests the CURRENTLY RUNNING cycle stop early - a no-op (but not an error) if nothing is running. */
    @PostMapping("/stop")
    public ActionResult stop() {
        if (stats.currentState() != FlexcubeStatsService.JobState.RUNNING) {
            return new ActionResult(false, "Nothing is currently running.");
        }
        stats.requestStop();
        stats.logEvent("info", "Stop requested by user - finishing the current file, then halting this cycle.");
        return new ActionResult(true, "Stop requested.");
    }

    /** Triggers an immediate cycle, independent of the timer - see FlexcubeFileWatcherJob.runNow()'s own Javadoc for exactly what it bypasses. */
    @PostMapping("/run-now")
    public ActionResult runNow() {
        if (stats.currentState() == FlexcubeStatsService.JobState.RUNNING) {
            return new ActionResult(false, "A cycle is already running.");
        }
        stats.logEvent("info", "Run now requested by user.");
        job.runNow();
        return new ActionResult(true, "Started.");
    }

    /**
     * Paginated, newest-first list of successful conversions (2026-09-17, user-requested) -
     * lightweight rows only (no MT/MX content, see FlexcubeStatsService.ConversionSummary) so a
     * page of 25 stays cheap; fetch a row's full content via GET .../conversions/{id} only when
     * the user actually clicks it.
     */
    @GetMapping("/conversions")
    public FlexcubeStatsService.ConversionPage conversions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return stats.listConversions(Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/conversions/{id}")
    public ResponseEntity<FlexcubeStatsService.ConversionDetail> conversionDetail(@PathVariable long id) {
        FlexcubeStatsService.ConversionDetail detail = stats.getConversionDetail(id);
        return detail != null ? ResponseEntity.ok(detail) : ResponseEntity.notFound().build();
    }
}
