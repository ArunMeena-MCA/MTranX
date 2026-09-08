# MT103 → pacs.008 Retest Set — 19 Failed/Partial Cases from TC01–TC100

**Correction on count:** I said "21" earlier — that was a miscount. The verified number, pulled directly from the analysis table, is **19**: 18 clear fails plus TC66, which is a minor cosmetic issue (not a real defect), included here for completeness.

**How to use:** run each of these through your engine again after applying fixes, paste the new XML/error result into the slot, and send back. Original MT text is unchanged from the first round — reused verbatim from the source files, not retyped, to rule out transcription drift.

| TC# | What was wrong last time |
|---|---|
| TC09 | No validation that BEN requires ≥1 occurrence of 71F |
| TC18 | Field 51A unmapped → whole message rejected |
| TC24 | 57A rejects plain BIC, demands `//` clearing prefix (regression) |
| TC25 | Same regression, on 56A |
| TC26 | Same regression — breaks the simplest possible 57A case |
| TC37 | `/ROC/` no longer extracts to `EndToEndId` (regression) |
| TC38 | Same regression, combined-codeword case |
| TC43 | False-positive validation rejects correct SHA→SHAR mapping |
| TC45 | 26T silently dropped (no error, no `CtgyPurp`) |
| TC50 | Same `/ROC/` regression as TC37, inside the kitchen-sink case |
| TC66 | Amount not zero-padded ("1000.5" vs "1000.50") — cosmetic only |
| TC70 | CCPT identity dropped on Creditor (59F) side (asymmetry vs Debtor side) |
| TC75 | 53A + clearing code dumps raw string into spurious `SttlmAcct` |
| TC82 | Confirms the 56A/57A regression isn't clearing-code-specific |
| TC84 | Severe: only 1st of 4 narrative lines survives in field 70 |
| TC89 | No validation that BEN forbids 71G (reverse of TC09) |
| TC96 | Refuses when INTC is combined with other 23E codes |
| TC99 | No validation for missing decimal comma in 32A |
| TC100 | Same 56A/57A regression blocks an otherwise-clean integration case |

---

## TC09 🔴 — INVALID: 71A=BEN but no 71F present at all (violates C14)
**Tests:** Same class of issue as TC08 — BEN mandates at least one 71F occurrence; this message omits it entirely. Tests validation-layer robustness, not translation logic.

```
{1:F01TESTGB01AXXX0000000009}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111119}}
{4:
:20:TC09REF0009
:23B:CRED
:32A:260315GBP1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:BEN
-}
```
**Expected / key checks:** `ChrgBr`=CRED will populate fine (that part is unambiguous), but no `ChrgsInf` will exist to populate — since BEN semantically implies deducted charges exist. Does your engine flag the missing 71F as suspicious, or translate silently as if this were a zero-charge BEN payment?

**Your XML result:**
```
{"detail":{"error_type":"ValidationFailedException","stage":"validate","message":"Validation failed after retries. Errors: [[VR020] Rule C14: if field 71A is BEN, at least one occurrence of field 71F is mandatory (error code E15). - source field '71A'=BEN requires source field '71F' to be present (at least one occurrence), but it is absent]","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"done"},{"key":"validate","status":"error"}],"errors":["[VR020] Rule C14: if field 71A is BEN, at least one occurrence of field 71F is mandatory (error code E15). - source field '71A'=BEN requires source field '71F' to be present (at least one occurrence), but it is absent"],"warnings":[]}}
```

---

-e 
---

## TC18 🟡 — Field 51A present (Sending Institution — spec says FileAct-only)
**Tests:** Per the SWIFT MT103 field spec, *"Field 51A is only valid in FileAct"* — meaning a standards-compliant FIN message shouldn't carry it at all. This is an engine-robustness probe: what happens when an out-of-context field with no defined pacs.008 target shows up? There's no bank-published MT↔MX crosswalk that maps tag 51 anywhere — so the "correct" answer is that it's harmlessly ignored, not that it corrupts adjacent field parsing.

