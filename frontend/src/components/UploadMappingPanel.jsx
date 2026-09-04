import { useEffect, useState } from "react";
import { checkMappingExists, uploadMapping } from "../lib/api.js";
import DiagnosticsPanel from "./DiagnosticsPanel.jsx";

const DIRECTIONS = [
  { value: "MT_TO_MX", label: "MT → MX" },
  { value: "MX_TO_MT", label: "MX → MT" },
];

export default function UploadMappingPanel({ onUploaded }) {
  const [direction, setDirection] = useState("MT_TO_MX");
  const [sourceFormat, setSourceFormat] = useState("");
  const [targetFormat, setTargetFormat] = useState("");
  const [mappingFile, setMappingFile] = useState(null);
  const [xsdFile, setXsdFile] = useState(null);

  const [checkResult, setCheckResult] = useState(null);
  const [confirmOverwrite, setConfirmOverwrite] = useState(false);

  const [status, setStatus] = useState("idle"); // idle | submitting | done | error
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const xsdRequired = direction === "MT_TO_MX";

  // Debounced existence check as soon as both format fields have values -
  // this is what powers the overwrite notice before any file is attached.
  useEffect(() => {
    setCheckResult(null);
    setConfirmOverwrite(false);
    if (!sourceFormat.trim() || !targetFormat.trim()) {
      return;
    }
    const timer = setTimeout(() => {
      checkMappingExists({ sourceFormat: sourceFormat.trim(), targetFormat: targetFormat.trim() })
        .then(setCheckResult)
        .catch(() => setCheckResult(null));
    }, 400);
    return () => clearTimeout(timer);
  }, [sourceFormat, targetFormat]);

  const canSubmit =
    sourceFormat.trim() &&
    targetFormat.trim() &&
    mappingFile &&
    (!xsdRequired || xsdFile) &&
    status !== "submitting";

  const handleSubmit = async () => {
    setStatus("submitting");
    setError(null);
    setResult(null);
    try {
      const data = await uploadMapping({
        direction,
        sourceFormat: sourceFormat.trim(),
        targetFormat: targetFormat.trim(),
        mappingFile,
        xsdFile,
        confirm: confirmOverwrite,
      });
      setResult(data);
      setStatus("done");
      onUploaded?.();
    } catch (e) {
      // A conflict (overwrite or filename mismatch not yet confirmed) comes
      // back with stage "upload" - surface the confirm checkbox instead of
      // just a dead-end error, since the user can resolve it themselves.
      if (e.stage === "upload") {
        setCheckResult((prev) => prev || { exists: true });
      }
      setError({ stage: e.stage, errorType: e.errorType, message: e.message, missing: e.missing, errors: e.errors, warnings: e.warnings });
      setStatus("error");
    }
  };

  const showOverwriteNotice = checkResult?.exists;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5 overflow-auto p-6">
      <div>
        <h2 className="font-display text-[15px] font-semibold text-ledger-ink">Upload a mapping document</h2>
        <p className="mt-1 text-[12px] text-ledger-inkDim">
          Register a new MT↔MX conversion, or overwrite an existing one, without restarting the engine.
        </p>
      </div>

      {/* Direction */}
      <div className="flex items-center gap-2">
        {DIRECTIONS.map((d) => (
          <button
            key={d.value}
            onClick={() => setDirection(d.value)}
            className={[
              "rounded border px-3 py-1.5 text-[12px] font-semibold transition-colors",
              direction === d.value
                ? "border-ledger-accent bg-ledger-accentDim text-ledger-ink"
                : "border-ledger-line bg-ledger-panel text-ledger-inkDim hover:text-ledger-ink",
            ].join(" ")}
          >
            {d.label}
          </button>
        ))}
      </div>

      {/* Format fields */}
      <div className="grid grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-[11px] text-ledger-inkDim">
          <span className="uppercase tracking-widest2">
            {direction === "MT_TO_MX" ? "Source (MT)" : "Source (MX)"}
          </span>
          <input
            type="text"
            value={sourceFormat}
            onChange={(e) => setSourceFormat(e.target.value)}
            placeholder={direction === "MT_TO_MX" ? "e.g. MT204" : "e.g. pacs.008.001.08"}
            className="rounded border border-ledger-line bg-ledger-panel px-2 py-1.5 text-[12px] text-ledger-ink outline-none focus:border-ledger-accent"
          />
        </label>
        <label className="flex flex-col gap-1 text-[11px] text-ledger-inkDim">
          <span className="uppercase tracking-widest2">
            {direction === "MT_TO_MX" ? "Target (MX)" : "Target (MT)"}
          </span>
          <input
            type="text"
            value={targetFormat}
            onChange={(e) => setTargetFormat(e.target.value)}
            placeholder={direction === "MT_TO_MX" ? "e.g. pacs.010.001.05" : "e.g. MT204"}
            className="rounded border border-ledger-line bg-ledger-panel px-2 py-1.5 text-[12px] text-ledger-ink outline-none focus:border-ledger-accent"
          />
        </label>
      </div>

      {/* File pickers */}
      <div className="grid grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-[11px] text-ledger-inkDim">
          <span className="uppercase tracking-widest2">Mapping YAML (required)</span>
          <input
            type="file"
            accept=".yaml,.yml"
            onChange={(e) => setMappingFile(e.target.files?.[0] || null)}
            className="text-[11px] text-ledger-inkDim file:mr-2 file:rounded file:border-0 file:bg-ledger-accentDim file:px-2 file:py-1 file:text-ledger-ink"
          />
        </label>
        <label className="flex flex-col gap-1 text-[11px] text-ledger-inkDim">
          <span className="uppercase tracking-widest2">XSD {xsdRequired ? "(required)" : "(optional)"}</span>
          <input
            type="file"
            accept=".xsd"
            onChange={(e) => setXsdFile(e.target.files?.[0] || null)}
            className="text-[11px] text-ledger-inkDim file:mr-2 file:rounded file:border-0 file:bg-ledger-accentDim file:px-2 file:py-1 file:text-ledger-ink"
          />
        </label>
      </div>

      {/* Overwrite / mismatch notice */}
      {showOverwriteNotice && (
        <div className="rounded border border-ledger-amber/50 bg-ledger-amberDim/40 px-3 py-2.5">
          <p className="text-[12px] text-ledger-ink">
            {checkResult.conversion_id
              ? `This will overwrite conversion_id: ${checkResult.conversion_id}`
              : "A mapping already exists for this source/target pair (or the filename doesn't look right) - this will overwrite it."}
          </p>
          <label className="mt-2 flex items-center gap-2 text-[11px] text-ledger-inkDim">
            <input
              type="checkbox"
              checked={confirmOverwrite}
              onChange={(e) => setConfirmOverwrite(e.target.checked)}
            />
            I understand, overwrite it
          </label>
        </div>
      )}

      <button
        onClick={handleSubmit}
        disabled={!canSubmit || (showOverwriteNotice && !confirmOverwrite)}
        className="w-fit rounded bg-ledger-accent px-4 py-1.5 text-[12px] font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-40"
      >
        {status === "submitting" ? "Uploading…" : "Upload mapping"}
      </button>

      {status === "done" && result && (
        <div className="rounded border border-ledger-wire/50 bg-ledger-wireDim/40 px-3 py-2.5">
          <p className="text-[12px] text-ledger-ink">
            Saved {result.source_format} → {result.target_format} (conversion_id: {result.conversion_id})
            {result.overwritten ? " — existing files overwritten." : "."}
          </p>
          {result.warnings && result.warnings.length > 0 && (
            <ul className="mt-1.5 space-y-1">
              {result.warnings.map((w, i) => (
                <li key={i} className="text-[11px] text-ledger-amber">
                  {w}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {status === "error" && <DiagnosticsPanel error={error} />}
    </div>
  );
}
