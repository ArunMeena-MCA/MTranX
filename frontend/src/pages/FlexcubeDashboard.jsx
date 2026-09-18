import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  fetchFlexcubeConversionDetail,
  fetchFlexcubeConversions,
  fetchFlexcubeStatus,
  pauseFlexcube,
  resumeFlexcube,
  runNowFlexcube,
  stopFlexcube,
} from "../lib/api.js";

const STATUS_POLL_MS = 2000;
const STAGE_CYCLE_MS = 750;
const CONVERSIONS_PAGE_SIZE = 25;
const THEME_STORAGE_KEY = "flexcube-dashboard-theme";

// The pipeline diagram is illustrative liveness, not a literal per-stage tracker - the backend
// only reports RUNNING/IDLE/PAUSED plus the file currently being read (see
// FlexcubeStatsService.Snapshot). There is no real per-stage timing to show, so the "active
// stage" highlight below cycles through these on a fixed local timer while RUNNING, giving a
// sense of motion through the real pipeline shape without pretending to know which exact backend
// line of code is executing at any instant. The five stages themselves ARE grounded in the real
// backend flow though (2026-09-19, user-requested "Flexcube -> AI assistant -> Conversion ->
// Validation -> Flexcube") - see ConversionOrchestrator.convert(): parse -> converter.convert()
// (the narrowly-scoped LLM step, "AI Assistant") -> render ("Conversion") -> validator.validate()
// ("Validation", looped back to Conversion on a retryable CONVERSION_ERROR) -> written back out.
const STAGES = [
  { key: "flexcube_out", label: "FLEXCUBE", sub: "MT_out" },
  { key: "ai", label: "AI Assistant", sub: "Semantic audit" },
  { key: "conversion", label: "Conversion", sub: "MT -> MX" },
  { key: "validation", label: "Validation", sub: "Schema & rules" },
  { key: "flexcube_in", label: "FLEXCUBE", sub: "MX_out" },
];

