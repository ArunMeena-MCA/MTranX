# Architecture — Java / Prowide Revision

## What changed from the Python engine

| Aspect | Previous (Python) | This revision (Java) |
|---|---|---|
| MT parsing | Hand-rolled regex over block-4 text | **Prowide Core** (`SwiftMessage`/`SwiftTagListBlock`/`Tag`) |
| MX parsing | lxml, generic DOM walk | Generic JAXP DOM walk (same approach, different library) |
| Mapping engine | Pydantic + PyYAML, same schema | Jackson + SnakeYAML-backed YAML mapper, **same schema** |
| Completeness auditor | Deterministic Python function | Deterministic Java class, same checks |
| Transformation engine | Deterministic Python functions | Deterministic Java class, same operations |
| LLM-assisted paths | Anthropic Python SDK | Raw `java.net.http.HttpClient` calling the Messages API directly |
| REST layer | FastAPI | Spring Boot (`@RestController`) |
| Frontend | React + Tailwind | **Unchanged** |

The mapping-document **schema is identical** — it's just YAML, read the
same way regardless of implementation language. `MT103_TO_PACS008.yaml`
was copied over verbatim from the earlier project.

## Why Prowide, and why only for MT

You asked specifically for an open-source library to handle MT/MX
parsing rather than hand-rolled parsing logic. **Prowide Core**
(`com.prowidesoftware:pw-swift-core`) is a mature, widely-used open
source library for exactly this: it understands SWIFT's block structure,
field continuation lines, and repeated-field sequences correctly, which
a regex can only approximate. `MtParserService` uses it to turn raw MT
text into a `SwiftMessage`, then walks `block4.getTags()` to get exact
tag/value pairs.

For **MX**, Prowide also publishes `pw-iso20022`, but that library
generates one strongly-typed Java class *per specific message* (e.g. a
dedicated class for pacs.008.001.08). That's the right tool when you know
exactly which message and version you're handling at compile time — but
this engine is built to support any conversion pair described by
whatever's in your `mappings/` folder, at runtime, without a Java class
per message type. So `MxParserService`/`MxRenderer` use a generic,
namespace-agnostic XML walk instead — same conceptual approach as the
earlier Python engine's lxml-based parser, just implemented with the
JDK's built-in `javax.xml` APIs (no extra dependency needed).

**If you have a small, fixed set of MX message types** you always
convert to/from, wiring Prowide's typed classes in for those specific
pairs is a worthwhile follow-up: you'd get compile-time-checked field
access and, more importantly, automatic structural validation against
the exact bundled XSD for that message, for free. That would replace
`MxParserService`/`MxRenderer` for those specific conversion IDs while
leaving the generic path available for everything else. Flagging this
as a deliberate scope decision rather than an oversight — happy to build
it once you tell me which specific message types are worth the
per-type investment.

## Pipeline (unchanged in spirit from the Python engine)

```
raw MT/MX text
      │
      ▼
MappingRegistry            loads <SRC>_TO_<TGT>.yaml
      │
      ▼
CompletenessAuditor         deterministic, no LLM - refuses to proceed
      │                     if the mapping doc itself has gaps
      ▼
MtParserService (Prowide)   or   MxParserService (generic DOM)
      │
      ▼
ConverterService            deterministic transforms; LLM only for
      │                     fields explicitly marked llm_assisted or
      │                     as a decompose_party fallback
      ▼
ValidatorService            deterministic checks (mandatory fields,
      │                     length/pattern, charset, XSD if configured,
      │                     mapping doc's validation_rules) + an
      │                     independent LLM semantic audit
      ▼
 valid? ──yes──▶ ConversionResult (rendered output + field trace)
    │no
    ▼
CONVERSION_ERROR ──▶ retry (bounded by mtmx.max-converter-retries)
MAPPING_GAP       ──▶ MappingDocIncompleteException immediately
```

## REST contract (kept identical on purpose)

So the existing frontend needs zero changes:

- `GET /api/health` → `{"status": "ok"}`
- `GET /api/mappings` → `[{"conversion_id", "source_format", "target_format"}, ...]`
- `POST /api/convert` body `{"raw_text", "source_format", "target_format"}` →
  on success, `{"rendered_output", "validation_warnings", "audit_warnings",
  "attempts", "parsed_source_fields", "converted_tree", "field_trace"}`;
  on failure, HTTP 422 with `{"detail": {"error_type", "stage", "message",
  ["missing" | "errors"/"warnings"]}}`.

`GlobalExceptionHandler` maps every engine exception to this shape,
matching exactly what `frontend/src/lib/api.js` already parses (built in
the previous revision, unmodified here) and what `PipelineStatus.jsx`
uses to highlight which stage failed.

## Module map

| Package | Responsibility |
|---|---|
| `config` | `AppProperties` (all knobs), CORS |
| `exception` | The exception hierarchy — every one represents "refused to guess" |
| `mapping.model` | POJOs mirroring the mapping-doc YAML schema |
| `mapping` | `MappingRegistry` (loader), `CompletenessAuditor` (gatekeeper) |
| `parser` | `MtParserService` (Prowide), `MxParserService` (generic DOM), `DecompositionService` |
| `llm` | `AnthropicClient` - the only place that talks to the Anthropic API |
| `transform` | `TransformationEngine` - deterministic field transforms |
| `convert` | `ConverterService`, `MtRenderer`, `MxRenderer` |
| `validate` | `ValidatorService`, `ValidationReport` |
| `orchestrate` | `ConversionOrchestrator` - owns the retry/abort policy |
| `web` | `ConversionController`, DTOs, `GlobalExceptionHandler` |

## Known gaps / things to verify (carried over discipline from the Python engine)

- **MT rendering** (`MtRenderer`) only emits a bare block-4 payload
  (`{4: ... -}`), same simplification the Python engine used. A fully
  valid FIN message needs block 1/2 headers (sender/receiver BIC,
  session/sequence numbers) the mapping doc doesn't currently supply.
  Worth building with Prowide's `SwiftWriter`/block builders once that
  data is available.
- **XSD validation is opt-in** via `mtmx.xsd-dir`; without it, every MX
  conversion emits an explicit warning rather than a silent pass — same
  as before.
- **The Prowide version and exact API calls in `MtParserService` are
  unverified** — see the top-level README's warning. This is the
  highest-priority thing to confirm before trusting this in production.
- **The sample `MT103_TO_PACS008.yaml`** is still the same illustrative,
  self-declared-unverified document from the earlier revision — it
  exercises the pipeline but is not an authoritative SWIFT translation
  table. See `backend/mappings/MAPPING_DOC_SPEC.md`.