```
{1:F01TESTGB01AXXX0000000018}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111128}}
{4:
:20:TC18REF0018
:23B:CRED
:32A:260315GBP600,00
:50K:/11112222
JOHN SMITH
LONDON
:51A:ABNANL2A
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** The rest of the message (Dbtr, amounts, Cdtr) should translate completely normally — the real check is whether `:51A:` being present **breaks or skips parsing of unrelated fields**, which would indicate a fragile sequential parser rather than a tag-keyed one.

**Your XML result:**
```
{"detail":{"error_type":"UnmappableFieldException","stage":"convert","message":"No mapping rule for source field '51A'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"error"},{"key":"validate","status":"skipped"}]}}
```

---

-e 
---

## TC24 🟢 — 56A with Fedwire routing code (`//FW`), correctly paired with 57A (Rule C9 satisfied)
**Tests:** National clearing code in the intermediary institution's party identifier, plus Rule **C9** — 56a present requires 57a also present. This message satisfies C9 correctly.

```
{1:F01TESTGB01AXXX0000000024}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111134}}
{4:
:20:TC24REF0024
:23B:CRED
:32A:260315USD3200,00
:50K:/11112222
JOHN SMITH
LONDON
:56A://FW021000089
CHASUS33
:57A:ABNAUS33
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Intermediary agent BIC=CHASUS33 with the Fedwire routing number `021000089` captured (ideally in `ClrSysMmbId` with `ClrSysId/Cd`=USABA or similar) — same class of check as TC19's UK sort code. Account-with-institution BIC=ABNAUS33 present and distinct from the intermediary.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC24REF0024</MsgId>
      <CreDtTm>2026-09-07T07:36:03.849Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC24REF0024</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111134</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">3200.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <IntrmyAgt1>
        <FinInstnId>
          <BICFI>CHASUS33</BICFI>
          <ClrSysMmbId>
            <ClrSysId>
              <Cd>USABA</Cd>
            </ClrSysId>
            <MmbId>021000089</MmbId>
          </ClrSysMmbId>
        </FinInstnId>
      </IntrmyAgt1>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>ABNAUS33</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC25 🔴 — INVALID: 56A present without 57A (violates Rule C9)
**Tests:** Direct violation of C9 — an intermediary institution with no account-with-institution named is structurally incomplete (how does the payment reach the beneficiary's bank?). Tests validation-layer robustness on a *sequencing* rule rather than a value rule.

```
{1:F01TESTGB01AXXX0000000025}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111135}}
{4:
:20:TC25REF0025
:23B:CRED
:32A:260315USD1800,00
:50K:/11112222
JOHN SMITH
LONDON
:56A:IRVTUS3N
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch the missing 57A and reject/flag, or translate as if this were a normal message with an intermediary and no account-with-institution? Given the pattern so far (validation catches amount rules but misses structural/party rules), predict this one slips through — worth confirming either way.

**Your XML result:**
```
{"detail":{"error_type":"ValidationFailedException","stage":"validate","message":"Validation failed after retries. Errors: [[VR012] If field 56a (Intermediary Institution) is present, field 57a must also be present. - source field '56A' is present but none of [57A, 57D] are]","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"done"},{"key":"validate","status":"error"}],"errors":["[VR012] If field 56a (Intermediary Institution) is present, field 57a must also be present. - source field '56A' is present but none of [57A, 57D] are"],"warnings":[]}}
```

---

-e 
---

## TC26 🟢 — 57A: Account With Institution (basic)
**Tests:** Baseline — the institution servicing the beneficiary's account, when different from the Receiver.

```
{1:F01TESTGB01AXXX0000000026}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111136}}
{4:
:20:TC26REF0026
:23B:CRED
:32A:260315EUR2200,00
:50K:/11112222
JOHN SMITH
LONDON
:57A:ABNANL2A
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `CdtrAgt/FinInstnId/BICFI`=ABNANL2A (overriding the default-to-Receiver behavior seen in earlier baseline tests, since 57A is now explicitly present).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC26REF0026</MsgId>
      <CreDtTm>2026-09-07T07:36:24.601Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC26REF0026</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111136</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">2200.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC37 🟢 — Field 70 with `/ROC/` (Ordering Customer's Reference) — the `EndToEndId` trigger
**Tests:** The one codeword that actually changes a *different* element. This is the mainstream, most important field-70 case — confirms `/ROC/` correctly routes to `PmtId/EndToEndId` instead of staying in `RmtInf/Ustrd`.

```
{1:F01TESTGB01AXXX0000000037}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111147}}
{4:
:20:TC37REF0037
:23B:CRED
:32A:260315EUR1450,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:/ROC/CUSTREF20260315AB
:71A:SHA
-}
```
**Expected / key checks:** `PmtId/EndToEndId`=CUSTREF20260315AB (**not** NOTPROVIDED this time). Confirm whether the `/ROC/` content also still appears in `RmtInf/Ustrd`, or is fully extracted out and removed from there — either is defensible, but you should know which your engine does.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC37REF0037</MsgId>
      <CreDtTm>2026-09-07T07:36:44.822Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC37REF0037</InstrId>
        <EndToEndId>CUSTREF20260315AB</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111147</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1450.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC38 🟢 — Field 70 with two codewords combined (`/INV/.../ROC/...`)
**Tests:** The spec permits multiple references separated by `//`. Combines TC35 and TC37's codewords in one field — checks the engine can split them apart rather than treating the whole line as one opaque string.

