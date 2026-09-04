const STAGE_HEADLINES = {
  mapping: "Mapping document incomplete",
  parse: "Couldn't read this message",
  convert: "Conversion stopped",
  validate: "Validation failed",
  upload: "Mapping upload failed",
  unknown: "Conversion failed",
};

export default function DiagnosticsPanel({ error, warnings, auditWarnings, fieldTrace }) {
  if (error) {
    return (
      <div className="border-t border-ledger-line bg-ledger-alarmDim/40 px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="h-1.5 w-1.5 rounded-full bg-ledger-alarm" />
          <h3 className="text-sm font-display font-semibold text-ledger-ink">
            {STAGE_HEADLINES[error.stage] || STAGE_HEADLINES.unknown}
          </h3>
          <span className="text-[10px] uppercase tracking-widest2 text-ledger-inkDim">
            {error.errorType}
          </span>
        </div>
        <p className="mt-1.5 whitespace-pre-wrap text-[13px] text-ledger-ink/90">{error.message}</p>

        {error.missing && error.missing.length > 0 && (
          <ul className="mt-2 space-y-1 border-l-2 border-ledger-alarm/50 pl-3">
            {error.missing.map((m, i) => (
              <li key={i} className="text-[12px] text-ledger-inkDim">
                {m}
              </li>
            ))}
          </ul>
        )}

        {error.errors && error.errors.length > 0 && (
          <ul className="mt-2 space-y-1 border-l-2 border-ledger-alarm/50 pl-3">
            {error.errors.map((m, i) => (
              <li key={i} className="text-[12px] text-ledger-inkDim">
                {m}
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  const hasWarnings = (warnings && warnings.length > 0) || (auditWarnings && auditWarnings.length > 0);

  if (!hasWarnings && (!fieldTrace || fieldTrace.length === 0)) {
    return null;
  }

  return (
    <div className="max-h-56 overflow-auto border-t border-ledger-line pane-scroll">
      {hasWarnings && (
        <div className="border-b border-ledger-line bg-ledger-amberDim/30 px-4 py-2.5">
          <h3 className="text-[10px] font-display font-semibold uppercase tracking-widest2 text-ledger-amber">
            Warnings
          </h3>
          <ul className="mt-1.5 space-y-1">
            {[...(warnings || []), ...(auditWarnings || [])].map((w, i) => (
              <li key={i} className="text-[12px] text-ledger-inkDim">
                {w}
              </li>
            ))}
          </ul>
        </div>
      )}

      {fieldTrace && fieldTrace.length > 0 && (
        <div className="px-4 py-2.5">
          <h3 className="text-[10px] font-display font-semibold uppercase tracking-widest2 text-ledger-inkDim">
            Field trace
          </h3>
          <table className="mt-1.5 w-full text-[12px]">
            <tbody>
              {fieldTrace.map((row, i) => (
                <tr key={i} className="border-t border-ledger-line/60">
                  <td className="py-1 pr-3 text-ledger-inkDim">{row.source_field}</td>
                  <td className="py-1 pr-3 text-ledger-ink">{row.target_path}</td>
                  <td className="py-1 pr-3 text-ledger-inkDim">{row.method}</td>
                  <td className="py-1 text-ledger-wire">
                    {typeof row.result === "object" ? JSON.stringify(row.result) : String(row.result)}
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
