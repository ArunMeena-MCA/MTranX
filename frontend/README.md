# Wire Desk — MT/MX Converter Frontend

React + Tailwind UI for the MT/MX conversion engine. Paste a raw MT or MX
message on the left, pick source/target format, hit **Convert message**
(or Cmd/Ctrl+Enter), and the converted message appears on the right.

## Design notes

- The bar between the controls and the two panels is a real pipeline
  status indicator, not decoration: **Mapping doc → Parse → Convert →
  Validate**. On failure, stages that completed show green, the stage
  that stopped the conversion shows red, and stages after it are dimmed
  as skipped — so you can tell at a glance whether the reference mapping
  document itself was the problem, or the specific input message.
- Errors surface the actual detail the backend returns: for a
  `MappingDocIncompleteError` you get the exact list of what's missing
  from the mapping doc, not a generic failure toast.
- The field trace under the panels (once you're on `Warnings`/trace view)
  shows exactly which source field produced which target value via which
  transformation — this is your main tool for debugging/refining a
  mapping document.

## Running it

1. Start the backend first (see `../backend/`):
   ```bash
   cd .. && uvicorn mt_mx_converter.backend.api:app --reload --port 8000
   ```
2. Then, in this folder:
   ```bash
   npm install
   npm run dev
   ```
3. Open the printed local URL (default `http://localhost:5173`).

If your backend runs somewhere other than `http://localhost:8000`, copy
`.env.example` to `.env.local` and set `VITE_API_BASE_URL`.

## Build for production

```bash
npm run build
```

Outputs to `dist/`. This is a static bundle — serve it from any static
host, and make sure `VITE_API_BASE_URL` (baked in at build time) points
at your deployed backend, with CORS on the backend restricted to your
frontend's real origin (the shipped `backend/api.py` allows `*` for local
dev only).
