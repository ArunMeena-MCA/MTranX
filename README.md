# MT/MX Wire Desk

A microservice that converts SWIFT MT messages to ISO 20022 MX messages
(and back), guided entirely by a reference mapping document per
conversion direction — and refuses to guess when that document doesn't
say what to do.

This revision replaces the earlier Python engine's backend with a **Java
/ Spring Boot microservice that uses Prowide Core (open source) to parse
SWIFT MT messages**, instead of hand-rolled regex. The React + Tailwind
frontend is unchanged — same UI, same API contract, just talking to a
different backend.

```
mtmx-java/
  backend/     Java/Spring Boot microservice (this revision's new work)
  frontend/    React + Tailwind UI (untouched, copied as-is)
  DOCUMENTATION.md   Full architecture writeup
```

## ⚠️ Read this first

I built and reviewed this Java project carefully, but **could not compile
or run it** in the sandbox this was written in — that sandbox can only
reach a fixed list of domains, and Maven Central isn't one of them. That
means:

- `pom.xml`'s Prowide Core version is a placeholder marked for
  verification, not a confirmed-current version.
- The Prowide API calls in `MtParserService.java` are written from my
  best recollection of Prowide's long-standing public API, not confirmed
  against a real compile.
- None of the Java code has been run against real Prowide/Spring jars.

Everything else about the design (the mapping-doc engine, completeness
auditing, transformation engine, MX generic XML handling, the REST
contract matching the existing frontend) is ordinary Java/Spring/JDK code
I'm confident in, but likewise untested here. **Please run `mvn compile`
and `mvn test` first and tell me what breaks** — that's expected, and
straightforward to fix once we can see real compiler output.

## Quick start

```bash
# Terminal 1 - backend
cd backend
# Copy .env.example to .env and set GEMINI_API_KEY in .env
mvn spring-boot:run

# Terminal 2 - frontend (unchanged)
cd frontend
npm install
npm run dev
```

Backend listens on port 8000 by default so the frontend's existing
`http://localhost:8000` default just works.

The backend supports Gemini and Groq for its narrow LLM-assisted conversion
steps. Choose a provider and set its API key in `backend/.env`:

```env
MTMX_PROVIDER=groq
MTMX_MODEL=openai/gpt-oss-20b
GROQ_API_KEY=your-groq-api-key
# Or use Gemini:
# MTMX_PROVIDER=gemini
# MTMX_MODEL=gemini-3.6-flash
# GEMINI_API_KEY=your-gemini-api-key
```

`backend/.env` is ignored by Git. Use `backend/.env.example` as the
configuration template.

See `backend/README.md` for configuration and `DOCUMENTATION.md` for the
architecture writeup, and `backend/mappings/MAPPING_DOC_SPEC.md` for the
mapping-document authoring guide.