function useCountdown(nextCycleAt, state) {
  const [remaining, setRemaining] = useState(null);
  useEffect(() => {
    if (!nextCycleAt || state !== "IDLE") {
      setRemaining(null);
      return;
    }
    const target = new Date(nextCycleAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.round((target - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [nextCycleAt, state]);
  return remaining;
}

/**
 * Light/dark toggle for this page only (2026-09-19, user-requested). Persisted per-viewer in
 * localStorage so a reload keeps the last choice; applied via a "data-theme" attribute on this
 * page's own root element (see index.css's "[data-theme='light']" block) rather than on
 * documentElement, so it can never affect the separate main converter page.
 */
function useDashboardTheme() {
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem(THEME_STORAGE_KEY) === "light" ? "light" : "dark";
    } catch {
      return "dark";
    }
  });
  const toggleTheme = useCallback(() => {
    setTheme((prev) => {
      const next = prev === "dark" ? "light" : "dark";
      try {
        localStorage.setItem(THEME_STORAGE_KEY, next);
      } catch {
        // Private browsing / storage blocked - the toggle still works this session, it just won't
        // be remembered on the next visit.
      }
      return next;
    });
  }, []);
  return [theme, toggleTheme];
}

export default function FlexcubeDashboard() {
  const [theme, toggleTheme] = useDashboardTheme();
  const [status, setStatus] = useState(null);
  const [engineOnline, setEngineOnline] = useState(null);
  const [showFailures, setShowFailures] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [activeStage, setActiveStage] = useState(0);
  const pollRef = useRef(null);

  const poll = useCallback(async () => {
    try {
      const data = await fetchFlexcubeStatus();
      setStatus(data);
      setEngineOnline(true);
    } catch {
      setEngineOnline(false);
    }
  }, []);

  useEffect(() => {
    poll();
    pollRef.current = setInterval(poll, STATUS_POLL_MS);
    return () => clearInterval(pollRef.current);
  }, [poll]);

  const state = status?.snapshot?.state ?? null;
  const isRunning = state === "RUNNING";

  useEffect(() => {
    if (!isRunning) {
      setActiveStage(0);
      return;
    }
    const id = setInterval(() => setActiveStage((s) => (s + 1) % STAGES.length), STAGE_CYCLE_MS);
    return () => clearInterval(id);
  }, [isRunning]);

  const remainingSeconds = useCountdown(status?.snapshot?.next_cycle_at, state);

  const typeStats = status?.snapshot?.type_stats ?? [];
  const totals = useMemo(
    () =>
      typeStats.reduce(
        (acc, t) => ({
          received: acc.received + t.received,
          converted: acc.converted + t.converted,
          failed: acc.failed + t.failed,
        }),
        { received: 0, converted: 0, failed: 0 }
      ),
    [typeStats]
  );

  const recentFailures = status?.snapshot?.recent_failures ?? [];
  const recentEvents = status?.snapshot?.recent_events ?? [];
  const errorEvents = useMemo(() => recentEvents.filter((e) => e.level === "error"), [recentEvents]);
  const latestEvents = useMemo(() => [...recentEvents].slice(-40).reverse(), [recentEvents]);

  // Successful conversions - paginated server-side (25/page, newest first - see
  // FlexcubeStatsService.listConversions). Only re-fetched here on mount/page-change/manual
  // refresh, plus automatically whenever the total converted count grows WHILE viewing page 0 -
  // so a live new arrival appears without the user needing to do anything, but paging back
  // through history never shifts under them mid-read.
  const [conversionsData, setConversionsData] = useState(null);
  const [conversionsPage, setConversionsPage] = useState(0);
  const [showConversions, setShowConversions] = useState(false);
  const [selectedConversionId, setSelectedConversionId] = useState(null);
  const [selectedConversionDetail, setSelectedConversionDetail] = useState(null);
  const [selectedConversionError, setSelectedConversionError] = useState(null);
  const lastConvertedTotalRef = useRef(0);

  const loadConversions = useCallback(async (page) => {
    try {
      const data = await fetchFlexcubeConversions({ page, size: CONVERSIONS_PAGE_SIZE });
      setConversionsData(data);
    } catch {
      // Left as-is (stale/previous page still shown) - a transient fetch failure here isn't
      // worth its own error UI on top of the main status polling already covering "unreachable".
    }
  }, []);

  useEffect(() => {
    if (showConversions) {
      loadConversions(conversionsPage);
    }
  }, [showConversions, conversionsPage, loadConversions]);

  useEffect(() => {
    if (!showConversions || conversionsPage !== 0) return;
    if (totals.converted !== lastConvertedTotalRef.current) {
      lastConvertedTotalRef.current = totals.converted;
      loadConversions(0);
    }
  }, [totals.converted, showConversions, conversionsPage, loadConversions]);

  const openConversion = useCallback(async (id) => {
    setSelectedConversionId(id);
    setSelectedConversionDetail(null);
    setSelectedConversionError(null);
    try {
      const detail = await fetchFlexcubeConversionDetail(id);
      setSelectedConversionDetail(detail);
    } catch (e) {
      setSelectedConversionError(e.message);
    }
  }, []);

  const runAction = useCallback(
    async (fn) => {
      setActionPending(true);
      try {
        await fn();
        await poll();
      } catch {
        // Surfaced via the next status poll's own event log rather than a separate toast -
        // keeps this dashboard's error reporting in one place (the bottom panel).
      } finally {
        setActionPending(false);
      }
    },
    [poll]
  );

  return (
    <div data-theme={theme} className="hud-grid-bg flex h-screen flex-col overflow-y-auto bg-ledger-void text-ledger-ink">
      {/* Ambient glow orbs behind everything - see index.css's own note on why these exist: the
          glass panels' blur needs something colorful behind them to visibly distort, or the
          "glass" effect is technically present but invisible against a near-flat background. */}
      <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
        <div className="animate-orb-a absolute -left-24 -top-24 h-[420px] w-[420px] rounded-full bg-ledger-accent/30 blur-[110px]" />
        <div className="animate-orb-b absolute -right-20 top-1/3 h-[380px] w-[380px] rounded-full bg-ledger-wire/25 blur-[110px]" />
        <div className="animate-orb-c absolute -bottom-32 left-1/3 h-[440px] w-[440px] rounded-full bg-ledger-cyan/25 blur-[120px]" />
      </div>

      <TopBar
        state={state}
        engineOnline={engineOnline}
        actionPending={actionPending}
        theme={theme}
        onToggleTheme={toggleTheme}
        onPause={() => runAction(pauseFlexcube)}
        // Resuming from PAUSED (2026-09-19, user-requested "pause the timer on click and rerun
        // on next click") deliberately does more than resumeFlexcube() alone would: resuming just
        // restarts the countdown to the NEXT scheduled cycle (still poll-interval-ms away), which
        // would leave the button looking like nothing happened for however long that interval is.
        // Immediately following it with runNowFlexcube() kicks off a cycle right now - matching
        // the "rerun" the user asked for - while resumeFlexcube() having already cleared the pause
        // flag means the normal timer resumes counting down again once this cycle finishes,
        // instead of falling back into PAUSED (which is what run-now alone would do, since it
        // deliberately does not touch the pause flag - see FlexcubeFileWatcherJob.runNow()).
        onResume={() => runAction(async () => {
          await resumeFlexcube();
          await runNowFlexcube();
        })}
        onStop={() => runAction(stopFlexcube)}
        onRunNow={() => runAction(runNowFlexcube)}
      />

      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-5 px-5 py-6">
        <PipelineFlow
          isRunning={isRunning}
          activeStage={activeStage}
          currentFile={status?.snapshot?.current_file}
        />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_300px]">
          <TypeStatsPanel typeStats={typeStats} totals={totals} />
          <CountdownPanel state={state} remainingSeconds={remainingSeconds} pollIntervalMs={status?.snapshot?.poll_interval_ms} />
        </div>

        <FailuresPanel failures={recentFailures} show={showFailures} onToggle={() => setShowFailures((v) => !v)} />

        <ConversionsPanel
          data={conversionsData}
          show={showConversions}
          onToggle={() => setShowConversions((v) => !v)}
          onPageChange={setConversionsPage}
          onRowClick={openConversion}
        />

        <EventLogPanel events={latestEvents} errorCount={errorEvents.length} configured={status?.configured} enabled={status?.enabled} />
      </div>

      {selectedConversionId !== null && (
        <ConversionDetailModal
          detail={selectedConversionDetail}
          error={selectedConversionError}
          onClose={() => setSelectedConversionId(null)}
        />
      )}
    </div>
  );
}

