const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

/**
 * GET /api/flexcube/status - the FLEXCUBE dashboard's single polling endpoint (see
 * pages/FlexcubeDashboard.jsx). Returns the current job state, per-MT-type counters, recent
 * failures, and a recent-events log - see FlexcubeDashboardController/FlexcubeStatsService on the
 * backend for the exact shape. Throws on network failure so the page can show "engine
 * unreachable" the same way the rest of this app does.
 */
export async function fetchFlexcubeStatus() {
  const res = await fetch(`${API_BASE}/api/flexcube/status`);
  if (!res.ok) {
    throw new Error("Can't reach the conversion engine at " + API_BASE);
  }
  return res.json();
}

async function postFlexcubeAction(action) {
  const res = await fetch(`${API_BASE}/api/flexcube/${action}`, { method: "POST" });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(body?.message || `Request failed (${action}).`);
  }
  return body;
}

export const pauseFlexcube = () => postFlexcubeAction("pause");
export const resumeFlexcube = () => postFlexcubeAction("resume");
export const stopFlexcube = () => postFlexcubeAction("stop");
export const runNowFlexcube = () => postFlexcubeAction("run-now");

/**
 * GET /api/flexcube/conversions?page=&size= - paginated, newest-first list of successful
 * conversions (lightweight rows only, no MT/MX content - see fetchFlexcubeConversionDetail for
 * that). Returns { items, page, size, total }.
 */
export async function fetchFlexcubeConversions({ page = 0, size = 25 } = {}) {
  const res = await fetch(`${API_BASE}/api/flexcube/conversions?page=${page}&size=${size}`);
  if (!res.ok) {
    throw new Error("Could not load the conversions list.");
  }
  return res.json();
}

/** GET /api/flexcube/conversions/{id} - the full MT (source) + MX (converted) text for one row, fetched only when clicked. */
export async function fetchFlexcubeConversionDetail(id) {
  const res = await fetch(`${API_BASE}/api/flexcube/conversions/${id}`);
  if (!res.ok) {
    throw new Error(res.status === 404 ? "This conversion is no longer available." : "Could not load this conversion.");
  }
  return res.json();
}

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

/**
 * GET /api/automated/messages - pulls the automated dashboard's fixed batch of real MT103
 * messages from Oracle. Same structured-error shape as convertMessage() on failure (stage
 * "fetch" specifically means "Oracle isn't configured / unreachable").
 */
export async function fetchAutomatedMessages() {
  const res = await fetch(`${API_BASE}/api/automated/messages`);
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    const detail = body?.detail || {};
    const err = new Error(detail.message || "Could not fetch messages from Oracle.");
    err.stage = detail.stage || "unknown";
    err.errorType = detail.error_type || "Error";
    throw err;
  }
  return body;
}

/**
 * POST /api/automated/convert - converts one fetched MT103 message (by reference number) and
 * archives it server-side to backend/sample/<reference_no>.md. Same structured-error shape as
 * convertMessage().
 */
export async function convertAutomatedMessage({ referenceNo, message }) {
  const res = await fetch(`${API_BASE}/api/automated/convert`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reference_no: referenceNo, message }),
  });

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    const detail = body?.detail || {};
    const err = new Error(detail.message || "Conversion failed.");
    err.stage = detail.stage || "unknown";
    err.errorType = detail.error_type || "Error";
    err.errors = detail.errors || null;
    err.warnings = detail.warnings || null;
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
