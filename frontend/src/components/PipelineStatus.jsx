const STAGES = [
  { key: "mapping", label: "Mapping doc" },
  { key: "parse", label: "Parse" },
  { key: "convert", label: "Convert" },
  { key: "validate", label: "Validate" },
];

/**
 * status: "idle" | "running" | "done" | "error"
 * failedStage: which stage key failed, if any (stages after it are "skipped")
 */
export default function PipelineStatus({ status, failedStage, steps }) {
  const stepByKey = new Map((steps || []).map((step) => [step.key, step]));

  const stageState = (key, index) => {
    const backendStep = stepByKey.get(key);
    if (backendStep?.status) return backendStep.status;
    if (status === "idle") return "pending";
    if (status === "running") return "running"; // we don't have real per-stage timing, so all stages
    // show the same in-flight state during the request rather than faking granularity.
    const failedIndex = failedStage ? STAGES.findIndex((s) => s.key === failedStage) : -1;
    if (status === "done") return "done";
    if (status === "error") {
      if (failedIndex === -1) return "pending";
      if (index < failedIndex) return "done";
      if (index === failedIndex) return "error";
      return "skipped";
    }
    return "pending";
  };

  return (
    <div className="relative flex items-center justify-center gap-0 py-3">
      {status === "running" && (
        <div className="absolute inset-x-6 top-0 h-px overflow-hidden">
          <div className="h-px w-1/3 bg-ledger-accent animate-scan" />
        </div>
      )}
      {STAGES.map((stage, i) => {
        const state = stageState(stage.key, i);
        const detail = stepByKey.get(stage.key)?.message;
        return (
          <div key={stage.key} className="flex items-start">
            {i > 0 && (
              <div
                className={[
                  "h-px w-8 sm:w-12",
                  state === "pending" ? "bg-ledger-line" : "bg-ledger-line",
                ].join(" ")}
              />
            )}
            <div className="flex max-w-32 flex-col items-center gap-1.5 px-1 text-center">
              <Dot state={state} />
              <span
                className={[
                  "text-[10px] uppercase tracking-widest2 whitespace-nowrap",
                  state === "error" ? "text-ledger-alarm" : "text-ledger-inkDim",
                ].join(" ")}
              >
                {stage.label}
              </span>
              {detail && (
                <span className="max-w-32 text-[10px] leading-tight text-ledger-alarm">
                  {detail}
                </span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function Dot({ state }) {
  const base = "h-2.5 w-2.5 rounded-full border";
  if (state === "done") {
    return <div className={`${base} bg-ledger-wire border-ledger-wire`} />;
  }
  if (state === "error") {
    return <div className={`${base} bg-ledger-alarm border-ledger-alarm`} />;
  }
  if (state === "running") {
    return (
      <div className={`${base} border-ledger-accent bg-ledger-accent/40 animate-pulse`} />
    );
  }
  if (state === "skipped") {
    return <div className={`${base} border-ledger-line bg-transparent opacity-50`} />;
  }
  return <div className={`${base} border-ledger-line bg-transparent`} />;
}