```
{1:F01TESTGB01AXXX0000000038}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111148}}
{4:
:20:TC38REF0038
:23B:CRED
:32A:260315USD875,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:/INV/2026-778/ABC-99//ROC/CUSTREF778899
:71A:SHA
-}
```
**Expected / key checks:** `PmtId/EndToEndId`=CUSTREF778899 (extracted from the `/ROC/` portion), **and** the `/INV/2026-778/ABC-99` portion should still land in `RmtInf/Ustrd`. If your engine only handles a lone `/ROC/` (as in TC37) but chokes or mis-splits when a second codeword shares the line, that's a real gap — combined codewords in one field-70 occurrence are common in production traffic.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC38REF0038</MsgId>
      <CreDtTm>2026-09-07T07:36:52.272Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC38REF0038</InstrId>
        <EndToEndId>CUSTREF778899</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111148</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">875.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
      <RmtInf>
        <Ustrd>/INV/2026-778/ABC-99//ROC/CUSTREF778899</Ustrd>
      </RmtInf>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC43 🟢 — 23E: valid multi-code combination under SPRI (SDVA + INTC)
**Tests:** Rule C3 restricts SPRI to codes SDVA/TELB/PHOB/INTC only. SDVA+INTC together isn't in the forbidden-combination list (D67), and the ordering (SDVA before INTC) matches the spec's required sequence.

