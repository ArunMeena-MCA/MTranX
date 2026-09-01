# mtmx-converter (Java / Spring Boot)

MT/MX SWIFT message conversion microservice. Same architecture and
"never guess" philosophy as the previous engine, rebuilt from scratch in
Java: **Prowide Core** parses SWIFT MT messages (replacing the earlier
regex-based parser), a mapping-doc-driven Converter applies field rules,
and a Validator checks the result — with an LLM used only for the narrow,
explicitly-opted-into cases the mapping doc allows.

## ⚠️ Before you build this

I could not reach Maven Central from the sandbox this was written in
(only a fixed domain allow-list was reachable, and it doesn't include
`repo.maven.apache.org`/`search.maven.org`), so **this project has not
been compiled or run** here. Specifically unverified:

1. **The Prowide Core version pinned in `pom.xml`** (`pw-swift-core`,
   property `prowide.core.version`). Check
   <https://mvnrepository.com/artifact/com.prowidesoftware/pw-swift-core>
   for the current version and update the property.
2. **The exact Prowide API surface** used in
   `parser/MtParserService.java` (`SwiftMessage.parse(String)`,
   `SwiftTagListBlock`, `Tag.getName()`/`getValue()`). This is Prowide's
   long-standing public API as I recall it, but I have not compiled
   against the real jar to confirm method signatures for the version you
   pin. Run `mvn compile` and fix any drift before trusting this in
   production.

Everything else (the mapping engine, transformation engine, MX generic
parser/renderer, REST layer) is plain Java/Spring/JDK code with no
similar external-library uncertainty, but likewise has not been
compiled here — **run `mvn compile && mvn test` yourself** and let's fix
whatever comes up together.

## Architecture

```
MappingRegistry.loadRaw
    -> CompletenessAuditor        (deterministic gatekeeper, no LLM)
    -> MtParserService (Prowide)  or  MxParserService (generic DOM)
    -> ConverterService           (deterministic transforms + narrow LLM opt-ins)
    -> ValidatorService           (deterministic checks + LLM semantic audit)
         -> loop back to Converter on CONVERSION_ERROR (bounded retries)
         -> abort with MappingDocIncompleteException on MAPPING_GAP
    -> ConversionResult
```

See `../DOCUMENTATION.md` for the full writeup and module map, and
`mappings/MAPPING_DOC_SPEC.md` for the mapping-document authoring guide
(same schema as before — it's language-agnostic).

## Why Prowide only for MT, not MX

Prowide's ISO 20022 library (`pw-iso20022`) generates one strongly-typed
Java class **per message type** (e.g. a specific class for pacs.008).
That's a great fit when you know exactly which message/version you're
handling at compile time. This engine is designed to support any
conversion pair your `mappings/` folder describes, at runtime, without
adding a Java class per message type — so MX parsing/rendering uses a
generic, namespace-agnostic XML walk instead (`MxParserService`,
`MxRenderer`). Swapping in Prowide's typed classes for a specific,
fixed message pair is a reasonable enhancement later; see
`DOCUMENTATION.md`.

## Running it

```bash
copy .env.example .env
# Choose MTMX_PROVIDER and set the matching API key in .env, then run:
mvn spring-boot:run
```

The service listens on **port 8000** by default (see
`src/main/resources/application.yml`) specifically so the existing React
frontend — which defaults to `http://localhost:8000` — needs no changes.

Then, in `../frontend`:

```bash
npm install
npm run dev
```

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8000` | HTTP port |
| `MTMX_MAPPINGS_DIR` | `./mappings` | Folder of `<SRC>_TO_<TGT>.yaml` mapping docs |
| `MTMX_XSD_DIR` | (unset) | Folder of official ISO 20022 `.xsd` files for real schema validation |
| `MTMX_PROVIDER` | `gemini` | LLM provider: `gemini` or `groq` |
| `MTMX_MODEL` | `gemini-3.6-flash` | Model used for the narrow LLM-assisted steps; use `openai/gpt-oss-20b` with Groq |
| `GEMINI_API_KEY` | (unset) | Gemini key, used when `MTMX_PROVIDER=gemini` |
| `GROQ_API_KEY` | (unset) | Groq key, used when `MTMX_PROVIDER=groq` |

## Tests

`src/test/java` has JUnit 5 unit tests for `CompletenessAuditor` and
`TransformationEngine` (pure Java, no Spring context, no Prowide, no
network) — these are the pieces I have the highest confidence in. Same
caveat as above: written but not executed here; run `mvn test`.
