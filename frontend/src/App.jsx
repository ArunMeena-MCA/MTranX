import { useEffect, useMemo, useState, useCallback } from "react";
import MessagePanel from "./components/MessagePanel.jsx";
import PipelineStatus from "./components/PipelineStatus.jsx";
import DiagnosticsPanel from "./components/DiagnosticsPanel.jsx";
import UploadMappingPanel from "./components/UploadMappingPanel.jsx";
import { fetchMappings, convertMessage } from "./lib/api.js";
import { SAMPLE_MT103 } from "./lib/samples.js";
import { detectSourceFormat, isMtFormat } from "./lib/detectFormat.js";

const FALLBACK_MAPPINGS = [
  { conversion_id: "MT103_TO_PACS008", source_format: "MT103", target_format: "pacs.008.001.08" },
];

export default function App() {
  const [mappings, setMappings] = useState(FALLBACK_MAPPINGS);
  const [engineOnline, setEngineOnline] = useState(null); // null = checking, true/false after
  const [view, setView] = useState("convert"); // convert | upload
  const [convertDirection, setConvertDirection] = useState("MT_TO_MX"); // MT_TO_MX | MX_TO_MT

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

  // Every registered mapping is classified MT_TO_MX or MX_TO_MT by its own
  // source_format's shape (same MT-vs-MX convention the backend itself
  // uses for rendering - see isMtFormat) - this is what lets the direction
  // toggle actually filter the dropdowns instead of showing every
  // conversion pair merged into one flat, directionless list.
  const mappingsForDirection = useMemo(
    () =>
      mappings.filter((m) =>
        convertDirection === "MT_TO_MX" ? isMtFormat(m.source_format) : !isMtFormat(m.source_format)
      ),
    [mappings, convertDirection]
  );

  const sourceOptions = useMemo(
    () => [...new Set(mappingsForDirection.map((m) => m.source_format))],
    [mappingsForDirection]
  );
  const targetOptions = useMemo(
    () => mappingsForDirection.filter((m) => m.source_format === sourceFormat).map((m) => m.target_format),
    [mappingsForDirection, sourceFormat]
  );

  useEffect(() => {
    if (sourceOptions.length > 0 && !sourceOptions.includes(sourceFormat)) {
      setSourceFormat(sourceOptions[0]);
    }
  }, [sourceOptions, sourceFormat]);

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

  // Unfiltered (both directions) - used only to check "is this detected
  // format supported at all", separate from sourceOptions (which is
  // filtered to the CURRENTLY selected direction for the dropdown).
  const allSourceFormats = useMemo(
    () => [...new Set(mappings.map((m) => m.source_format))],
    [mappings]
  );

  const handleRawTextChange = useCallback(
    (text) => {
      setRawText(text);
      const detected = detectSourceFormat(text);
      // Only auto-select a format the backend's mapping docs actually
      // support - never set the dropdown to a value with no matching
      // <option>, and never override with a no-op when it's already
      // correct. A paste can also imply the OTHER direction (e.g. pasting
      // an MX message while the toggle is still on MT->MX) - switch the
      // toggle too so the dropdown that ends up showing it is the right one.
      if (detected && detected !== sourceFormat && allSourceFormats.includes(detected)) {
        const detectedDirection = isMtFormat(detected) ? "MT_TO_MX" : "MX_TO_MT";
        if (detectedDirection !== convertDirection) {
          setConvertDirection(detectedDirection);
        }
        setSourceFormat(detected);
      }
    },
    [sourceFormat, allSourceFormats, convertDirection]
  );

  const handleMappingUploaded = useCallback(() => {
    fetchMappings()
      .then((data) => {
        if (data && data.length > 0) setMappings(data);
      })
      .catch(() => {});
    setView("convert");
  }, []);

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
          <nav className="flex items-center gap-1 pl-2">
            <button
              onClick={() => setView("convert")}
              className={[
                "rounded px-2 py-1 text-[11px] uppercase tracking-widest2 transition-colors",
                view === "convert" ? "text-ledger-accent" : "text-ledger-inkDim hover:text-ledger-ink",
              ].join(" ")}
            >
              Convert
            </button>
            <button
              onClick={() => setView("upload")}
              className={[
                "rounded px-2 py-1 text-[11px] uppercase tracking-widest2 transition-colors",
                view === "upload" ? "text-ledger-accent" : "text-ledger-inkDim hover:text-ledger-ink",
              ].join(" ")}
            >
              Upload mapping
            </button>
          </nav>
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

      {view === "upload" ? (
        <UploadMappingPanel onUploaded={handleMappingUploaded} />
      ) : (
        <>
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 border-b border-ledger-line bg-ledger-panelAlt px-5 py-2.5">
        <div className="flex items-center gap-1">
          {[
            { value: "MT_TO_MX", label: "MT → MX" },
            { value: "MX_TO_MT", label: "MX → MT" },
          ].map((d) => (
            <button
              key={d.value}
              onClick={() => setConvertDirection(d.value)}
              className={[
                "rounded border px-2.5 py-1 text-[11px] font-semibold transition-colors",
                convertDirection === d.value
                  ? "border-ledger-accent bg-ledger-accentDim text-ledger-ink"
                  : "border-ledger-line bg-ledger-panel text-ledger-inkDim hover:text-ledger-ink",
              ].join(" ")}
            >
              {d.label}
            </button>
          ))}
        </div>
        <span className="text-ledger-inkDim">|</span>
        <FormatSelect label="Source" value={sourceFormat} options={sourceOptions} onChange={setSourceFormat} />
        <span className="text-ledger-inkDim">→</span>
        <FormatSelect label="Target" value={targetFormat} options={targetOptions} onChange={setTargetFormat} />

        <div className="flex-1" />

        <button
          onClick={() => handleRawTextChange(SAMPLE_MT103)}
          className="text-[11px] uppercase tracking-widest2 text-ledger-inkDim hover:text-ledger-accent transition-colors"
        >
          Load sample
        </button>
        <button
          onClick={handleConvert}
          disabled={status === "running" || !rawText.trim() || sourceOptions.length === 0 || targetOptions.length === 0}
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
          onChange={handleRawTextChange}
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
        </>
      )}
    </div>
  );
}

function FormatSelect({ label, value, options, onChange }) {
  return (
    <label className="flex items-center gap-2 text-[11px] text-ledger-inkDim">
      <span className="uppercase tracking-widest2">{label}</span>
      <select
        value={options.length === 0 ? "" : value}
        onChange={(e) => onChange(e.target.value)}
        disabled={options.length === 0}
        className="rounded border border-ledger-line bg-ledger-panel px-2 py-1 text-[12px] text-ledger-ink outline-none focus:border-ledger-accent disabled:opacity-50"
      >
        {options.length === 0 && <option value="">none registered</option>}
        {options.map((opt) => (
          <option key={opt} value={opt}>
            {opt}
          </option>
        ))}
      </select>
    </label>
  );
}