function TopBar({ state, engineOnline, actionPending, theme, onToggleTheme, onPause, onResume, onStop, onRunNow }) {
  return (
    <header className="relative border-b border-ledger-line/70 bg-gradient-to-r from-ledger-panel via-ledger-panelAlt to-ledger-panel px-5 py-3.5 shadow-[0_1px_0_0_rgba(0,0,0,0.15)]">
      {/* Animated scanning underline - a thin bright band sweeping the header's bottom edge on
          loop, replacing the old static diagonal gradient overlay (which didn't hold up well
          against a light background). Purely decorative, pointer-events-none. */}
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px overflow-hidden">
        <div className="animate-nav-scan h-full w-1/4 bg-gradient-to-r from-transparent via-ledger-cyan to-transparent" />
      </div>

      {/*
        Deliberately NOT a 3-column flex-1 layout, and NOT centered via plain "absolute
        left-1/2" at a narrow breakpoint - both were tried and both overlapped the right-side
        controls at medium widths (~800px), since the right group's own content (status text +
        theme toggle + buttons) doesn't shrink and needs more than an equal one-third share. Below
        lg (1024px, where the left+right groups' combined natural width leaves comfortable slack
        either side) the title just sits on its own centered row underneath instead - never
        sharing a line with content that could push into it.
      */}
      <div className="relative flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-ledger-violet to-ledger-cyan shadow-[0_0_18px_rgba(139,92,246,0.5)]">
            <span className="font-hud text-base font-bold text-white">P</span>
          </div>
          <span className="truncate font-hud text-[15px] font-semibold tracking-wide text-ledger-ink">Profinch Solutions</span>
        </div>

        <div className="flex shrink-0 items-center gap-2.5">
          <div className="hidden items-center gap-1.5 sm:flex">
            <span
              className={[
                "h-1.5 w-1.5 shrink-0 rounded-full",
                engineOnline === null ? "bg-ledger-inkDim animate-pulse" : engineOnline ? "bg-ledger-wire animate-pulse-dot-green" : "bg-ledger-alarm",
              ].join(" ")}
            />
            <span className="whitespace-nowrap text-[11px] uppercase tracking-widest2 text-ledger-inkDim">
              {engineOnline === null ? "Connecting" : engineOnline ? "Engine online" : "Unreachable"}
            </span>
          </div>

          <ThemeToggle theme={theme} onToggle={onToggleTheme} />

          <div className="flex items-center gap-1.5">
            {state === "RUNNING" ? (
              <ControlButton tone="alarm" onClick={onStop} disabled={actionPending}>
                Stop
              </ControlButton>
            ) : state === "PAUSED" ? (
              <ControlButton tone="accent" onClick={onResume} disabled={actionPending}>
                Rerun
              </ControlButton>
            ) : (
              <ControlButton tone="default" onClick={onPause} disabled={actionPending}>
                Pause
              </ControlButton>
            )}
            <ControlButton tone="cyan" onClick={onRunNow} disabled={actionPending || state === "RUNNING"}>
              Run now
            </ControlButton>
          </div>
        </div>
      </div>

      {/*
        pointer-events-none (2026-09-19, real bug found via browser testing): at the lg breakpoint
        this title becomes absolutely positioned and spans the FULL header width (inset-x-0),
        which put it - with its own z-10 - directly on top of the Pause/Resume/Stop/Run now
        button group in the top-right corner and silently swallowed clicks aimed at them
        (confirmed via document.elementFromPoint() landing on this h1 instead of the button). The
        title is decorative text, never meant to be clicked, so disabling its pointer events is
        safe and lets clicks fall through to whatever is actually underneath.
      */}
      <h1 className="pointer-events-none relative z-10 mt-2 text-center font-hud text-[13px] font-semibold uppercase tracking-widest2 text-ledger-inkDim lg:absolute lg:inset-x-0 lg:top-1/2 lg:mt-0 lg:-translate-y-1/2 lg:text-sm">
        AI Assisted MT - MX Converter
      </h1>
    </header>
  );
}

