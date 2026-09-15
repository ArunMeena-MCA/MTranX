# MT103 Address Parser Sidecar

Splits free-text address lines (e.g. MT103 field `50K`/bare `59`'s unstructured
content) into `street` / `city` / `postcode` / `country_code`, using
[libpostal](https://github.com/openvenues/libpostal) - a statistical NLP model
trained on real-world postal address data (OpenStreetMap + OpenAddresses), not
a hand-written heuristic.

**Why this exists**: from 14 November 2026, Swift requires "hybrid" addresses
for in-scope cross-border payment messages - structured `PostalAddress24`
fields (`StrtNm`/`TwnNm`/`Ctry`) populated *alongside* the existing free-text
`AdrLine`, not instead of it. This service enriches the converter's output to
meet that requirement, without changing default behavior for anyone who
doesn't enable it. See `MT103_TO_PACS00800108.yaml`'s `ADDRESS POLICY` note
(under `scope_notes`) for the full policy context, and `app.py`'s module
docstring for the confidence-gating design.

**Status**: tested end-to-end (2026-09-15) against the live Java backend + a
real Docker container - confidence gating, country resolution (including the
libpostal-doesn't-label-it raw-text-scan fallback), and the fail-soft path
have all been verified with real conversions, not just this service in
isolation. Still validate against your own hard address samples before
relying on it in production - "tested" here means the mechanism works
correctly, not that every real-world address shape has been tried.

---

## Option A: Docker (recommended)

This is the easiest path since it handles compiling libpostal from source for
you - there's no prebuilt libpostal package for most platforms.

```bash
cd address-parser-service
docker build -t mtmx-address-parser .
```

**Budget real time for this build.** It compiles a C library from source and
downloads libpostal's ~2GB trained-model data during `make install` - this is
not a quick `pip install`, expect it to take a while depending on your machine
and network speed.

Run it:

```bash
docker run -d --name mtmx-address-parser -p 8090:8090 --restart unless-stopped mtmx-address-parser
```

Verify it's up:

```bash
curl http://localhost:8090/health
# {"status":"ok"}
```

**If you edit `app.py`, the running container does NOT see the change.**
`COPY app.py .` bakes the file into the image at build time - editing the
file on disk does nothing until you rebuild the image AND recreate the
container from it:

```bash
docker build -t mtmx-address-parser .
docker stop mtmx-address-parser && docker rm mtmx-address-parser
docker run -d --name mtmx-address-parser -p 8090:8090 --restart unless-stopped mtmx-address-parser
```

(The rebuild is fast after the first time - libpostal's own build/data-download
layers stay cached; only the final `COPY app.py .` layer re-runs.) This bit us
for real once already: a country-resolution fix was written to `app.py`,
verified by direct script execution, and documented as done - but the
*running* container was never rebuilt, so it kept serving the old behavior
for weeks until a live conversion surfaced the mismatch. If structured
address fields aren't showing up the way a recent `app.py` change says they
should, check `docker inspect mtmx-address-parser --format '{{.Created}}'`
against the file's last edit time before assuming the Java side is wrong.

### Rebuilding without re-downloading the model data

The Dockerfile's builder stage downloads libpostal's data into
`/opt/libpostal_data` inside the image. If you expect to rebuild this image
often (e.g. while iterating on `app.py`), consider mounting a named volume at
that path in a docker-compose setup so the ~2GB download only happens once:

```yaml
# docker-compose.yml (optional, not included by default)
services:
  address-parser:
    build: ./address-parser-service
    ports:
      - "8090:8090"
    volumes:
      - libpostal-data:/opt/libpostal_data
volumes:
  libpostal-data:
```

---

## Option B: Run locally without Docker

Only do this if you already have (or are prepared to install) a C build
toolchain and can spare the disk space/time for libpostal's data.

1. Install libpostal itself, following its own documented steps:
   <https://github.com/openvenues/libpostal#installation-maclinux>
   (on Windows, use WSL2 - libpostal's build process assumes a Linux-like
   toolchain and this project's Dockerfile is Linux-based for the same reason)

2. Install the Python dependencies:
   ```bash
   cd address-parser-service
   pip install -r requirements.txt
   ```
   `pip install postal` (pulled in via `requirements.txt`) links against the
   libpostal shared library installed in step 1 - if step 1 wasn't done first,
   this step will fail to build, the same way it failed in this project's own
   development environment.

3. Run the service:
   ```bash
   uvicorn app:app --host 0.0.0.0 --port 8090
   ```

---

## Verify it actually parses addresses correctly

```bash
curl -X POST http://localhost:8090/parse-address \
  -H "Content-Type: application/json" \
  -d '{"lines": ["XYZ Street", "Chennai India"]}'
```

Expected shape:
```json
{
  "street": "XYZ Street",
  "city": "Chennai",
  "postcode": null,
  "country_code": "IN",
  "confident": true
}
```

**Test it against your own real, hard cases before trusting it** - not just
the happy path above. In particular, try the genuinely ambiguous address
shapes this service was built to handle carefully, e.g.:

```bash
curl -X POST http://localhost:8090/parse-address \
  -H "Content-Type: application/json" \
  -d '{"lines": ["ABC Street", "Chennai", "India New Delhi"]}'
```

If `confident` comes back `false` (or the country looks wrong), that's the
system working as designed - the Java caller will skip structured enrichment
for that message and fall back to `AdrLine`-only, exactly like today. It is
NOT expected to correctly resolve every ambiguous address; it's expected to
know when *not* to guess.

---

## Wiring this into the backend

This service is **opt-in** - the Spring Boot backend does nothing with it
until you turn it on.

### 1. Point the backend at this service

Set these on the backend (env vars, or the matching `mtmx.*` keys in
`application.yml`):

| Env var | Default | Purpose |
|---|---|---|
| `MTMX_ADDRESS_PARSER_ENABLED` | `false` | Master switch. Leave `false` until you've validated this service against real address samples. |
| `MTMX_ADDRESS_PARSER_URL` | `http://localhost:8090/parse-address` | Where the backend sends parse requests. Update if this service isn't running on the same host as the backend (e.g. a separate container/host). |

Example (`backend/.env` or your deployment's environment) - assumes the
backend runs as a plain process on the SAME machine as the `docker run`
container above, reaching it via the host-exposed port:
```
MTMX_ADDRESS_PARSER_ENABLED=true
MTMX_ADDRESS_PARSER_URL=http://localhost:8090/parse-address
```

**Only use a `http://address-parser:8090/...`-style hostname if the backend
itself is ALSO running as a container on the same docker-compose network**
(the `address-parser` name would then resolve via Docker's internal DNS) -
this is NOT the setup Option A above describes, and using that hostname when
the backend runs directly on the host (as it does throughout this project)
silently breaks the connection: `MTMX_ADDRESS_PARSER_ENABLED=true` looks
correctly configured, but every call fails and falls back to `AdrLine`-only,
with no obvious error unless you check the backend's own logs. This exact
mismatch happened once already in this project's own `.env`.

### 2. Nothing else to configure in the mapping doc

`MT103_TO_PACS00800108.yaml`'s `50K` and bare `59` entries already have a
`structured_address` block wired up (v2.14) - once the two env vars above are
set and this service is running, those two fields will automatically start
populating `StrtNm`/`TwnNm`/`PstCd`/`Ctry` alongside their existing `AdrLine`
output, with no further yaml changes needed. `50F`/`59F` are untouched by
this service on purpose - they already extract `Ctry` unambiguously from
their own numbered-line format.

### 3. Restart the backend

Spring Boot reads `mtmx.address-parser-enabled`/`mtmx.address-parser-url` at
startup (`AppProperties`) - a running instance won't pick up a changed env var
without a restart.

---

## What "fails soft" means in practice

If this service is down, slow, or returns something the backend can't parse,
`AddressParserClient` logs a warning and the conversion proceeds with
`AdrLine`-only output - it never fails an otherwise-successful conversion.
Check the backend logs (`com.wiredesk.mtmx.address.AddressParserClient`) if
you've enabled this and structured fields aren't showing up as expected; that
log line will tell you whether this service was even reachable.
