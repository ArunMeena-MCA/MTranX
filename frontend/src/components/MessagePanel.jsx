export default function MessagePanel({
  eyebrow,
  formatBadge,
  value,
  onChange,
  readOnly = false,
  placeholder,
  emptyState,
  onCopy,
}) {
  return (
    <div className="flex h-full flex-col bg-ledger-panel">
      <div className="flex items-center justify-between border-b border-ledger-line px-4 py-2.5">
        <div className="flex items-baseline gap-2">
          <span className="text-[10px] font-display font-semibold uppercase tracking-widest2 text-ledger-inkDim">
            {eyebrow}
          </span>
          {formatBadge && (
            <span className="rounded border border-ledger-line px-1.5 py-0.5 text-[10px] font-medium text-ledger-ink">
              {formatBadge}
            </span>
          )}
        </div>
        {readOnly && value && (
          <button
            onClick={onCopy}
            className="text-[10px] uppercase tracking-widest2 text-ledger-inkDim hover:text-ledger-accent transition-colors"
          >
            Copy
          </button>
        )}
      </div>

      <div className="relative flex-1">
        {readOnly ? (
          value ? (
            <pre className="pane-scroll absolute inset-0 overflow-auto whitespace-pre-wrap break-words p-4 text-[13px] leading-relaxed">
              {value}
            </pre>
          ) : (
            <div className="absolute inset-0 flex items-center justify-center px-8 text-center text-sm text-ledger-inkDim">
              {emptyState}
            </div>
          )
        ) : (
          <textarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            spellCheck={false}
            className="pane-scroll absolute inset-0 h-full w-full resize-none bg-transparent p-4 text-[13px] leading-relaxed text-ledger-ink outline-none placeholder:text-ledger-inkDim/60"
          />
        )}
      </div>
    </div>
  );
}