function ThemeToggle({ theme, onToggle }) {
  const isDark = theme === "dark";
  return (
    <button
      onClick={onToggle}
      title={isDark ? "Switch to light theme" : "Switch to dark theme"}
      aria-label="Toggle color theme"
      className="flex h-7 w-7 shrink-0 items-center justify-center overflow-hidden rounded-md border border-ledger-line bg-ledger-panel text-ledger-inkDim transition-colors hover:border-ledger-cyan/60 hover:text-ledger-cyan"
    >
      <span key={theme} className="animate-icon-swap flex">
        {isDark ? <SunGlyph /> : <MoonGlyph />}
      </span>
    </button>
  );
}

function SunGlyph() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2.7M12 18.8v2.7M4.2 4.2l1.9 1.9M17.9 17.9l1.9 1.9M2.5 12h2.7M18.8 12h2.7M4.2 19.8l1.9-1.9M17.9 6.1l1.9-1.9" />
    </svg>
  );
}

function MoonGlyph() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.2 14.4A8.5 8.5 0 1 1 9.6 3.8a6.6 6.6 0 0 0 10.6 10.6z" />
    </svg>
  );
}

function ControlButton({ tone, onClick, disabled, children }) {
  const toneClass =
    tone === "alarm"
      ? "border-ledger-alarm/60 bg-ledger-alarmDim/60 text-ledger-alarm hover:bg-ledger-alarmDim"
      : tone === "accent"
      ? "border-ledger-accent/60 bg-ledger-accentDim/60 text-ledger-ink hover:bg-ledger-accentDim"
      : tone === "cyan"
      ? "border-ledger-cyan/50 bg-ledger-cyan/10 text-ledger-cyan hover:bg-ledger-cyan/20"
      : "border-ledger-line bg-ledger-panel text-ledger-ink hover:bg-ledger-panelAlt";
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={[
        "rounded-md border px-3 py-1.5 font-hud text-[12px] font-semibold uppercase tracking-widest2 transition-colors disabled:cursor-not-allowed disabled:opacity-40",
        toneClass,
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function PipelineFlow({ isRunning, activeStage, currentFile }) {
  return (
    <div className="glass-panel animate-rise-in animate-panel-glow rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <div className="mb-4 flex items-center justify-between">
        <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          Conversion pipeline
        </span>
        {isRunning ? (
          <span className="flex items-center gap-1.5 text-[12px] font-semibold text-ledger-cyan">
            <span className="h-1.5 w-1.5 animate-ping rounded-full bg-ledger-cyan" />
            {currentFile ? `Processing ${currentFile}` : "Live"}
          </span>
        ) : (
          <span className="text-[12px] text-ledger-inkDim">Idle</span>
        )}
      </div>

      <div className="flex flex-col items-stretch gap-2.5 overflow-x-auto md:flex-row md:items-center md:gap-0">
        {STAGES.flatMap((stage, i) => {
          const items = [<StageNode key={stage.key} stage={stage} active={isRunning && activeStage === i} />];
          if (i < STAGES.length - 1) items.push(<Connector key={`c-${i}`} active={isRunning} />);
          return items;
        })}
      </div>
    </div>
  );
}

function StageNode({ stage, active }) {
  return (
    <div
      className={[
        "glass-block flex min-w-0 flex-1 basis-[120px] flex-col items-center gap-1.5 rounded-lg border px-3 py-3.5 text-center transition-colors",
        active ? "border-ledger-cyan/70 bg-ledger-cyan/20 animate-node-glow" : "border-ledger-line bg-ledger-panelAlt/35",
      ].join(" ")}
    >
      <StageIcon stageKey={stage.key} active={active} />
      <div className="font-hud text-[13px] font-semibold text-ledger-ink">{stage.label}</div>
      <div className="text-[10.5px] uppercase tracking-widest2 text-ledger-inkDim">{stage.sub}</div>
    </div>
  );
}

function StageIcon({ stageKey, active }) {
  const color = active ? "rgb(var(--ledger-cyan))" : "rgb(var(--ledger-inkDim))";
  const common = { width: 22, height: 22, viewBox: "0 0 24 24", fill: "none", stroke: color, strokeWidth: 1.6 };
  switch (stageKey) {
    case "flexcube_out":
    case "flexcube_in":
      return (
        <svg {...common}>
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <path d="M3 9h18M8 4v5" />
        </svg>
      );
    case "ai":
      // Neural-network glyph (2026-09-19, replaces the old sun/brightness icon which read as
      // "brightness settings" rather than "AI") - a central node connected to five satellite
      // nodes, the common shorthand for "neural network / ML" in icon sets.
      return (
        <svg {...common}>
          <line x1="12" y1="12" x2="12" y2="4.2" />
          <line x1="12" y1="12" x2="5.1" y2="9.6" />
          <line x1="12" y1="12" x2="18.9" y2="9.6" />
          <line x1="12" y1="12" x2="8.2" y2="19.4" />
          <line x1="12" y1="12" x2="15.8" y2="19.4" />
          <circle cx="12" cy="12" r="2.1" fill={color} stroke="none" />
          <circle cx="12" cy="4.2" r="1.5" fill={color} stroke="none" />
          <circle cx="5.1" cy="9.6" r="1.5" fill={color} stroke="none" />
          <circle cx="18.9" cy="9.6" r="1.5" fill={color} stroke="none" />
          <circle cx="8.2" cy="19.4" r="1.5" fill={color} stroke="none" />
          <circle cx="15.8" cy="19.4" r="1.5" fill={color} stroke="none" />
        </svg>
      );
    case "conversion":
      return (
        <svg {...common}>
          <path d="M4 7h13l-3-3M20 17H7l3 3" />
        </svg>
      );
    case "validation":
      // Shield + check (2026-09-19, new stage between Conversion and the return FLEXCUBE leg) -
      // grounded in the real backend: ConversionOrchestrator loops converter.convert() through
      // ValidatorService.validate() (deterministic + LLM audit) before a result is ever returned.
      return (
        <svg {...common}>
          <path d="M12 3.2l6.5 2.6v5.4c0 4.3-2.8 7.3-6.5 8.6-3.7-1.3-6.5-4.3-6.5-8.6V5.8L12 3.2z" />
          <path d="M8.7 12.3l2.2 2.2 4.2-4.6" />
        </svg>
      );
    default:
      return null;
  }
}

function Connector({ active }) {
  return (
    <div className="relative hidden h-4 w-full min-w-0 flex-1 md:block">
      <svg className="absolute inset-0 h-full w-full" style={{ width: "100%" }} viewBox="0 0 100 4" preserveAspectRatio="none">
        <line
          x1="0"
          y1="2"
          x2="100"
          y2="2"
          stroke={active ? "rgb(var(--ledger-cyan))" : "rgb(var(--ledger-line))"}
          strokeWidth="2"
          className={active ? "animate-flow" : ""}
        />
      </svg>
      {/* Three small "packets" travelling left-to-right at staggered delays - a more realistic
          data-flow look than the dashed-line animation alone, closer to how live-throughput
          pipeline monitors usually render motion (2026-09-19, user-requested "more realistic flow
          animation"). Only rendered while a cycle is actually running. */}
      {active && (
        <>
          <span
            className="animate-packet absolute top-1/2 h-1.5 w-1.5 -translate-y-1/2 rounded-full bg-ledger-cyan shadow-[0_0_8px_2px_rgba(34,211,238,0.65)]"
            style={{ animationDelay: "0s" }}
          />
          <span
            className="animate-packet absolute top-1/2 h-1.5 w-1.5 -translate-y-1/2 rounded-full bg-ledger-cyan shadow-[0_0_8px_2px_rgba(34,211,238,0.65)]"
            style={{ animationDelay: "0.55s" }}
          />
          <span
            className="animate-packet absolute top-1/2 h-1.5 w-1.5 -translate-y-1/2 rounded-full bg-ledger-cyan shadow-[0_0_8px_2px_rgba(34,211,238,0.65)]"
            style={{ animationDelay: "1.1s" }}
          />
        </>
      )}
    </div>
  );
}

function TypeStatsPanel({ typeStats, totals }) {
  return (
    <div className="glass-panel animate-rise-in animate-panel-glow rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          Message types received
        </span>
        <span className="text-[11px] text-ledger-inkDim">
          {totals.received} total &middot; {totals.converted} converted &middot; {totals.failed} failed
        </span>
      </div>

      {typeStats.length === 0 ? (
        <div className="py-8 text-center text-[13px] text-ledger-inkDim">No messages seen yet this session.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-[13px]">
            <thead>
              <tr className="border-b border-ledger-line/60 text-[11px] uppercase tracking-widest2 text-ledger-inkDim">
                <th className="py-2 pr-3 font-medium">Input type</th>
                <th className="py-2 pr-3 font-medium">No. of inputs</th>
                <th className="py-2 pr-3 font-medium">Converted</th>
                <th className="py-2 pr-3 font-medium">Failed</th>
                <th className="py-2 pr-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {typeStats.map((t) => (
                <tr key={t.mt_type} className="animate-float-in border-b border-ledger-line/30 last:border-0">
                  <td className="py-2 pr-3 font-mono font-semibold text-ledger-ink">MT{t.mt_type}</td>
                  <td className="py-2 pr-3 text-ledger-ink">{t.received}</td>
                  <td className="py-2 pr-3 text-ledger-wire">{t.converted}</td>
                  <td className="py-2 pr-3 text-ledger-alarm">{t.failed}</td>
                  <td className="py-2 pr-3">
                    <span
                      className={[
                        "rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-widest2",
                        t.supported ? "bg-ledger-wireDim text-ledger-wire" : "bg-ledger-amberDim text-ledger-amber",
                      ].join(" ")}
                    >
                      {t.supported ? "Supported" : "No mapping"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function CountdownPanel({ state, remainingSeconds, pollIntervalMs }) {
  const label = state === "RUNNING" ? "Running" : state === "PAUSED" ? "Paused" : "Next cycle in";
  const display = state === "RUNNING" ? "..." : state === "PAUSED" ? "--" : remainingSeconds ?? "--";
  return (
    <div className="glass-panel animate-rise-in animate-panel-glow flex flex-col items-center justify-center rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">{label}</span>
      <span
        key={typeof display === "number" ? display : label}
        className="animate-tick mt-2 font-hud text-5xl font-bold text-ledger-cyan"
      >
        {typeof display === "number" ? `${display}s` : display}
      </span>
      {typeof pollIntervalMs === "number" && (
        <span className="mt-1.5 text-[11px] text-ledger-inkDim">cycle every {Math.round(pollIntervalMs / 1000)}s</span>
      )}
    </div>
  );
}

function FailuresPanel({ failures, show, onToggle }) {
  return (
    <div className="glass-panel animate-rise-in animate-panel-glow rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <button onClick={onToggle} className="flex w-full items-center justify-between text-left">
        <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          Failed messages ({failures.length})
        </span>
        <span className="text-[12px] text-ledger-accent">{show ? "Hide" : "View list"}</span>
      </button>

      {show && (
        <div className="animate-float-in mt-3 max-h-64 overflow-y-auto pane-scroll">
          {failures.length === 0 ? (
            <div className="py-6 text-center text-[13px] text-ledger-inkDim">Nothing failed this session.</div>
          ) : (
            <table className="w-full text-left text-[12.5px]">
              <thead>
                <tr className="border-b border-ledger-line/60 text-[10.5px] uppercase tracking-widest2 text-ledger-inkDim">
                  <th className="py-1.5 pr-3 font-medium">File</th>
                  <th className="py-1.5 pr-3 font-medium">Type</th>
                  <th className="py-1.5 pr-3 font-medium">Reason</th>
                  <th className="py-1.5 pr-3 font-medium">Detail</th>
                  <th className="py-1.5 pr-3 font-medium">When</th>
                </tr>
              </thead>
              <tbody>
                {[...failures].reverse().map((f, i) => (
                  <tr key={`${f.file_name}-${i}`} className="border-b border-ledger-line/20 align-top last:border-0">
                    <td className="py-1.5 pr-3 font-mono text-ledger-ink">{f.file_name}</td>
                    <td className="py-1.5 pr-3 text-ledger-ink">MT{f.mt_type}</td>
                    <td className="py-1.5 pr-3 text-ledger-alarm">{f.reason.replace(/_/g, " ")}</td>
                    <td className="max-w-[360px] py-1.5 pr-3 text-ledger-inkDim">{f.detail}</td>
                    <td className="py-1.5 pr-3 whitespace-nowrap text-ledger-inkDim">{new Date(f.at).toLocaleTimeString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

function ConversionsPanel({ data, show, onToggle, onPageChange, onRowClick }) {
  const items = data?.items ?? [];
  const total = data?.total ?? 0;
  const page = data?.page ?? 0;
  const size = data?.size ?? CONVERSIONS_PAGE_SIZE;
  const totalPages = Math.max(1, Math.ceil(total / size));

  return (
    <div className="glass-panel animate-rise-in animate-panel-glow rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <button onClick={onToggle} className="flex w-full items-center justify-between text-left">
        <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          Successful conversions ({total})
        </span>
        <span className="text-[12px] text-ledger-accent">{show ? "Hide" : "View list"}</span>
      </button>

      {show && (
        <div className="animate-float-in mt-3">
          {items.length === 0 ? (
            <div className="py-6 text-center text-[13px] text-ledger-inkDim">Nothing converted yet this session.</div>
          ) : (
            <div className="max-h-64 overflow-y-auto pane-scroll">
              <table className="w-full text-left text-[12.5px]">
                <thead>
                  <tr className="border-b border-ledger-line/60 text-[10.5px] uppercase tracking-widest2 text-ledger-inkDim">
                    <th className="py-1.5 pr-3 font-medium">File</th>
                    <th className="py-1.5 pr-3 font-medium">Type</th>
                    <th className="py-1.5 pr-3 font-medium">Converted at</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((c) => (
                    <tr
                      key={c.id}
                      onClick={() => onRowClick(c.id)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onRowClick(c.id)}
                      className="cursor-pointer border-b border-ledger-line/20 align-top transition-colors last:border-0 hover:bg-ledger-panelAlt/60"
                    >
                      <td className="py-1.5 pr-3 font-mono text-ledger-ink">{c.file_name}</td>
                      <td className="py-1.5 pr-3 text-ledger-wire">MT{c.mt_type}</td>
                      <td className="py-1.5 pr-3 whitespace-nowrap text-ledger-inkDim">{new Date(c.at).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {total > 0 && (
            <div className="mt-3 flex items-center justify-between border-t border-ledger-line/40 pt-2.5">
              <span className="text-[11px] text-ledger-inkDim">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => onPageChange(Math.max(0, page - 1))}
                  disabled={page <= 0}
                  className="rounded border border-ledger-line px-2 py-1 text-[11px] uppercase tracking-widest2 text-ledger-inkDim transition-colors hover:text-ledger-ink disabled:cursor-not-allowed disabled:opacity-30"
                >
                  Previous
                </button>
                <button
                  onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="rounded border border-ledger-line px-2 py-1 text-[11px] uppercase tracking-widest2 text-ledger-inkDim transition-colors hover:text-ledger-ink disabled:cursor-not-allowed disabled:opacity-30"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ConversionDetailModal({ detail, error, onClose }) {
  useEffect(() => {
    const onKeyDown = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div
      className="animate-float-in fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onClick={onClose}
    >
      <div
        className="flex max-h-[85vh] w-full max-w-4xl flex-col overflow-hidden rounded-xl border border-ledger-line bg-ledger-panel shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-ledger-line/60 px-4 py-3">
          <div>
            {detail ? (
              <>
                <div className="font-mono text-sm font-semibold text-ledger-ink">{detail.file_name}</div>
                <div className="text-[11px] uppercase tracking-widest2 text-ledger-inkDim">
                  MT{detail.mt_type} &middot; converted {new Date(detail.at).toLocaleString()}
                </div>
              </>
            ) : (
              <div className="text-[13px] text-ledger-inkDim">{error ? "Could not load conversion" : "Loading…"}</div>
            )}
          </div>
          <button
            onClick={onClose}
            className="rounded border border-ledger-line px-2.5 py-1 text-[11px] uppercase tracking-widest2 text-ledger-inkDim transition-colors hover:text-ledger-ink"
          >
            Close
          </button>
        </div>

        <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-ledger-line/60 overflow-y-auto md:grid-cols-2 md:divide-x md:divide-y-0">
          {error ? (
            <div className="col-span-2 p-6 text-center text-[13px] text-ledger-alarm">{error}</div>
          ) : !detail ? (
            <div className="col-span-2 flex items-center justify-center gap-2 p-10 text-[13px] text-ledger-accent">
              <span className="h-1.5 w-1.5 animate-pulse-dot rounded-full bg-ledger-accent" />
              Loading conversion…
            </div>
          ) : (
            <>
              <div className="flex min-h-[200px] flex-col">
                <div className="border-b border-ledger-line/60 px-4 py-2">
                  <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
                    MT{detail.mt_type} (source)
                  </span>
                </div>
                <pre className="pane-scroll flex-1 overflow-auto whitespace-pre-wrap break-words p-4 text-[13px] leading-relaxed text-ledger-ink">
                  {detail.mt_content}
                </pre>
              </div>
              <div className="flex min-h-[200px] flex-col">
                <div className="border-b border-ledger-line/60 px-4 py-2">
                  <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
                    Converted (MX)
                  </span>
                </div>
                <pre className="pane-scroll flex-1 overflow-auto whitespace-pre-wrap break-words p-4 text-[13px] leading-relaxed text-ledger-ink">
                  {detail.mx_content}
                </pre>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function EventLogPanel({ events, errorCount, configured, enabled }) {
  return (
    <div className="glass-panel animate-rise-in animate-panel-glow rounded-xl border border-ledger-line/60 bg-ledger-panel/55 p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="font-hud text-[11px] font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          Pipeline activity &amp; errors
        </span>
        {errorCount > 0 && (
          <span className="rounded bg-ledger-alarmDim px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-widest2 text-ledger-alarm">
            {errorCount} error{errorCount === 1 ? "" : "s"}
          </span>
        )}
      </div>

      {enabled === false && (
        <div className="mb-2 rounded border border-ledger-amber/40 bg-ledger-amberDim/40 px-3 py-2 text-[12px] text-ledger-amber">
          FLEXCUBE_ENABLED is false - the automatic cycle will not run until it is enabled in backend/.env.
        </div>
      )}
      {enabled === true && configured === false && (
        <div className="mb-2 rounded border border-ledger-amber/40 bg-ledger-amberDim/40 px-3 py-2 text-[12px] text-ledger-amber">
          Enabled, but not fully configured - check FLEXCUBE_* settings in backend/.env.
        </div>
      )}

      <div className="max-h-48 overflow-y-auto pane-scroll font-mono text-[12.5px] leading-relaxed">
        {events.length === 0 ? (
          <div className="text-ledger-inkDim/70">No activity yet.</div>
        ) : (
          events.map((e, i) => (
            <div
              key={`${e.at}-${i}`}
              className={[
                "animate-float-in",
                e.level === "error" ? "text-ledger-alarm" : e.level === "success" ? "text-ledger-wire" : "text-ledger-inkDim",
              ].join(" ")}
            >
              <span className="text-ledger-inkDim/60">{new Date(e.at).toLocaleTimeString()}</span> {e.message}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
