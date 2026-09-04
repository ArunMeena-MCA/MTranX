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

**Status**: built but **not tested end-to-end** - libpostal could not be
installed in the environment this was developed in (no C compiler toolchain
present, plus the ~2GB model-data download). Follow the steps below and
validate against real address samples before enabling this in production.

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
docker run -d --name mtmx-address-parser -p 8090:8090 mtmx-address-parser
```

Verify it's up:

```bash
curl http://localhost:8090/health
# {"status":"ok"}
```

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

Example (`backend/.env` or your deployment's environment):
```
MTMX_ADDRESS_PARSER_ENABLED=true
MTMX_ADDRESS_PARSER_URL=http://address-parser:8090/parse-address
```

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
