import { useEffect, useMemo, useState, useCallback } from "react";
import MessagePanel from "./components/MessagePanel.jsx";
import PipelineStatus from "./components/PipelineStatus.jsx";
import DiagnosticsPanel from "./components/DiagnosticsPanel.jsx";
import { fetchMappings, convertMessage } from "./lib/api.js";
import { SAMPLE_MT103 } from "./lib/samples.js";

const FALLBACK_MAPPINGS = [
  { conversion_id: "MT103_TO_PACS008", source_format: "MT103", target_format: "pacs.008.001.08" },
];

export default function App() {
  const [mappings, setMappings] = useState(FALLBACK_MAPPINGS);
  const [engineOnline, setEngineOnline] = useState(null); // null = checking, true/false after

  const [sourceFormat, setSourceFormat] = useState("MT103");
  const [targetFormat, setTargetFormat] = useState("pacs.008.001.08");
  const [rawText, setRawText] = useState("");

  const [status, setStatus] = useState("idle"); // idle | running | done | error
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchMappings()
      .then((data) => {
        if (data && data.length > 0) setMappings(data);
        setEngineOnline(true);
      })
      .catch(() => setEngineOnline(false));
  }, []);

  const sourceOptions = useMemo(
    () => [...new Set(mappings.map((m) => m.source_format))],
    [mappings]
  );
  const targetOptions = useMemo(
    () => mappings.filter((m) => m.source_format === sourceFormat).map((m) => m.target_format),
    [mappings, sourceFormat]
  );

  useEffect(() => {
    if (targetOptions.length > 0 && !targetOptions.includes(targetFormat)) {
      setTargetFormat(targetOptions[0]);
    }
  }, [targetOptions, targetFormat]);

  const handleConvert = useCallback(async () => {
    if (!rawText.trim()) return;
    setStatus("running");
    setError(null);
    setResult(null);
    try {
      const data = await convertMessage({ rawText, sourceFormat, targetFormat });
      setResult(data);
      setStatus("done");
    } catch (e) {
      setError({
        stage: e.stage,
        errorType: e.errorType,
        message: e.message,
        missing: e.missing,
        errors: e.errors,
        warnings: e.warnings,
        pipelineSteps: e.pipelineSteps,
      });
      setStatus("error");
    }
  }, [rawText, sourceFormat, targetFormat]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
        e.preventDefault();
        handleConvert();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleConvert]);

  const handleCopy = () => {
    if (result?.rendered_output) {
      navigator.clipboard.writeText(result.rendered_output);
    }
  };

  return (
    <div className="flex h-screen flex-col">
      {/* Header */}
      <header className="flex items-center justify-between border-b border-ledger-line bg-ledger-panelAlt px-5 py-3">
        <div className="flex items-baseline gap-3">
          <span className="text-[10px] font-display font-semibold uppercase tracking-widest2 text-ledger-inkDim">
            Wire Desk
          </span>
          <h1 className="font-display text-[15px] font-semibold text-ledger-ink">
            MT / MX Message Conversion
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={[
              "h-1.5 w-1.5 rounded-full",
              engineOnline === null
                ? "bg-ledger-inkDim animate-pulse"
                : engineOnline
                ? "bg-ledger-wire"
                : "bg-ledger-alarm",
            ].join(" ")}
          />
          <span className="text-[11px] text-ledger-inkDim">
            {engineOnline === null ? "Checking engine…" : engineOnline ? "Engine online" : "Engine unreachable"}
          </span>
        </div>
      </header>

      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 border-b border-ledger-line bg-ledger-panelAlt px-5 py-2.5">
        <FormatSelect label="Source" value={sourceFormat} options={sourceOptions} onChange={setSourceFormat} />
        <span className="text-ledger-inkDim">→</span>
        <FormatSelect label="Target" value={targetFormat} options={targetOptions} onChange={setTargetFormat} />

        <div className="flex-1" />

        <button
          onClick={() => setRawText(SAMPLE_MT103)}
          className="text-[11px] uppercase tracking-widest2 text-ledger-inkDim hover:text-ledger-accent transition-colors"
        >
          Load sample
        </button>
        <button
          onClick={handleConvert}
          disabled={status === "running" || !rawText.trim()}
          className="rounded bg-ledger-accent px-4 py-1.5 text-[12px] font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-40"
        >
          {status === "running" ? "Converting…" : "Convert message"}
        </button>
      </div>

      {/* Pipeline status */}
      <PipelineStatus
        status={status}
        failedStage={error?.stage}
        steps={status === "error" ? error?.pipelineSteps : result?.pipeline_steps}
      />

      {/* Panels */}
      <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-ledger-line md:grid-cols-2 md:divide-x md:divide-y-0">
        <MessagePanel
          eyebrow="Source message"
          formatBadge={sourceFormat}
          value={rawText}
          onChange={setRawText}
          placeholder={"Paste a raw MT or MX message here…\n\n(Cmd/Ctrl + Enter to convert)"}
        />
        <MessagePanel
          eyebrow="Converted message"
          formatBadge={targetFormat}
          value={result?.rendered_output || ""}
          readOnly
          onCopy={handleCopy}
          emptyState={
            status === "running"
              ? "Converting…"
              : "Converted output appears here."
          }
        />
      </div>

      <DiagnosticsPanel
        error={status === "error" ? error : null}
        warnings={result?.validation_warnings}
        auditWarnings={result?.audit_warnings}
        fieldTrace={result?.field_trace}
      />
    </div>
  );
}

function FormatSelect({ label, value, options, onChange }) {
  return (
    <label className="flex items-center gap-2 text-[11px] text-ledger-inkDim">
      <span className="uppercase tracking-widest2">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded border border-ledger-line bg-ledger-panel px-2 py-1 text-[12px] text-ledger-ink outline-none focus:border-ledger-accent"
      >
        {options.length === 0 && <option value={value}>{value}</option>}
        {options.map((opt) => (
          <option key={opt} value={opt}>
            {opt}
          </option>
        ))}
      </select>
    </label>
  );
}
