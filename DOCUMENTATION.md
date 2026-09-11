# MTMX Converter — Complete Guide

## 1. Introduction — What Is This Application?

Banks send payment instructions to each other using SWIFT messages. For decades, the standard format for an international payment instruction has been something called **MT103** — a compact, tag-based text format (you'll see things like `:50K:` or `:59:` in it).

The banking industry is moving to a new, richer, XML-based standard called **ISO 20022**. The equivalent of an MT103 in this new world is called **pacs.008**.

**This application is a translator.** You give it a raw MT103 message, and it converts it into a valid pacs.008 XML message — the new format banks are expected to use going forward. It also supports the mapping in the other direction (MT202 → pacs.009, a similar message used for bank-to-bank transfers), and the underlying engine is built so that *any* MT ↔ MX pair can be added later just by uploading a new mapping rulebook — no new programming required.

**Who is this for?** Banks, payment processors, or anyone who needs to convert old-format SWIFT payment messages into the new ISO 20022 format reliably, with a clear paper trail of exactly what changed and why — and with built-in checks that refuse to guess when something doesn't clearly follow the rules.

**The core design promise:** *never invent an answer.* If a field's meaning is genuinely ambiguous, or a rule isn't clearly documented, the app is built to say "I don't know, here's why" rather than silently making something up. This matters enormously for payments — a wrong guess in a payment message can mean money going to the wrong place.

---

## 2. How It Works — The Workflow in Simple Steps

Think of converting one message as passing it through four checkpoints, one after another. If a checkpoint finds a real problem, the process stops there and tells you exactly what went wrong and why — it does not produce a "maybe it's fine" output.

```
  Your MT103 text
        │
        ▼
 ┌──────────────────────┐
 │ 1. Rulebook Check    │   Is the conversion rulebook itself complete and sane?
 └──────────┬───────────┘
        ▼
 ┌──────────────────────┐
 │ 2. Read the Message  │   Break the raw MT103 text into individual tagged fields
 └──────────┬───────────┘
        ▼
 ┌──────────────────────┐
 │ 3. Convert           │   Apply the rulebook, field by field, to build the pacs.008
 └──────────┬───────────┘
        ▼
 ┌──────────────────────┐
 │ 4. Validate          │   Double-check the result against SWIFT's own rules
 └──────────┬───────────┘
        ▼
  Your pacs.008 XML  (+ a full "what happened" report)
```

In slightly more detail:

1. **Rulebook check (no guessing about the rulebook itself).** Before touching your message at all, the app makes sure the mapping rulebook it's about to use is internally consistent — every mandatory piece is there, nothing contradicts itself. This never involves AI; it's a pure "is this document well-formed" check.
2. **Reading the message.** The raw MT103 text is split into its individual fields (tag `20`, tag `50K`, tag `59`, and so on) using a purpose-built SWIFT-format reader, not a hand-rolled guess at the format.
3. **Converting.** For every field the rulebook knows about, the app applies the documented rule — copy a value straight across, reformat a date, split a multi-line address into name/street/city, look up a code in a translation table, and so on. Most of this is 100%-deterministic (exact, repeatable, no AI). A small, clearly-scoped set of fields — mostly free-text fields where SWIFT's format allows real-world variety — get help from an AI model, but only in a narrow, supervised way (see section 5).
4. **Validating.** The freshly-built pacs.008 is checked twice: once by hard-coded rules taken directly from SWIFT's own published standards, and once more by an independent AI reviewer whose only job is to sanity-check the first pass. If either check finds a real, blocking problem, the conversion is reported as failed with the exact reason — it is never silently "fixed" or hidden.
5. **Result.** You get back the finished XML, plus a full trace of every field that was touched, any warnings that aren't serious enough to block the conversion, and a step-by-step "pipeline status" showing exactly which of the four checkpoints passed.

---

## 3. The MT103 → pacs.008 Field Mapping Table

This is the actual rulebook the engine follows today (from `backend/mappings/MT103_TO_PACS00800108.yaml`, version 2.34). Every MT103 field the engine understands is listed below, grouped by topic, with a plain-English description of where its content ends up in the pacs.008 message.

> **Reading the "Where it goes" column:** these are the technical XML element paths (e.g. `CdtTrfTxInf.Dbtr.Nm` means "inside the transaction, under Debtor, the Name field"). You don't need to memorize these — they're here so a technical reader can cross-reference the exact rule.

### 3.1 Message identity

| MT103 Tag | What it means | Where it goes in pacs.008 |
|---|---|---|
| `20` | Sender's own reference number | Message ID **and** the transaction's own Instruction ID |
| Block 3, tag `121` | Unique End-to-end Transaction Reference (a special tracking ID) | `UETR` |
| *(generated)* | — | A fresh creation timestamp and "number of transactions = 1" are generated automatically |

### 3.2 Amount, date, and currency

| MT103 Tag | What it means | Where it goes in pacs.008 |
|---|---|---|
| `32A` | Value date | Settlement Date |
| `32A` | Settlement currency | Settlement Amount's currency |
| `32A` | Settlement amount | Settlement Amount |
| `33B` | Instructed currency | Instructed Amount's currency (only when it differs from `32A`) |
| `33B` | Instructed amount | Instructed Amount |
| `36` | Exchange rate | Exchange Rate |

### 3.3 Ordering Customer (the person/company sending the money) — field 50

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `50A` | BIC-only version of the ordering customer | Debtor's BIC identifier, plus account (IBAN or other) |
| `50F` | Structured version (numbered lines: name, address, country, etc.) | Debtor's name, address, country, town — fully structured |
| `50K` | Free-text version (account, then name/address lines) | Debtor's name and address lines, plus account |

### 3.4 Beneficiary Customer (who receives the money) — field 59

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `59A` | BIC-only version of the beneficiary | Creditor's BIC identifier, plus account |
| `59` (no letter) | Free-text name/address version | Creditor's name and address lines, plus account |
| `59F` | Structured version (numbered lines) | Creditor's name, address, country, town — fully structured |

### 3.5 Agents on the "sending" side — fields 52, 51A, and the message header

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `52A` | Ordering Institution, BIC version | Debtor's Bank (BICFI), plus clearing-system code if present |
| `52D` | Ordering Institution, name/address version | Debtor's Bank name & address |
| *(header Sender BIC, if 52 is absent)* | The bank that actually sent this SWIFT message | Falls back to populate Debtor's Bank |
| *(header Sender BIC, always)* | — | Also separately populates "Instructing Agent" — the bank at *this specific hop*, which can legitimately differ from the Debtor's Bank in a multi-bank relay |
| `51A` | Sending Institution (rare, FileAct-only field) | Not carried into the message body — flagged as present, not translated (see §3.9) |

### 3.6 Agents on the "receiving" side — field 57 and the message header

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `57A` | Account With Institution, BIC version | Creditor's Bank (BICFI), plus clearing-system code if present |
| `57B` | Account With Institution, account/party-ID only | Creditor's Bank account |
| `57C` | Account With Institution, clearing-code only | Creditor's Bank clearing-system reference |
| `57D` | Account With Institution, name/address version | Creditor's Bank name & address |
| *(header Receiver BIC, if 57 is absent)* | The bank meant to ultimately act as Creditor's Bank | Falls back to populate Creditor's Bank |
| *(header Receiver BIC, always)* | — | Also separately populates "Instructed Agent" — this hop's receiving bank, which can differ from the Creditor's Bank |

### 3.7 Correspondent / reimbursement banks — fields 53, 54, 55

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `53A` / `53B` / `53D` | Sender's Correspondent bank | Settlement Instructions → Instructing Reimbursement Agent (BIC, account, or name/address depending on option used) |
| `54A` / `54B` | Receiver's Correspondent bank | Settlement Instructions → Instructed Reimbursement Agent |
| `55A` / `55B` | Third Reimbursement Institution | Settlement Instructions → Third Reimbursement Agent |
| *(derived)* | Whether any of 53/54/55 is present at all | Sets Settlement Method to "cover payment" style vs. the simple default |

### 3.8 Intermediary bank — field 56

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `56A` | Intermediary Institution, BIC version | Intermediary Agent 1 (BICFI + clearing code) |
| `56C` | Intermediary Institution, clearing-code/party-ID only | Intermediary Agent 1's clearing reference / account |
| `56D` | Intermediary Institution, name/address version | Intermediary Agent 1 name & address |

### 3.9 Instructions, remittance info, and charges — fields 23E, 70, 72, 77B, 26T, 71A/F/G

| MT103 Tag | What it means | Where it goes |
|---|---|---|
| `23B` | Bank Operation Code (e.g. CRED, SPRI, SSTD, SPAY) | Payment Type's Local Instrument |
| `23E` | Instruction codes (SDVA, TELB, PHOB, INTC, CORT, etc.) | Split across Instruction-for-Creditor's-Agent, Category Purpose, or Service Level, depending on the specific code |
| `26T` | Transaction Type Code | Purpose (proprietary code) |
| `70` | Remittance information (may contain a `/ROC/` reference codeword) | End-to-end reference (if `/ROC/` present) and/or free-text Remittance Information |
| `71A` | Details of Charges (OUR/SHA/BEN) | Charge Bearer (translated to the ISO 20022 equivalent code) |
| `71F` | Sender's Charges (currency + amount, may repeat) | Charges Information block(s), charging agent = the Sender |
| `71G` | Receiver's Charges | Charges Information block, charging agent = the Receiver |
| `72` | Sender-to-Receiver free-text instructions, using `/INS/`, `/ACC/`, and other codewords | Split across Previous Instructing Agents 1–3 (`/INS/`), Instruction-for-Creditor's-Agent (`/ACC/`), and Instruction-for-Next-Agent (anything else) |
| `77B` | Regulatory Reporting free text | Regulatory Reporting block (reporting indicator, country code, and/or the raw text if it can't be split further) |
| `13C` | Time Indication (several sub-codes: sender's time, receiver's time, cutoff times) | Settlement Time Indication / Settlement Time Request fields |

**Not implemented as a value-mapping** (and why — this is deliberate, not an oversight):
- `51A` (Sending Institution) — per SWIFT's own rules this field only appears in a different transmission mode this converter doesn't target, and its content duplicates information already captured elsewhere. Its *presence* is still recognized and surfaced as a warning.

---

## 4. Dry Run — Watching a Real Message Go Through the Engine

Let's convert one real, complete example, step by step, exactly as the engine actually processed it (this is a live trace, not a hypothetical).

### The input (a simple MT103)

```
{1:F01BANKBEBBAXXX0000000000}{2:I103BANKDEFFXXXXN}{4:
:20:REF123456789
:23B:CRED
:32A:240115USD1234,56
:50K:JOHN DOE
123 MAIN STREET
:59:JANE SMITH
456 OTHER STREET
:71A:SHA
-}
```

In plain English, this says: *"Reference REF123456789. This is a standard credit transfer. Value date 15 January 2024, USD 1,234.56. Sender is a bank whose SWIFT address starts with BANKBEBB, receiving bank starts with BANKDEFF. The person sending the money is John Doe, 123 Main Street. The person receiving it is Jane Smith, 456 Other Street. Charges are shared between both parties."*

### Step 1 — Rulebook check

The engine loads `MT103_TO_PACS00800108.yaml` and confirms it has everything a rulebook needs (a source/target format declared, a version, and a non-empty list of field rules). This message never even gets involved in this step — it's purely "is the rulebook itself OK to use." ✅ Passed.

### Step 2 — Reading the message

The engine splits the raw text into a simple list of tag → value pairs:

| Tag | Value |
|---|---|
| `20` | `REF123456789` |
| `23B` | `CRED` |
| `32A` | `240115USD1234,56` |
| `50K` | `JOHN DOE` / `123 MAIN STREET` |
| `59` | `JANE SMITH` / `456 OTHER STREET` |
| `71A` | `SHA` |
| *(from the message header, Block 1)* | Sender's BIC: `BANKBEBBXXX` |
| *(from the message header, Block 2)* | Receiver's BIC: `BANKDEFFXXX` |

### Step 3 — Converting, field by field

This is where the rulebook (§3 above) gets applied. Here is exactly what happened to each field:

| Source | Rule applied | Result written into the new message |
|---|---|---|
| `20` | Copy as-is | Message ID = `REF123456789` |
| `20` | Copy as-is (max 16 characters) | Instruction ID = `REF123456789` |
| `32A` | Extract the date portion, reformat it | Settlement Date = `2024-01-15` |
| `32A` | Extract the currency portion | Settlement Amount currency = `USD` |
| `32A` | Extract the amount, swap the comma for a decimal point | Settlement Amount = `1234.56` |
| *(no 53/54/55 present)* | Derived rule | Settlement Method = `INDA` (simple, no correspondent chain) |
| Sender BIC (header) | Used because field 52 is absent | Debtor's Bank BIC = `BANKBEBBXXX`, **and separately** Instructing Agent BIC = `BANKBEBBXXX` |
| `71A` | Look up in the OUR/SHA/BEN → ISO code table | Charge Bearer = `SHAR` (SHA → SHAR) |
| `23B` | Copy as-is | Local Instrument = `CRED` |
| `50K` | Split first line as name, rest as address | Debtor Name = `JOHN DOE`, Debtor Address Line = `123 MAIN STREET` |
| `59` | Split first line as name, rest as address | Creditor Name = `JANE SMITH`, Creditor Address Line = `456 OTHER STREET` |
| Receiver BIC (header) | Used because field 57 is absent | Creditor's Bank BIC = `BANKDEFFXXX`, **and separately** Instructed Agent BIC = `BANKDEFFXXX` |
| *(no rule matched)* | A safe placeholder | End-to-End ID = `NOTPROVIDED` (nothing in the source message supplied one) |

No field in this particular message needed the AI-assisted path — every field here had one clear, exact answer, so the deterministic rules handled all of it.

### Step 4 — Validating

Two independent checks ran against the result:

1. **Rule-based checks** — checked mandatory fields are present, amounts have the right number of decimal places, currency codes are valid, and so on. One **non-blocking warning** was raised: the addresses for both Debtor and Creditor are plain free-text lines (`123 MAIN STREET`, `456 OTHER STREET`) with no separately-structured Town/Country fields. This is flagged because SWIFT is moving toward requiring structured addresses (see §7) — but it is **not** an error today, so the conversion still succeeds.
2. **AI reviewer pass** — an independent AI model was shown the source fields, the rules that were supposed to apply, and the actual output, and asked to spot anything that looks wrong. It found nothing to flag.

Both checks passed (one warning, zero errors), so the conversion is reported as successful.

### The final output

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>REF123456789</MsgId>
      <CreDtTm>2026-09-09T04:59:14.071Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>REF123456789</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1234.56</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2024-01-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt><FinInstnId><BICFI>BANKBEBBXXX</BICFI></FinInstnId></InstgAgt>
      <InstdAgt><FinInstnId><BICFI>BANKDEFFXXX</BICFI></FinInstnId></InstdAgt>
      <Dbtr>
        <Nm>JOHN DOE</Nm>
        <PstlAdr><AdrLine>123 MAIN STREET</AdrLine></PstlAdr>
      </Dbtr>
      <DbtrAgt><FinInstnId><BICFI>BANKBEBBXXX</BICFI></FinInstnId></DbtrAgt>
      <CdtrAgt><FinInstnId><BICFI>BANKDEFFXXX</BICFI></FinInstnId></CdtrAgt>
      <Cdtr>
        <Nm>JANE SMITH</Nm>
        <PstlAdr><AdrLine>456 OTHER STREET</AdrLine></PstlAdr>
      </Cdtr>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

That's the whole journey: one plain-text SWIFT message in, one standards-compliant XML message out, with a full explanation of every decision along the way.

---

## 5. Architecture Overview

### The big picture

```
 ┌─────────────────┐        HTTP (JSON)        ┌──────────────────────────────┐
 │   Web Browser    │  ───────────────────────► │      Backend (Java /         │
 │  (React frontend)│ ◄─────────────────────── │      Spring Boot service)     │
 └─────────────────┘                            └───────────────┬──────────────┘
                                                                  │ reads
                                                                  ▼
                                                   ┌───────────────────────────┐
                                                   │  Mapping Rulebooks (YAML)  │
                                                   │  + ISO 20022 XSD schemas   │
                                                   └───────────────────────────┘
                                                                  │ (a few, narrowly-
                                                                  │  scoped fields only)
                                                                  ▼
                                                   ┌───────────────────────────┐
                                                   │   AI Model (via API)      │
                                                   │  free-text splitting +    │
                                                   │  independent audit pass   │
                                                   └───────────────────────────┘

                                                   ┌───────────────────────────┐
                                                   │ Optional: Address Parser  │
                                                   │ sidecar (Python service)  │
                                                   └───────────────────────────┘
```

### The pieces, in plain terms

- **The frontend** is a simple web page: paste a message in, pick source/target format, click convert, see the result and any warnings. It also has a page for uploading a brand-new rulebook (see §5.5).
- **The backend** is the actual engine. It's a single Java service with no database — everything it needs to know about a specific conversion (MT103→pacs.008, or any other pair) lives in a plain YAML rulebook file on disk, not hardcoded in Java. This is the single most important design decision in this app: **adding support for a new message type is a documentation task, not a coding task.**
- **The rulebooks** (YAML files) are the actual source of truth for "what does field X mean and where does it go." Every rule is written with its sourcing/citation and history in plain English right next to it, so a human reviewer can audit *why* a rule exists, not just *what* it does.
- **The AI model** is used in exactly two narrow, supervised roles — never as a general "figure it out" fallback:
  1. Splitting a handful of genuinely free-text fields (like a loosely-formatted regulatory reporting line) where no single fixed rule can cover every real-world variant, and
  2. A second, independent pass that re-checks the finished result and flags anything that looks inconsistent with the rules it was given.
  Everything else — dates, amounts, currency codes, structured name/address fields, BIC codes — is handled by exact, deterministic code with zero AI involvement, because those have one single correct answer and there's no reason to introduce any uncertainty.
- **The address-parser sidecar** is an optional, separate small Python service (using an open-source address-parsing library) that can enrich free-text addresses with a structured street/city/country breakdown, in preparation for a possible future SWIFT rule requiring that structure (see §7). It's off by default and the engine works perfectly well without it — if it's unreachable, the engine just falls back to the address as plain free text.

### 5.1 The conversion pipeline (inside the backend)

This is the same four-step workflow from §2, described here in terms of the actual internal handoffs:

```
MappingRegistry (loads the YAML)
      │
      ▼
CompletenessAuditor (rulebook sanity check — no AI)
      │
      ▼
MtParserService / MxParserService (reads the raw message into tag/value pairs)
      │
      ▼
ConverterService (applies the rulebook — deterministic + narrowly-scoped AI)
      │
      ▼
ValidatorService (rule-based checks + independent AI audit)
      │
      ├── found a real problem in the conversion → loop back and retry (a few times, then give up cleanly)
      ├── found a gap in the rulebook itself       → stop and clearly report the gap
      └── all clear                                 → return the finished message
```

One thing worth calling out because it's unusual and deliberate: **the rulebook is re-read from disk on every single conversion request.** There's no cache to restart or clear — edit the YAML file, and the very next conversion uses the new rule. This is what lets the "upload a new rulebook" feature (§5.5) work without a server restart.

### 5.2 Two layers of safety checking

1. **Deterministic rules** — things like "this field must be present," "these two fields' currencies must match," "this amount can't have more decimal places than the currency allows." These come directly from SWIFT's published rulebooks and never involve any AI, so they're 100% repeatable.
2. **AI audit** — an independent double-check, done by giving a *second, separate* AI call the original fields, the rule that was supposed to apply, and the actual result, and asking it to look for mistakes. This exists because deterministic rules can't catch every subtle mismatch, but it's explicitly a supporting layer, not the primary safety net — and because AI output isn't perfectly consistent run-to-run, its findings are treated more cautiously than the deterministic checks (see the note on non-determinism in §8).

### 5.3 Structural validation against the real schema

Where possible, the engine reads the actual official XML schema (XSD) for the target message type and uses it for two things: making sure elements come out in the exact order the schema requires, and proactively checking that every schema-mandatory piece is present *before* even trying a stricter, official schema validation pass. Without a schema configured, the engine falls back to a best-effort approach and says so loudly in its warnings — it never silently pretends full compliance it can't actually verify.

### 5.4 Registering a new conversion at runtime

Because the rulebook is just a file on disk that's read fresh every time, the app includes an upload feature: someone can submit a new rulebook (and, for MT→MX conversions, the matching XSD) through the web page, and the new conversion type is available immediately — no restart, no new Java code. If a rulebook already exists for that pair, the user gets a clear warning before anything is overwritten.

### 5.5 Everyday reliability

- Every deterministic error message explains *what* went wrong and *why*, in a format both a person and another program can act on.
- No conversion ever silently continues past a genuine data problem — the design principle throughout is "stop and explain" rather than "guess and hope."
- The engine is stateless between requests — every conversion is independent; nothing about one message affects another.

### 5.6 The Business Application Header (head.001)

Real SWIFT/ISO 20022 traffic is usually accompanied by a small "cover note" called the **Business Application Header** (`head.001`) — it says which bank sent the message, which bank it's addressed to, a business message ID, and when it was created. As of MT103 rulebook version 2.33+, this app can produce that header alongside the pacs.008 for MT→MX conversions, when the rulebook opts in (currently: MT103 → pacs.008; not yet enabled for MT202 → pacs.009).

A few things worth knowing about how it's built:

- **Where the data comes from.** The "From"/"To" banks reuse the same Instructing/Instructed Agent BICs already described in §3.5/§3.6 (the banks handling *this specific hop* of the message, not necessarily the ultimate Debtor's/Creditor's bank). The business message ID reuses the same Message ID the pacs.008 itself carries, and the creation time is the exact same timestamp — so the header and the message body never disagree with each other.
- **It's returned separately, not glued onto the pacs.008 XML.** ISO 20022 doesn't actually define one single fixed way to combine a header and a business message into one file — real implementations agree only that the header comes first, as a sibling of the message, with the exact "envelope" around them left up to each bank's own gateway/network. So this app hands back two independently-correct pieces (the header, and the pacs.008 body) rather than inventing a combining structure that might not match what your actual gateway expects. In the web page, the header appears in its own small panel above the converted message.
- **The exact shape was corrected against a real example.** The header is wrapped in a `<Envelope xmlns="urn:swift:xsd:envelope">` element and uses the `head.001.001.01` version — both confirmed against a genuine SWIFT CBPR+ worked example rather than assumed.

---

## 6. File-by-File Reference

A one-line purpose for every source file, organized by folder.

### Backend — `backend/src/main/java/com/wiredesk/mtmx/`

**Top level**
- `MtmxConverterApplication.java` — the program's entry point; just starts the Spring Boot service.

**`config/`** — application-wide settings
- `AppProperties.java` — all the configurable knobs (where rulebooks live, which AI provider/model to use, retry limits, address-parser settings), read from the app's configuration file.
- `WebConfig.java` — allows the web frontend to call this backend from a browser (CORS settings).

**`parser/`** — turning raw message text into structured data
- `MtParserService.java` — reads a raw SWIFT MT message (like an MT103) into individual tagged fields, using a professional SWIFT-parsing library rather than hand-written pattern matching.
- `MxParserService.java` — reads a raw ISO 20022 XML message into a flat list of element-path → value pairs, for the MX→MT direction.
- `ParsedMessage.java` — the simple in-memory container holding "here are all the fields I found," shared by both parsers.
- `DecompositionService.java` — splits one free-text field into several structured pieces (e.g. a multi-line address into name/street/city), trying exact rules first and only asking the AI when the rulebook explicitly allows it.

**`convert/`** — building the output message
- `ConverterService.java` — the heart of the engine: walks the rulebook entry by entry and builds the output message field by field.
- `ConvertedMessage.java` — the in-memory result of a conversion (the built-up output plus a trace of how each field got there).
- `MxRenderer.java` — turns the internal "path → value" result into properly-nested, properly-ordered XML.
- `MtRenderer.java` — turns the internal result into SWIFT MT block-4 text, for conversions that produce an MT-format output.
- `BusinessApplicationHeaderRenderer.java` — builds the optional head.001 "cover note" header described in §5.6, when a rulebook opts in.
- `XsdOrderingIndex.java` — reads an official XML schema (XSD) and works out the correct element order and which pieces are mandatory.
- `XsdIndexRegistry.java` — loads and caches one schema-reader per message type, shared by whoever needs it.

**`transform/`**
- `TransformationEngine.java` — the library of exact, rule-based transformations available to the rulebook: copy, uppercase, date reformat, currency-decimal reformatting, code lookups, text chunking, and so on.

**`validate/`** — checking the result
- `ValidatorService.java` — runs every deterministic rule from the rulebook against the finished message, checks the real schema if one is configured, and coordinates the independent AI audit pass.
- `ValidationReport.java` — the outcome of validation: is it valid, what errors/warnings were found, did the AI audit actually run.

**`mapping/`** — loading and managing rulebooks
- `MappingRegistry.java` — finds and loads rulebook YAML files from disk by naming convention, fresh on every request.
- `MappingFilenames.java` — the shared logic for turning "source format + target format" into the expected rulebook (and schema) filename.
- `CompletenessAuditor.java` — the pure sanity-checker that confirms a rulebook is internally complete before it's ever used on a real message.
- `AuditResult.java` — the simple pass/fail-plus-details result of that sanity check.
- `MappingUploadService.java` — the logic behind letting someone upload a brand-new rulebook (and matching schema) at runtime.
- `model/MappingDocument.java` — the structured, in-memory version of a whole rulebook YAML file.
- `model/FieldMapping.java` — one single field rule from the rulebook (source field, target location, which transformation to apply, and all its options).
- `model/DecompositionRule.java` — the detailed instructions for splitting one field into several structured pieces.
- `model/ConditionalRule.java` — a rule that derives a value from *whether* other fields are present, not from any one field's content.
- `model/ConditionalSubElementTarget.java` — lets one extracted piece of data be routed to one of two different destinations depending on what it looks like (e.g. "is this an IBAN or not").
- `model/StructuredAddressRule.java` — the opt-in instruction to also run a free-text address through the address-parsing sidecar.
- `model/BusinessApplicationHeaderConfig.java` — the opt-in instructions for building the head.001 header described in §5.6 (which fields to pull the From/To banks, message ID, and creation time from).
- `model/EdgeCase.java` — one documented "if this unusual situation happens, here's what to do" note attached to a field rule.
- `model/ValidationRule.java` — one rule from the rulebook's validation section (e.g. "these two fields must be consistent").

**`llm/`**
- `GeminiClient.java` — the one place in the whole engine that talks to the AI model; handles the two narrow use cases (splitting a free-text field, and the independent audit pass) and nothing else.

**`address/`** — optional address enrichment
- `AddressParserClient.java` — calls the separate address-parsing sidecar service to break free text into street/city/country.
- `ParsedAddress.java` — the structured result of that address-parsing call.

**`orchestrate/`**
- `ConversionOrchestrator.java` — wires the whole pipeline together (rulebook → parse → convert → validate) and owns the retry/give-up logic — the only place that policy lives.
- `ConversionResult.java` — everything returned to the caller about one conversion: the output, the field-by-field trace, warnings, and pipeline status.

**`web/`** — the HTTP API
- `ConversionController.java` — the three main endpoints: health check, list available conversions, and convert a message.
- `ConvertRequest.java` — the expected shape of an incoming conversion request.
- `MappingSummaryDto.java` — the short summary of one available conversion, sent to the frontend's dropdowns.
- `MappingUploadController.java` — the endpoints for checking whether a conversion already exists and for uploading a new rulebook.
- `MappingCheckResult.java` / `MappingUploadResult.java` — the response shapes for those upload-related endpoints.
- `GlobalExceptionHandler.java` — catches every kind of engine error and turns it into one consistent, clearly-labeled error response.

**`exception/`** — one class per distinct failure reason (all extend a common `MtmxException`), so every error the engine can produce is a named, specific, self-explanatory type rather than a generic failure:
- `MtmxException.java` — the shared base type all engine errors extend.
- `MappingDocNotFoundException.java` — no rulebook exists for the requested conversion pair.
- `MappingDocInvalidException.java` — the rulebook file itself doesn't parse.
- `MappingDocIncompleteException.java` — the rulebook parses, but is missing something it must have.
- `ParsingException.java` — the source message itself couldn't be read.
- `MandatorySourceFieldMissingException.java` — the source message is missing a field the rulebook says is required.
- `TransformationException.java` — a specific field-conversion step failed.
- `SemanticDecompositionGapException.java` — a free-text field couldn't be confidently split, even with AI help.
- `UnmappableFieldException.java` — a mandatory target field has no way to be populated from this source message.
- `ValidationFailedException.java` — the finished message failed validation.
- `LlmResponseException.java` — the AI model's response couldn't be understood/used.
- `MappingUploadConflictException.java` — an uploaded rulebook or schema would overwrite an existing one without confirmation.

### Backend — resources & data
- `backend/mappings/MT103_TO_PACS00800108.yaml` — the actual MT103 → pacs.008 rulebook described in §3.
- `backend/mappings/MT202_TO_PACS00900108.yaml` — the equivalent rulebook for MT202 → pacs.009 (bank-to-bank transfers).
- `backend/mappings/CHANGELOG_MT103_TO_PACS008.md` — the full, dated, versioned history of every fix and decision made to the MT103 rulebook.
- `backend/xsd/pacs.008.001.08.xsd`, `backend/xsd/pacs.009.001.08.xsd` — the official ISO 20022 schemas used for structural validation.
- `backend/src/main/resources/application.yml` — the default configuration values (ports, folders, AI provider settings).

### Address-parser sidecar — `address-parser-service/`
- `app.py` — a small, separate Python web service that wraps an open-source address-parsing library (libpostal) to split free text into street/city/country.
- `requirements.txt` — the Python packages it needs.
- `Dockerfile` — how to package it as a container.
- `README.md` — how to set it up and run it.

### Frontend — `frontend/src/`
- `main.jsx` — the tiny file that starts the React app in the browser.
- `App.jsx` — the main page: holds all the on-screen state (which format is selected, what's in the text boxes, the result), and coordinates the other pieces.
- `components/MessagePanel.jsx` — the reusable text box for pasting in a message or displaying the converted result.
- `components/PipelineStatus.jsx` — the visual "Mapping doc → Parse → Convert → Validate" progress indicator.
- `components/DiagnosticsPanel.jsx` — displays errors and warnings in a clear, stage-labeled way.
- `components/UploadMappingPanel.jsx` — the screen for uploading a new rulebook (with the overwrite-warning flow).
- `lib/api.js` — all the calls from the browser to the backend's API.
- `lib/detectFormat.js` — auto-detects whether pasted text looks like an MT message or an MX message, so the dropdown can pre-select itself.
- `lib/samples.js` — a ready-made sample MT103 message for quickly trying the app out (the same one walked through in §4).
- `index.css` — the visual styling.

---

## 7. Future Enhancement Scopes

Listed roughly in order of expected value:

1. **Structured / hybrid addresses.** SWIFT's SR2026 release plans to require Town Name and Country to be carried as their own structured fields (not just buried in free text) for cross-border payments — but as of this writing (September 2026), SWIFT has **postponed this entire package indefinitely** (announced 27 August 2026, no new date set, expected update by December 2026). The engine already has a non-blocking warning in place (§3.9's "not implemented" note aside — see the validation rule VR008) that flags exactly which messages would need attention, without rejecting anything today. The address-parsing sidecar (§5, §6) is the mechanism ready to be switched on once a real deadline is confirmed.
2. **A dashboard of what's actually been tested.** Track which rulebook rules have live test coverage, which fields go through the AI-assisted path vs. pure deterministic rules, and what percentage of test messages use each path — this is the clearest way to demonstrate the engine's reliability with real numbers instead of "we tested it a lot."
3. **Repeat-run consistency testing for the AI-assisted fields.** Since the same input can occasionally produce a slightly different AI response run-to-run, running the same message through several times and comparing results would give a measured, quotable consistency number instead of an assumption.
4. **Business Application Header for the other conversion pairs.** ✅ Built for MT103 → pacs.008 (see §5.6) — the natural next step is enabling the same opt-in header for MT202 → pacs.009 and any future conversion pair, since the mechanism is already generic. Still genuinely open: how the header and the message body get physically combined for transport (a SOAP envelope, a FileAct payload, ...) is specific to each bank's own gateway/network, not something this engine can decide on your behalf.
5. **More conversion pairs.** The upload feature (§5.4) already lets anyone register a new MT↔MX rulebook without touching the code — the natural next step is building out more of these rulebooks (e.g. other payment message types) using the same "cite every rule" discipline already established for MT103 and MT202.
6. **Formal access to SWIFT's official test suite.** SWIFT runs a "Test Sparring Partner" conformance-testing service with real pre-production test cases — running this engine's output against that suite (once access is arranged) would be a stronger proof of correctness than any homemade test set.
7. **Production-traffic-shaped testing.** All current test messages are hand-authored. Real-world messages have quirks (inconsistent casing, odd whitespace, unusual line breaks) that synthetic tests may not reproduce — sourcing anonymized real traffic (with proper authorization) would surface edge cases faster.
8. **Basic security hardening before any wider deployment.** There is currently no login/authentication on any endpoint, including the rulebook-upload feature — fine for an internal/local tool, but a hard requirement before exposing this beyond a trusted network.

---

## 8. Good to Know — Extra Notes

**A short glossary, for non-technical readers:**
- **MT103** — the old-format SWIFT message for an international customer payment instruction.
- **pacs.008** — the new ISO 20022 XML equivalent of an MT103.
- **BIC** — Bank Identifier Code, a bank's unique international "address" (e.g. `DEUTDEFF`).
- **UETR** — a unique tracking number attached to a payment so it can be traced across every bank it passes through.
- **Debtor / Creditor** — the ISO 20022 terms for "the person paying" and "the person being paid," equivalent to MT103's Ordering Customer / Beneficiary Customer.
- **Rulebook (mapping document)** — the YAML file that tells the engine exactly how to convert one message format into another, field by field.

**Two safety habits worth understanding:**
- **"Fail closed, not silently."** If the engine isn't sure a rule applies correctly, it stops and reports the problem rather than producing output that might be subtly wrong. This is slower to work with than a system that always produces *something*, but it's the right tradeoff for payment messages.
- **AI results are treated as advisory, not absolute.** The independent AI audit pass (§5.2) can occasionally be inconsistent run-to-run — the same message might pass on one attempt and get a spurious warning on another. When that happens, the fix is almost always to give the AI clearer information about the rule in question (several real examples of exactly this are documented in the rulebook's own changelog), not to just re-run it and hope.

**How the codebase stays trustworthy over time:** every fix to the MT103 rulebook is written down, dated, and — wherever possible — cited against an actual SWIFT publication, not just "this seemed right." That full history lives in `backend/mappings/CHANGELOG_MT103_TO_PACS008.md`. If you ever want to know *why* a particular field is handled a particular way, that file (or the rule's own notes in the YAML) is the place to look.
