# MT103 → pacs.008 Test Suite — Batch 5 (TC41–TC50, FINAL)
## Focus: Time indication (13C), instruction codes (23E), transaction type (26T), sender-to-receiver info (72), regulatory reporting (77B), and a full integration case

**Note:** All new coverage. TC42 deliberately re-uses the repeated-field pattern that broke TC07/TC14/TC34, applied to a third field — worth watching closely.

---

## TC41 🟢 — 13C with single `/CLSTIME/` codeword
**Tests:** Baseline time-indication field, never tested before this batch.

```
{1:F01TESTGB01AXXX0000000041}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111151}}
{4:
:20:TC41REF0041
:13C:/CLSTIME/0915+0100
:23B:CRED
:32A:260315USD1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `SttlmTmReq/CLSTm` populated with the time/offset (0915+0100 → likely normalized to `09:15:00+01:00`).

**Your XML result:**
```
LLM-assisted conversion for field 13C returned low confidence: The raw source value contains /CLSTIME/0915+0100, but the notes specifically state that the expected codeword for CdtDtTm is RNCTIME and that the field is derived from RNCTIME. Additionally, there is a structural gap where CdtDtTm is an ISODateTime requiring a date, but only a time-only raw value is available.. Refusing to use a low-confidence value - tighten the mapping doc's notes/edge_cases for this field.
```

---

## TC42 🟢 — 13C repeated three times, all three codewords (CLSTIME + RNCTIME + SNDTIME)
**Tests:** Field 13C is explicitly repetitive. This is the third field where we've now tested repetition (after 71F in TC07 and 50F/59F line-numbers in TC14/TC34) — checks whether the "only first occurrence survives" bug generalizes here too, or is specific to the earlier cases.

```
{1:F01TESTGB01AXXX0000000042}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111152}}
{4:
:20:TC42REF0042
:13C:/CLSTIME/0915+0100
:13C:/RNCTIME/0930+0100
:13C:/SNDTIME/0920+0100
:23B:CRED
:32A:260315USD1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** All three should surface: `SttlmTmReq/CLSTm` (from CLSTIME), `SttlmTmIndctn/CdtDtTm` (from RNCTIME), `SttlmTmIndctn/DbtDtTm` (from SNDTIME) — three distinct values, not just the first one processed and the other two silently dropped.

**Your XML result:**
```
LLM-assisted conversion for field 13C returned low confidence: Target field DbtDtTm expects a full ISODateTime (date + time), but 13C provides only a time component without a sourced rule for which date to combine it with, making this entry unverified per the reference doc notes.. Refusing to use a low-confidence value - tighten the mapping doc's notes/edge_cases for this field.
```

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
LLM-assisted conversion for field 23E returned low confidence: The decision procedure explicitly states that if the 23E value is SDVA, INTC, or CORT, it should not be copied into InstrInf (SDVA goes to SvcLvl, INTC/CORT goes to CtgyPurp). Since the raw source value is 'SDVA
INTC', it matches these excluded categories and cannot be mapped to InstrInf.. Refusing to use a low-confidence value - tighten the mapping doc's notes/edge_cases for this field.
```

---

## TC44 🔴 — INVALID: 23E forbidden combination (SDVA + HOLD, violates Rule D67)
**Tests:** The spec explicitly forbids SDVA with HOLD together (among other listed pairs). Tests whether this specific combinatorial rule is validated.

```
{1:F01TESTGB01AXXX0000000044}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111154}}
{4:
:20:TC44REF0044
:23B:CRED
:23E:SDVA
:23E:HOLD
:32A:260315EUR800,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch the forbidden SDVA+HOLD pairing, or translate both codes through regardless?

**Your XML result:**
```
LLM-assisted conversion for field 23E returned low confidence: The source value 'SDVA
HOLD' triggers the rule for SDVA (which routes to SvcLvl per the notes) and HOLD, making this value ambiguous or invalid according to the strict decision procedure, and it cannot be safely copied verbatim.. Refusing to use a low-confidence value - tighten the mapping doc's notes/edge_cases for this field.
```

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
No mapping rule for source field '26T'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.
```

---

## TC46 🟢 — 72: Sender to Receiver Information with `/INS/` and `/ACC/` codes
**Tests:** Structured instruction field with two distinct coded lines — checks both are captured, not just the first (same repetition-family concern, different field again).

```
{1:F01TESTGB01AXXX0000000046}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111156}}
{4:
:20:TC46REF0046
:23B:CRED
:32A:260315EUR900,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
:72:/INS/ABNANL2A
/ACC/PLEASE CREDIT URGENTLY
-}
```
**Expected / key checks:** Both the `/INS/` (instructing institution BIC) and `/ACC/` (instruction for account-with-institution) content should appear — likely as separate structured or narrative elements. Confirm neither is lost.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC46REF0046</MsgId>
      <CreDtTm>2026-09-04T11:13:22.179Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC46REF0046</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111156</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">900.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <PrvsInstgAgt1>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </PrvsInstgAgt1>
      <PrvsInstgAgt2>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </PrvsInstgAgt2>
      <PrvsInstgAgt3>
        <FinInstnId>
          <BICFI>ABNANL2A</BICFI>
        </FinInstnId>
      </PrvsInstgAgt3>
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
        <InstrInf>PLEASE CREDIT URGENTLY</InstrInf>
      </InstrForCdtrAgt>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC47 🟢 — 77B: Regulatory Reporting with `/BENEFRES/`
**Tests:** Never tested before — regulatory residence code for the beneficiary.

```
{1:F01TESTGB01AXXX0000000047}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111157}}
{4:
:20:TC47REF0047
:23B:CRED
:32A:260315EUR1100,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
:77B:/BENEFRES/BE//MEILAAN 1, 9000 GENT
-}
```
**Expected / key checks:** `RgltryRptg` populated with country code BE and the address detail — this is the field most likely to be entirely unmapped, given the pattern so far (compare to 51A/53B/53D). If it's unmapped, does the engine reject the whole message (like 51A/53B/53D did) or ignore gracefully?

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC47REF0047</MsgId>
      <CreDtTm>2026-09-04T11:13:54.314Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC47REF0047</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111157</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1100.00</IntrBkSttlmAmt>
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
      <RgltryRptg>
        <DbtCdtRptgInd>CRED</DbtCdtRptgInd>
        <Dtls>
          <Ctry>BE</Ctry>
          <Inf>/BENEFRES/BE//MEILAAN 1, 9000 GENT</Inf>
        </Dtls>
      </RgltryRptg>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC48 🔴 — INVALID: Field 20 starts with a slash (violates Rule T26)
**Tests:** Rule T26 — field 20 *"must not start or end with a slash '/' and must not contain two consecutive slashes."* A basic syntax-level violation on the single most fundamental field in the message.

```
{1:F01TESTGB01AXXX0000000048}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111158}}
{4:
:20:/TC48REF0048
:23B:CRED
:32A:260315EUR700,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** This is the most basic possible syntax violation — if any rule gets caught, it should be this one. If it doesn't, that's a meaningful signal about how little input sanitization happens before translation.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>/TC48REF0048</MsgId>
      <CreDtTm>2026-09-04T11:14:54.372Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>/TC48REF0048</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111158</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">700.00</IntrBkSttlmAmt>
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

## TC49 🔴 — INVALID: 32A with forbidden commodity currency (XAU, violates Rule C08)
**Tests:** Rule C08 explicitly forbids XAU/XAG/XPD/XPT in field 32A — these are precious-metal "currency" codes reserved for Category 6 commodities messages, not customer credit transfers.

```
{1:F01TESTGB01AXXX0000000049}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111159}}
{4:
:20:TC49REF0049
:23B:CRED
:32A:260315XAU1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch the forbidden currency code, or does it happily emit `<IntrBkSttlmAmt Ccy="XAU">`? XAU is a valid ISO 4217 code in general (it's gold), so a generic "is this a real currency" check would pass it — only a rule specifically targeting this message type would catch it.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC49REF0049</MsgId>
      <CreDtTm>2026-09-04T11:15:12.587Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC49REF0049</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111159</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="XAU">1000.00</IntrBkSttlmAmt>
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
No mapping rule for source field '26T'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.
```

---

*End of Batch 5 (TC41–TC50) — all 50 test cases complete. Once this comes back, I can also put together a consolidated summary across all five batches if that'd be useful for your team.*