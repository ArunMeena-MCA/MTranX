const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

export async function fetchMappings() {
  const res = await fetch(`${API_BASE}/api/mappings`);
  if (!res.ok) {
    throw new Error("Can't reach the conversion engine at " + API_BASE);
  }
  return res.json();
}

/**
 * Calls POST /api/convert. On failure, throws an Error carrying the
 * structured detail the backend attaches (stage, errorType, missing,
 * errors, warnings) so the UI can point at the exact pipeline stage
 * that stopped the conversion, instead of a generic failure message.
 */
export async function convertMessage({ rawText, sourceFormat, targetFormat }) {
  const res = await fetch(`${API_BASE}/api/convert`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      raw_text: rawText,
      source_format: sourceFormat,
      target_format: targetFormat,
    }),
  });

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    const detail = body?.detail || {};
    const err = new Error(detail.message || "Conversion failed.");
    err.stage = detail.stage || "unknown";
    err.errorType = detail.error_type || "Error";
    err.missing = detail.missing || null;
    err.errors = detail.errors || null;
    err.warnings = detail.warnings || null;
    err.pipelineSteps = detail.pipeline_steps || null;
    throw err;
  }

  return body;
}