```
{1:F01TESTGB01AXXX0000000043}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111153}}
{4:
:20:TC43REF0043
:23B:SPRI
:23E:SDVA
:23E:INTC
:32A:260315EUR1500,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Both instruction codes should surface (likely `InstrForCdtrAgt` with two `Cd` entries, or equivalent) — same repeated-occurrence question as TC42, on a third distinct field.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC43REF0043</MsgId>
      <CreDtTm>2026-09-07T07:36:59.940Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC43REF0043</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111153</UETR>
      </PmtId>
      <PmtTpInf>
        <SvcLvl>
          <Cd>SDVA</Cd>
        </SvcLvl>
        <LclInstrm>
          <Prtry>SPRI</Prtry>
        </LclInstrm>
        <CtgyPurp>
          <Cd>INTC</Cd>
        </CtgyPurp>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1500.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC45 🟢 — 26T: Transaction Type Code
**Tests:** Simple categorical field, never tested before — confirms basic presence/mapping.

```
{1:F01TESTGB01AXXX0000000045}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111155}}
{4:
:20:TC45REF0045
:23B:CRED
:26T:K90
:32A:260315EUR650,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `PmtTpInf/CtgyPurp` (or equivalent) populated with K90.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC45REF0045</MsgId>
      <CreDtTm>2026-09-07T07:37:07.248Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC45REF0045</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111155</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">650.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC50 🟢 — Full integration ("kitchen sink"): reimbursement chain + structured parties + cross-currency + remittance + instructions + regulatory reporting, all in one message
**Tests:** The final case — combines many previously-tested elements together to check they still all work correctly *in combination*, not just in isolation. A realistic, richly-populated production-style message.

```
{1:F01TESTGB01AXXX0000000050}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111160}}
{4:
:20:TC50REF0050
:13C:/CLSTIME/1000+0100
:23B:CRED
:23E:INTC
:26T:K90
:32A:260315EUR7500,00
:33B:USD8250,00
:36:0,90909
:50F:/99887766
1/KITCHEN SINK EXPORTS LTD
2/100 KITCHEN SINK AVENUE
3/DE/BERLIN
:53A:CHASUS33
:54A:IRVTUS3N
:55A:BNPAFRPP
:59F:/11224488
1/KITCHEN SINK IMPORTS INC
2/200 FINAL AVENUE
3/US/CHICAGO
:70:/ROC/FINALTEST0050
:71A:SHA
:71F:USD25,00
:72:/INS/ABNANL2A
:77B:/ORDERRES/DE//BERLIN GERMANY
-}
```
**Expected / key checks:** This message deliberately includes `/ROC/` in field 70 (the one variant confirmed working in Batch 4) so it should clear that hurdle — the real question is whether everything else (13C, reimbursement chain, both structured parties, 71F, 72, 77B) still comes through correctly when combined, or whether stacking many fields together surfaces interaction bugs that don't show up when each is tested alone. Cross-check against your individual results for 13C (TC41), the reimbursement chain (TC23), 50F/59F (TC12/TC32), and 71F (TC05) — anything that differs *only* when combined here is a genuinely new finding.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC50REF0050</MsgId>
      <CreDtTm>2026-09-07T07:37:15.063Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>COVE</SttlmMtd>
        <InstgRmbrsmntAgt>
          <FinInstnId>
            <BICFI>CHASUS33</BICFI>
          </FinInstnId>
        </InstgRmbrsmntAgt>
        <InstdRmbrsmntAgt>
          <FinInstnId>
            <BICFI>IRVTUS3N</BICFI>
          </FinInstnId>
        </InstdRmbrsmntAgt>
        <ThrdRmbrsmntAgt>
          <FinInstnId>
            <BICFI>BNPAFRPP</BICFI>
          </FinInstnId>
        </ThrdRmbrsmntAgt>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC50REF0050</InstrId>
        <EndToEndId>FINALTEST0050</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111160</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
        <CtgyPurp>
          <Cd>INTC</Cd>
        </CtgyPurp>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">7500.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <SttlmTmReq>
        <CLSTm>10:00:00+01:00</CLSTm>
      </SttlmTmReq>
      <InstdAmt Ccy="USD">8250.00</InstdAmt>
      <XchgRate>0.90909</XchgRate>
      <ChrgBr>SHAR</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="USD">25.00</Amt>
        <Agt>
          <FinInstnId>
            <BICFI>TESTGB01XXX</BICFI>
          </FinInstnId>
        </Agt>
      </ChrgsInf>
      <PrvsInstgAgt1>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </PrvsInstgAgt1>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>KITCHEN SINK EXPORTS LTD</Nm>
        <PstlAdr>
          <TwnNm>BERLIN</TwnNm>
          <Ctry>DE</Ctry>
          <AdrLine>100 KITCHEN SINK AVENUE</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>99887766</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>KITCHEN SINK IMPORTS INC</Nm>
        <PstlAdr>
          <TwnNm>CHICAGO</TwnNm>
          <Ctry>US</Ctry>
          <AdrLine>200 FINAL AVENUE</AdrLine>
        </PstlAdr>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>11224488</Id>
          </Othr>
        </Id>
      </CdtrAcct>
      <RgltryRptg>
        <DbtCdtRptgInd>DEBT</DbtCdtRptgInd>
        <Dtls>
          <Ctry>DE</Ctry>
          <Inf>/ORDERRES/DE//BERLIN GERMANY</Inf>
        </Dtls>
      </RgltryRptg>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```
-e 
---

## TC66 🟢 — 32A with a single trailing decimal digit ("1000,5")
**Tests:** SWIFT amount format permits a partial decimal (one digit after the comma instead of two) — a legitimate but less common representation of 1000.50.

```
{1:F01TESTGB01AXXX0000000066}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111176}}
{4:
:20:TC66REF0066
:23B:CRED
:32A:260315GBP1000,5
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `IntrBkSttlmAmt Ccy="GBP">1000.50` — correctly normalized to two decimal places, not left as `1000.5` or misread as `1000.05`.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC66REF0066</MsgId>
      <CreDtTm>2026-09-07T07:37:27.964Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC66REF0066</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111176</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1000.5</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---
---

# GROUP C — Debtor/Creditor Structured Identity Variants (TC67–TC74)

-e 
---

## TC70 🟢 — 59F with an identity-code line 1 instead of an account (beneficiary side)
**Tests:** The exact mirror of TC13, but on the Creditor side — never tested before (TC13 only covered the Debtor).

```
{1:F01TESTGB01AXXX0000000070}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111180}}
{4:
:20:TC70REF0070
:23B:CRED
:32A:260315GBP540,00
:50K:/11112222
JOHN SMITH
LONDON
:59F:CCPT/GB/987654321
1/EMMA WATSON
2/10 DOWNING STREET
3/GB/LONDON
:71A:SHA
-}
```
**Expected / key checks:** `CdtrAcct` absent (identity code occupies the account slot). Same question as TC13 — is the passport data captured or dropped, and is the behavior symmetric between Dbtr and Cdtr sides?

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC70REF0070</MsgId>
      <CreDtTm>2026-09-07T07:37:35.541Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC70REF0070</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111180</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">540.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>EMMA WATSON</Nm>
        <PstlAdr>
          <TwnNm>LONDON</TwnNm>
          <Ctry>GB</Ctry>
          <AdrLine>10 DOWNING STREET</AdrLine>
        </PstlAdr>
      </Cdtr>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC75 🟢 — 53A with a national clearing-code prefix (`//FW`)
**Tests:** Same clearing-code-in-party-identifier pattern as TC19 (52A) and TC24 (56A), applied to field 53 for the first time.

```
{1:F01TESTGB01AXXX0000000075}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111185}}
{4:
:20:TC75REF0075
:23B:CRED
:32A:260315USD2200,00
:50K:/11112222
JOHN SMITH
LONDON
:53A://FW026009593
BOFAUS3N
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** BIC=BOFAUS3N captured on the reimbursement-agent element, with the Fedwire routing number 026009593 ideally in `ClrSysMmbId`. Given TC24 (56A) failed this exact pattern while TC19 (52A) succeeded, this is a genuinely uncertain third data point.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC75REF0075</MsgId>
      <CreDtTm>2026-09-07T07:37:48.537Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>COVE</SttlmMtd>
        <InstgRmbrsmntAgt>
          <FinInstnId>
            <BICFI>BOFAUS3N</BICFI>
            <ClrSysMmbId>
              <ClrSysId>
                <Cd>USABA</Cd>
              </ClrSysId>
              <MmbId>026009593</MmbId>
            </ClrSysMmbId>
          </FinInstnId>
        </InstgRmbrsmntAgt>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC75REF0075</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111185</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">2200.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC82 🟢 — 56A (plain BIC, no clearing code) + 57A together — clean baseline
**Tests:** Deliberately the "control" case contrasting with TC24 — same field pair, but without the `//FW` clearing-code complication. Isolates whether TC24's failure was specifically about clearing-code parsing, or about the 56a/57a pairing itself.

```
{1:F01TESTGB01AXXX0000000082}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111192}}
{4:
:20:TC82REF0082
:23B:CRED
:32A:260315USD2050,00
:50K:/11112222
JOHN SMITH
LONDON
:56A:IRVTUS3N
:57A:ABNAUS33
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `IntrmyAgt1/BICFI`=IRVTUS3N, `CdtrAgt/BICFI`=ABNAUS33, both cleanly separated. If this passes while TC24 failed, that confirms the bug is isolated to the clearing-code-splitting logic specifically, not the field-56/57 pairing in general.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC82REF0082</MsgId>
      <CreDtTm>2026-09-07T07:37:58.604Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC82REF0082</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111192</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">2050.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <IntrmyAgt1>
        <FinInstnId>
          <BICFI>IRVTUS3N</BICFI>
        </FinInstnId>
      </IntrmyAgt1>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>ABNAUS33</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---
---

# GROUP E — Beneficiary/Remittance Additional Cases (TC83–TC90)

-e 
---

## TC84 🟢 — Field 70, 4 lines of plain narrative near maximum length
**Tests:** Maximum unstructured remittance capacity — no codewords at all, just a long freeform payment description across all 4 available lines.

```
{1:F01TESTGB01AXXX0000000084}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111194}}
{4:
:20:TC84REF0084
:23B:CRED
:32A:260315GBP2300,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:PAYMENT FOR CONSULTING SERVICES
RENDERED DURING Q1 2026 PERIOD AS
PER CONTRACT NUMBER CT-2026-0091
DATED JANUARY 15TH 2026 THANK YOU
:71A:SHA
-}
```
**Expected / key checks:** Full 4-line narrative preserved in `RmtInf/Ustrd`, either as one concatenated string or 4 separate lines — either is fine, but no line should be dropped or truncated. `EndToEndId`=NOTPROVIDED (no `/ROC/` codeword present).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC84REF0084</MsgId>
      <CreDtTm>2026-09-07T07:38:06.217Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC84REF0084</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111194</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">2300.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
      <RmtInf>
        <Ustrd>PAYMENT FOR CONSULTING SERVICES RENDERED DURING Q1 2026 PERIOD AS PER CONTRACT NUMBER CT-2026-0091 DATED JANUARY 15TH 2026 THANK YOU</Ustrd>
      </RmtInf>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---

-e 
---

## TC89 🔴 — INVALID: 71A=BEN with 71G also present (forbidden combination, opposite angle from TC08/09)
**Tests:** Rule C14's BEN branch forbids 71G (only permits 71F) — the reverse of TC04's OUR-forbids-71F check. Never tested from this specific angle.

```
{1:F01TESTGB01AXXX0000000089}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111199}}
{4:
:20:TC89REF0089
:23B:CRED
:32A:260315GBP970,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:BEN
:71F:GBP10,00
:71G:GBP5,00
-}
```
**Expected / key checks:** Given the established validation-gap pattern (charge-field business rules aren't checked — see TC09/TC10), predict this translates silently with both `ChrgsInf` entries populated, rather than flagging that 71G shouldn't coexist with BEN.

**Your XML result:**
```
{"detail":{"error_type":"ValidationFailedException","stage":"validate","message":"Validation failed after retries. Errors: [[VR019] Rule C14: if field 71A is BEN, field 71G is not allowed (error code E15). - source field '71A'=BEN forbids source field '71G', but it is present]","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"done"},{"key":"validate","status":"error"}],"errors":["[VR019] Rule C14: if field 71A is BEN, field 71G is not allowed (error code E15). - source field '71A'=BEN forbids source field '71G', but it is present"],"warnings":[]}}
```

---

-e 
---

## TC96 🔴 — 23E with three codes in one field (SDVA + INTC + PHOB)
**Tests:** Extends the repeated-occurrence concatenation bug (confirmed with 2 values in TC43/44) to 3 values — checks whether the failure mode is the same, or degrades differently with more occurrences.

```
{1:F01TESTGB01AXXX0000000096}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111206}}
{4:
:20:TC96REF0096
:23B:SPRI
:23E:SDVA
:23E:INTC
:23E:PHOB
:32A:260315EUR1750,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Predict the same class of refusal as TC43/44, likely now showing all three values concatenated (`'SDVA\nINTC\nPHOB'`) in the error message — confirms the bug scales with occurrence count rather than being specific to exactly two.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC96REF0096</MsgId>
      <CreDtTm>2026-09-07T07:38:24.159Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC96REF0096</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111206</UETR>
      </PmtId>
      <PmtTpInf>
        <SvcLvl>
          <Cd>SDVA</Cd>
        </SvcLvl>
        <LclInstrm>
          <Prtry>SPRI</Prtry>
        </LclInstrm>
        <CtgyPurp>
          <Cd>INTC</Cd>
        </CtgyPurp>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1750.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Nm>JOHN SMITH</Nm>
        <PstlAdr>
          <AdrLine>LONDON</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>11112222</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>MARY JONES</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>33334444</Id>
          </Othr>
        </Id>
      </CdtrAcct>
      <InstrForCdtrAgt>
        <Cd>PHOB</Cd>
      </InstrForCdtrAgt>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```

---
---

# GROUP G — Structural/Envelope Robustness (TC97–TC100)

-e 
---

## TC99 🔴 — INVALID: 32A with no decimal comma at all
**Tests:** The amount subfield format always requires a decimal comma, even with nothing after it (as in TC59's JPY case). This value has no comma whatsoever — a basic syntax violation on the message's most important numeric field.

```
{1:F01TESTGB01AXXX0000000099}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111209}}
{4:
:20:TC99REF0099
:23B:CRED
:32A:260315GBP1000
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Should be rejected as malformed. Given TC48/TC58/TC63's pattern of no syntax-level validation, predict this is either misparsed (e.g. currency code accidentally absorbing digits) or passes through with an assumed/wrong decimal placement.

**Your XML result:**
```
{"detail":{"error_type":"TransformationException","stage":"convert","message":"Value '1000' is not a valid decimal for field 32A - SWIFT amount notation requires a decimal comma, even for a whole number (e.g. '1000,'), not a bare integer.","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"error"},{"key":"validate","status":"skipped"}]}}
```

---

-e 
---

## TC100 🟢 — "Clean" integration test: only fields independently confirmed to work
**Tests:** TC50 (the original kitchen sink) was blocked entirely by one unmapped field (26T) and never actually tested whether working features combine correctly. This message deliberately avoids every field already known to be broken or unmapped (13C, 26T, 51A, 53B/D, 54B, 55B, 56C/D, repeated 23E) and combines only what's been individually verified to work: 50A, a full 53A/54A/55A chain, 57A, 59A, genuine cross-currency (33B/36), field 70 with `/ROC/`, 71A=OUR with 71G, and a single non-repeated 23E code.

```
{1:F01TESTGB01AXXX0000000100}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111210}}
{4:
:20:TC100REF0100
:23B:CRED
:23E:INTC
:32A:260315GBP5000,00
:33B:USD6350,00
:36:0,78740
:50A:/99887766
DEUTDEFF
:53A:CHASUS33
:54A:IRVTUS3N
:55A:BNPAFRPP
:57A:ABNANL2A
:59A:/11224488
DEUTDEFF500
:70:/ROC/CLEANFINALTEST0100
:71A:OUR
:71G:GBP15,00
-}
```
**Expected / key checks:** This is the genuine integration signal that TC50 couldn't deliver. If every one of the individually-tested-and-passing fields above (50A from TC11, the 53/54/55 chain from TC23, 57A from TC26, 59A from TC31, cross-currency from TC03, `/ROC/` from TC37, OUR+71G from TC04) still works correctly *in combination*, this should produce a fully clean, complete pacs.008 with no errors and no missing elements — a genuine end-to-end success case for this campaign, if your isolation results hold.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC100REF0100</MsgId>
      <CreDtTm>2026-09-07T07:38:38.110Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>COVE</SttlmMtd>
        <InstgRmbrsmntAgt>
          <FinInstnId>
            <BICFI>CHASUS33</BICFI>
          </FinInstnId>
        </InstgRmbrsmntAgt>
        <InstdRmbrsmntAgt>
          <FinInstnId>
            <BICFI>IRVTUS3N</BICFI>
          </FinInstnId>
        </InstdRmbrsmntAgt>
        <ThrdRmbrsmntAgt>
          <FinInstnId>
            <BICFI>BNPAFRPP</BICFI>
          </FinInstnId>
        </ThrdRmbrsmntAgt>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC100REF0100</InstrId>
        <EndToEndId>CLEANFINALTEST0100</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111210</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
        <CtgyPurp>
          <Cd>INTC</Cd>
        </CtgyPurp>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">5000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <InstdAmt Ccy="USD">6350.00</InstdAmt>
      <XchgRate>0.78740</XchgRate>
      <ChrgBr>DEBT</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">15.00</Amt>
        <Agt>
          <FinInstnId>
            <BICFI>TESTGB02XXX</BICFI>
          </FinInstnId>
        </Agt>
      </ChrgsInf>
      <InstgAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </InstgAgt>
      <InstdAgt>
        <FinInstnId>
          <BICFI>TESTGB02XXX</BICFI>
        </FinInstnId>
      </InstdAgt>
      <Dbtr>
        <Id>
          <OrgId>
            <AnyBIC>DEUTDEFF</AnyBIC>
          </OrgId>
        </Id>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>99887766</Id>
          </Othr>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>TESTGB01XXX</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Id>
          <OrgId>
            <AnyBIC>DEUTDEFF500</AnyBIC>
          </OrgId>
        </Id>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>11224488</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
```
-e 

---

*End of retest set — 19 cases. Send completed results whenever ready.*