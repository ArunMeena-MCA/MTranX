import { useCallback, useMemo, useRef, useState } from "react";
import { fetchAutomatedMessages, convertAutomatedMessage } from "../lib/api.js";

const STATUS_META = {
  pending: { label: "Queued", dotClass: "bg-ledger-inkDim/50" },
  converting: { label: "Translating…", dotClass: "bg-ledger-accent animate-pulse-dot" },
  done: { label: "Converted", dotClass: "bg-ledger-wire animate-pulse-dot-green" },
  error: { label: "Failed", dotClass: "bg-ledger-alarm" },
};

const MAX_ATTEMPTS = 3;
const PAUSE_MS = 5000;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * The automated dashboard: pulls a fixed batch of real MT103 messages from Oracle
 * (pmtb_msg_dly_msg_out, via /api/automated/messages) and translates them one at a time
 * (/api/automated/convert), showing live per-row status - matching the "green dot on translated
 * entries" and "sequential" behavior the user asked for. Deliberately driven from THIS component's
 * own sequential for-loop rather than a backend batch/streaming endpoint - see
 * AutomatedConversionController's own Javadoc for why (keeps the backend stateless, no new
 * WebSocket/SSE machinery needed for a 10-row demo).
 */
export default function AutomatedDashboard() {
  const [rows, setRows] = useState([]);
  const [selectedRef, setSelectedRef] = useState(null);
  const [isRunning, setIsRunning] = useState(false);
  const [retryingRef, setRetryingRef] = useState(null);
  const [phase, setPhase] = useState("idle"); // idle | fetching | running | done | error
  const [logs, setLogs] = useState([]);
  const logIdRef = useRef(0);
  const logEndRef = useRef(null);
  // Source messages never change after the initial fetch, only each row's status/result/error
  // do - kept in a ref (not state) so handleRetry always reads the right message for a given
  // reference number without depending on a possibly-stale `rows` closure.
  const messagesRef = useRef({});

  const appendLog = useCallback((text, tone = "info") => {
    logIdRef.current += 1;
    setLogs((prev) => {
      const next = [...prev, { id: logIdRef.current, text, tone, at: new Date() }];
      return next.length > 300 ? next.slice(next.length - 300) : next;
    });
    requestAnimationFrame(() => logEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" }));
  }, []);

  // Shared by both the initial sequential batch and a single-row retry - converts one message,
  // automatically retrying up to MAX_ATTEMPTS times (with a PAUSE_MS gap between attempts) before
  // giving up and marking the row failed. Updates that row's status/result/error in place
  // throughout, so the sidebar dot and detail panel stay live across every attempt.
  const convertOne = useCallback(
    async (referenceNo) => {
      const message = messagesRef.current[referenceNo];
      // A row can match the Oracle query but still have a NULL/blank MESSAGE column (seen in
      // real data: 9/10 fetched rows converted cleanly, the 10th had no content at all) -
      // caught here BEFORE calling the backend so the user sees this specific, honest reason
      // instead of a generic "message must not be blank" validation dump from the API layer.
      // Not retried - retrying can't manufacture message content that isn't there.
      if (!message || !message.trim()) {
        setRows((prev) =>
          prev.map((r) =>
            r.referenceNo === referenceNo
              ? { ...r, status: "error", error: "No MESSAGE content for this reference in Oracle (column is NULL/blank) - nothing to translate." }
              : r
          )
        );
        appendLog(`${referenceNo} skipped — no MESSAGE content in Oracle for this reference.`, "error");
        return false;
      }

      for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        setRows((prev) => prev.map((r) => (r.referenceNo === referenceNo ? { ...r, status: "converting", error: null } : r)));
        appendLog(`Translating ${referenceNo} → pacs.008.001.08 (attempt ${attempt}/${MAX_ATTEMPTS}) …`);
        try {
          const result = await convertAutomatedMessage({ referenceNo, message });
          setRows((prev) => prev.map((r) => (r.referenceNo === referenceNo ? { ...r, status: "done", result, error: null } : r)));
          appendLog(`${referenceNo} converted and archived to backend/sample/${referenceNo}.md`, "success");
          return true;
        } catch (e) {
          if (attempt < MAX_ATTEMPTS) {
            appendLog(`${referenceNo} failed (attempt ${attempt}/${MAX_ATTEMPTS}) — ${e.message}. Retrying in 5s…`, "error");
            await sleep(PAUSE_MS);
          } else {
            setRows((prev) => prev.map((r) => (r.referenceNo === referenceNo ? { ...r, status: "error", error: e.message } : r)));
            appendLog(`${referenceNo} failed after ${MAX_ATTEMPTS} attempts — ${e.message}. Skipping.`, "error");
            return false;
          }
        }
      }
      return false; // unreachable, satisfies eslint's consistent-return
    },
    [appendLog]
  );

  const handleStart = useCallback(async () => {
    if (isRunning) return;
    setIsRunning(true);
    setPhase("fetching");
    setLogs([]);
    setRows([]);
    setSelectedRef(null);
    messagesRef.current = {};
    appendLog("Connecting to Oracle source — pmtb_msg_dly_msg_out …");

    let fetched;
    try {
      fetched = await fetchAutomatedMessages();
    } catch (e) {
      appendLog(`Fetch failed: ${e.message}`, "error");
      setPhase("error");
      setIsRunning(false);
      return;
    }

    appendLog(`Fetched ${fetched.length} message${fetched.length === 1 ? "" : "s"} (media=SWIFT, module=PX, swift_msg_type=103).`, "success");

    const initialRows = fetched.map((m) => ({
      referenceNo: m.reference_no,
      message: m.message,
      status: "pending",
      result: null,
      error: null,
    }));
    messagesRef.current = Object.fromEntries(initialRows.map((r) => [r.referenceNo, r.message]));
    setRows(initialRows);
    if (initialRows.length > 0) setSelectedRef(initialRows[0].referenceNo);
    setPhase("running");

    for (let i = 0; i < initialRows.length; i++) {
      await convertOne(initialRows[i].referenceNo);
      const isLast = i === initialRows.length - 1;
      if (!isLast) {
        appendLog(`Waiting 5s before the next entry…`);
        await sleep(PAUSE_MS);
      }
    }

    appendLog("Batch complete.", "success");
    setPhase("done");
    setIsRunning(false);
  }, [isRunning, appendLog, convertOne]);

  const handleRetry = useCallback(
    async (referenceNo, event) => {
      event.stopPropagation();
      if (isRunning || retryingRef) return;
      setRetryingRef(referenceNo);
      await convertOne(referenceNo);
      setRetryingRef(null);
    },
    [isRunning, retryingRef, convertOne]
  );

  const selected = useMemo(() => rows.find((r) => r.referenceNo === selectedRef) || null, [rows, selectedRef]);

  const counts = useMemo(() => {
    const c = { done: 0, error: 0, total: rows.length };
    rows.forEach((r) => {
      if (r.status === "done") c.done += 1;
      if (r.status === "error") c.error += 1;
    });
    return c;
  }, [rows]);

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col bg-[#080B14] text-ledger-ink">
      {/* Top strip: branding + control + live counters */}
      <div className="relative overflow-hidden border-b border-ledger-line/60 bg-gradient-to-r from-[#0B0F1D] via-[#0E1428] to-[#0B0F1D] px-5 py-3">
        <div
          className="animate-gradient pointer-events-none absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              "linear-gradient(120deg, rgba(139,92,246,0.5), rgba(34,211,238,0.4), rgba(91,127,222,0.5))",
          }}
        />
        <div className="relative flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="relative flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-ledger-violet to-ledger-cyan shadow-[0_0_18px_rgba(139,92,246,0.55)]">
              <span className="font-display text-sm font-bold text-white">P</span>
            </div>
            <div>
              <div className="font-display text-[13px] font-semibold tracking-wide text-white">Profinch</div>
              <div className="text-[10px] uppercase tracking-widest2 text-ledger-inkDim">
                Autonomous MT → MX Translation
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {phase !== "idle" && (
              <div className="hidden items-center gap-3 sm:flex">
                <StatCounter label="Fetched" value={rows.length} />
                <StatCounter label="Converted" value={counts.done} tone="wire" />
                <StatCounter label="Failed" value={counts.error} tone="alarm" />
              </div>
            )}
            <button
              onClick={handleStart}
              disabled={isRunning}
              className="group relative overflow-hidden rounded-md bg-gradient-to-r from-ledger-violet to-ledger-cyan px-4 py-1.5 text-[12px] font-semibold text-white shadow-[0_0_20px_rgba(139,92,246,0.35)] transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {isRunning ? (
                <span className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 animate-ping rounded-full bg-white" />
                  Running…
                </span>
              ) : (
                "Fetch & Translate 10 messages"
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Body: sidebar + viewer + activity log */}
      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[260px_1fr]">
        {/* Sidebar */}
        <div className="flex min-h-0 flex-col border-b border-ledger-line/60 bg-[#0A0E1A] lg:border-b-0 lg:border-r">
          <div className="border-b border-ledger-line/60 px-3 py-2 text-[10px] uppercase tracking-widest2 text-ledger-inkDim">
            Reference numbers
          </div>
          <div className="pane-scroll flex-1 overflow-y-auto">
            {rows.length === 0 ? (
              <div className="px-3 py-6 text-center text-[12px] text-ledger-inkDim">
                {phase === "fetching" ? "Fetching from Oracle…" : "Nothing fetched yet."}
              </div>
            ) : (
              rows.map((row) => {
                const meta = STATUS_META[row.status];
                const active = row.referenceNo === selectedRef;
                const isRetryingThis = retryingRef === row.referenceNo;
                return (
                  <div
                    key={row.referenceNo}
                    role="button"
                    tabIndex={0}
                    onClick={() => setSelectedRef(row.referenceNo)}
                    onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && setSelectedRef(row.referenceNo)}
                    className={[
                      "flex w-full cursor-pointer items-center gap-2.5 border-b border-ledger-line/30 px-3 py-2.5 text-left transition-colors animate-float-in",
                      active ? "bg-ledger-accentDim/60" : "hover:bg-ledger-panelAlt/60",
                    ].join(" ")}
                  >
                    <span className={["h-2 w-2 shrink-0 rounded-full", meta.dotClass].join(" ")} />
                    <span className="flex-1 truncate font-mono text-[12px] text-ledger-ink">{row.referenceNo}</span>
                    {row.status === "error" ? (
                      <button
                        onClick={(e) => handleRetry(row.referenceNo, e)}
                        disabled={isRunning || retryingRef !== null}
                        className="shrink-0 rounded border border-ledger-alarm/50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-widest2 text-ledger-alarm transition-colors hover:bg-ledger-alarmDim/60 disabled:opacity-40"
                      >
                        {isRetryingThis ? "Retrying…" : "Retry"}
                      </button>
                    ) : (
                      <span className="shrink-0 text-[10px] uppercase tracking-widest2 text-ledger-inkDim">
                        {meta.label}
                      </span>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Viewer + log */}
        <div className="flex min-h-0 flex-col">
          <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-ledger-line/60 md:grid-cols-2 md:divide-x md:divide-y-0">
            <MessageViewer
              eyebrow="MT103 (source)"
              value={selected?.message}
              emptyState="Select a reference number to view its source message."
            />
            <MessageViewer
              eyebrow="pacs.008.001.08 (converted)"
              // Prefers the real, single-root <Envelope> combining AppHdr + Document (see
              // BusinessApplicationHeaderRenderer.renderEnvelope()) when a head.001 header
              // applies - matches exactly what gets written to the .md archive. Falls back to
              // the bare Document when no header was produced for this mapping doc.
              value={selected?.result ? selected.result.envelope_output || selected.result.rendered_output : null}
              errorText={selected?.status === "error" ? selected.error : null}
              pending={selected?.status === "converting"}
              emptyState="Converted output appears here once translated."
            />
          </div>

          {/* Activity log */}
          <div className="h-40 shrink-0 border-t border-ledger-line/60 bg-black/40">
            <div className="flex items-center justify-between border-b border-ledger-line/40 px-3 py-1.5">
              <span className="text-[10px] uppercase tracking-widest2 text-ledger-inkDim">Activity log</span>
              {isRunning && (
                <span className="flex items-center gap-1.5 text-[10px] text-ledger-cyan">
                  <span className="h-1.5 w-1.5 animate-ping rounded-full bg-ledger-cyan" />
                  live
                </span>
              )}
            </div>
            <div className="pane-scroll h-[calc(100%-28px)] overflow-y-auto px-3 py-1.5 font-mono text-[11px] leading-relaxed">
              {logs.length === 0 ? (
                <div className="text-ledger-inkDim/70">Awaiting run — click "Fetch &amp; Translate" to begin.</div>
              ) : (
                logs.map((l) => (
                  <div
                    key={l.id}
                    className={[
                      "animate-float-in",
                      l.tone === "error"
                        ? "text-ledger-alarm"
                        : l.tone === "success"
                        ? "text-ledger-wire"
                        : "text-ledger-inkDim",
                    ].join(" ")}
                  >
                    <span className="text-ledger-inkDim/60">{l.at.toLocaleTimeString()}</span> {l.text}
                  </div>
                ))
              )}
              <div ref={logEndRef} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCounter({ label, value, tone }) {
  const color =
    tone === "wire" ? "text-ledger-wire" : tone === "alarm" ? "text-ledger-alarm" : "text-ledger-ink";
  return (
    <div className="flex flex-col items-center leading-none">
      <span className={["font-display text-[15px] font-semibold", color].join(" ")}>{value}</span>
      <span className="mt-0.5 text-[9px] uppercase tracking-widest2 text-ledger-inkDim">{label}</span>
    </div>
  );
}

function MessageViewer({ eyebrow, value, emptyState, errorText, pending }) {
  return (
    <div className="flex h-full min-h-[220px] flex-col bg-ledger-panel">
      <div className="border-b border-ledger-line/60 px-4 py-2">
        <span className="text-[10px] font-display font-semibold uppercase tracking-widest2 text-ledger-inkDim">
          {eyebrow}
        </span>
      </div>
      <div className="relative flex-1">
        {pending ? (
          <div className="absolute inset-0 flex items-center justify-center gap-2 text-[12px] text-ledger-accent">
            <span className="h-1.5 w-1.5 animate-pulse-dot rounded-full bg-ledger-accent" />
            Translating…
          </div>
        ) : errorText ? (
          <div className="absolute inset-0 overflow-auto p-4 text-[12px] text-ledger-alarm">{errorText}</div>
        ) : value ? (
          <pre className="pane-scroll absolute inset-0 overflow-auto whitespace-pre-wrap break-words p-4 text-[13px] leading-relaxed">
            {value}
          </pre>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center px-8 text-center text-sm text-ledger-inkDim">
            {emptyState}
          </div>
        )}
      </div>
    </div>
  );
}
