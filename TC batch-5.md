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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC41REF0041</MsgId>
      <CreDtTm>2026-09-07T12:11:13.571Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC41REF0041</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111151</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <SttlmTmReq>
        <CLSTm>09:15:00+01:00</CLSTm>
      </SttlmTmReq>
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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC42REF0042</MsgId>
      <CreDtTm>2026-09-07T12:11:21.847Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC42REF0042</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111152</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <SttlmTmIndctn>
        <DbtDtTm>2026-03-15T09:20:00+01:00</DbtDtTm>
        <CdtDtTm>2026-03-15T09:30:00+01:00</CdtDtTm>
      </SttlmTmIndctn>
      <SttlmTmReq>
        <CLSTm>09:15:00+01:00</CLSTm>
      </SttlmTmReq>
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
      <CreDtTm>2026-09-07T12:11:33.453Z</CreDtTm>
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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC44REF0044</MsgId>
      <CreDtTm>2026-09-07T12:11:41.203Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC44REF0044</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111154</UETR>
      </PmtId>
      <PmtTpInf>
        <SvcLvl>
          <Cd>SDVA</Cd>
        </SvcLvl>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">800.00</IntrBkSttlmAmt>
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
        <Cd>HOLD</Cd>
      </InstrForCdtrAgt>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>
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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC45REF0045</MsgId>
      <CreDtTm>2026-09-07T12:11:49.059Z</CreDtTm>
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
      <CreDtTm>2026-09-07T12:11:56.589Z</CreDtTm>
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
      <CreDtTm>2026-09-07T12:12:06.513Z</CreDtTm>
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
Validation failed after retries. Errors: [[VR015] Field 20 (Sender's Reference) must not start or end with '/' and must not contain '//' anywhere. - source field '20' value '/TC48REF0048' violates the required format (matches forbidden pattern ^/|/$|//)]
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
{"detail":{"error_type":"ValidationFailedException","stage":"validate","message":"Validation failed after retries. Errors: [[VR016] Field 32A's currency (IntrBkSttlmAmt/@Ccy) must not be a precious-metal ISO 4217 code (XAU, XAG, XPD, XPT). - CdtTrfTxInf.IntrBkSttlmAmt.@Ccy value 'XAU' is in the forbidden set for this field]","pipeline_steps":[{"key":"mapping","status":"done"},{"key":"parse","status":"done"},{"key":"convert","status":"done"},{"key":"validate","status":"error"}],"errors":["[VR016] Field 32A's currency (IntrBkSttlmAmt/@Ccy) must not be a precious-metal ISO 4217 code (XAU, XAG, XPD, XPT). - CdtTrfTxInf.IntrBkSttlmAmt.@Ccy value 'XAU' is in the forbidden set for this field"],"warnings":[]}}
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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC50REF0050</MsgId>
      <CreDtTm>2026-09-07T12:12:45.864Z</CreDtTm>
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

---

*End of Batch 5 (TC41–TC50) — all 50 test cases complete. Once this comes back, I can also put together a consolidated summary across all five batches if that'd be useful for your team.*