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

/** GET /api/mappings/check - cheap existence preview, no file upload. */
export async function checkMappingExists({ sourceFormat, targetFormat }) {
  const params = new URLSearchParams({ source_format: sourceFormat, target_format: targetFormat });
  const res = await fetch(`${API_BASE}/api/mappings/check?${params}`);
  if (!res.ok) {
    throw new Error("Could not check existing mappings.");
  }
  return res.json();
}

/**
 * POST /api/mappings/upload - registers a new (or overwrites an existing)
 * MT<->MX conversion. Same structured-error shape as convertMessage() on
 * failure, including the "upload" stage / MappingUploadConflictException
 * case that means "resubmit with confirm: true".
 */
export async function uploadMapping({
  direction,
  sourceFormat,
  targetFormat,
  mappingFile,
  xsdFile,
  confirm,
}) {
  const formData = new FormData();
  formData.append("direction", direction);
  formData.append("source_format", sourceFormat);
  formData.append("target_format", targetFormat);
  formData.append("mapping_file", mappingFile);
  if (xsdFile) {
    formData.append("xsd_file", xsdFile);
  }
  formData.append("confirm", confirm ? "true" : "false");

  // Deliberately no Content-Type header - fetch sets the multipart
  // boundary itself from the FormData body; setting it manually breaks it.
  const res = await fetch(`${API_BASE}/api/mappings/upload`, {
    method: "POST",
    body: formData,
  });

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    const detail = body?.detail || {};
    const err = new Error(detail.message || "Upload failed.");
    err.stage = detail.stage || "unknown";
    err.errorType = detail.error_type || "Error";
    err.missing = detail.missing || null;
    err.errors = detail.errors || null;
    err.warnings = detail.warnings || null;
    throw err;
  }

  return body;
}
