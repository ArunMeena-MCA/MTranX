# MT103 → pacs.008 Test Suite — Batch 4 (TC31–TC40)
## Focus: Beneficiary (59/59A/59F) & Remittance Information (field 70 codewords)

**Note:** All 10 cases here are new coverage — no repeats of 59 (no-letter, already exercised in Batches 1–3), and no field-70 case has been tested before this batch at all.

---

## TC31 🟢 — 59A: BIC-only Beneficiary
**Tests:** The Creditor-side mirror of TC11's 50A — never tested on the beneficiary before. Option A gives only account + BIC, no name/address possible.

```
{1:F01TESTGB01AXXX0000000031}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111141}}
{4:
:20:TC31REF0031
:23B:CRED
:32A:260315EUR1750,00
:50K:/11112222
JOHN SMITH
LONDON
:59A:/98765432
DEUTDEFF
:71A:SHA
-}
```
**Expected / key checks:** `Cdtr/Id/OrgId/AnyBIC`=DEUTDEFF, **no** `Cdtr/Nm`. `CdtrAcct/Id/Othr/Id`=98765432.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC31REF0031</MsgId>
      <CreDtTm>2026-09-04T10:43:41.371Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC31REF0031</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111141</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
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
        <Id>
          <OrgId>
            <AnyBIC>DEUTDEFF</AnyBIC>
          </OrgId>
        </Id>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>98765432</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC32 🟢 — 59F structured, country-only address line (no town given)
**Tests:** Per the spec, number 3's town detail is optional — *"the first occurrence of number 3 must be followed by... the ISO country code and, optionally, additional details."* A country code alone with no town is structurally valid. Checks the engine doesn't require `TwnNm` to exist before it will populate `Ctry`.

```
{1:F01TESTGB01AXXX0000000032}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111142}}
{4:
:20:TC32REF0032
:23B:CRED
:32A:260315SGD3400,00
:50K:/11112222
JOHN SMITH
LONDON
:59F:/11223344
1/GLOBAL EXPORTS LTD
2/456 COMMERCE STREET
3/SG
:71A:SHA
-}
```
**Expected / key checks:** `Cdtr/Nm`=GLOBAL EXPORTS LTD, `Ctry`=SG, **no `TwnNm` element** (none given — check it isn't left as an empty tag). `CdtrAcct/Id/Othr/Id`=11223344.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC32REF0032</MsgId>
      <CreDtTm>2026-09-04T10:45:16.998Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC32REF0032</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111142</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="SGD">3400.00</IntrBkSttlmAmt>
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
        <Nm>GLOBAL EXPORTS LTD</Nm>
        <PstlAdr>
          <AdrLine>456 COMMERCE STREET</AdrLine>
        </PstlAdr>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>11223344</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC33 🔴 — INVALID 59F: mandatory number-3 line entirely missing
**Tests:** The spec is explicit: *"A line starting with number 3 must be present"* in option F. This message only has name and address, no country line at all — structurally incomplete for STP purposes.

```
{1:F01TESTGB01AXXX0000000033}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111143}}
{4:
:20:TC33REF0033
:23B:CRED
:32A:260315EUR980,00
:50K:/11112222
JOHN SMITH
LONDON
:59F:/55667788
1/INCOMPLETE BENEFICIARY LTD
2/UNKNOWN STREET
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch the missing mandatory country line and reject/flag, or translate with `Cdtr/Nm` populated and simply no `Ctry`/`PstlAdr` country info at all (silent gap, consistent with the validation pattern seen so far)?

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC33REF0033</MsgId>
      <CreDtTm>2026-09-04T10:45:46.863Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC33REF0033</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111143</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">980.00</IntrBkSttlmAmt>
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
        <Nm>INCOMPLETE BENEFICIARY LTD</Nm>
        <PstlAdr>
          <AdrLine>UNKNOWN STREET</AdrLine>
        </PstlAdr>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>55667788</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC34 🟢 — 59F with repeated Name line (number "1" twice — two-part business name)
**Tests:** Numbers 1, 2, 3 may each repeat up to twice — this uses a real example straight from the SWIFT field spec itself (a two-line organisation name). Distinct from TC14's repeated *address* line — this repeats the *name* line instead.

```
{1:F01TESTGB01AXXX0000000034}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111144}}
{4:
:20:TC34REF0034
:23B:CRED
:32A:260315USD2100,00
:50K:/11112222
JOHN SMITH
LONDON
:59F:/12345678
1/DEPT OF PROMOTION OF SPICY FISH
1/CENTER FOR INTERNATIONALISATION
3/CN
:71A:SHA
-}
```
**Expected / key checks:** **Both** name fragments must appear — not just "DEPT OF PROMOTION OF SPICY FISH" with "CENTER FOR INTERNATIONALISATION" silently lost (the exact failure mode seen in TC14 for repeated address lines — this checks whether that bug also affects the *name* line, or was specific to address lines only).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC34REF0034</MsgId>
      <CreDtTm>2026-09-04T10:46:46.548Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC34REF0034</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111144</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">2100.00</IntrBkSttlmAmt>
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
        <Nm>DEPT OF PROMOTION OF SPICY FISH</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id>
          <Othr>
            <Id>12345678</Id>
          </Othr>
        </Id>
      </CdtrAcct>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC35 🟢 — Field 70 with `/INV/` (Invoice) codeword
**Tests:** First of five field-70 codeword variants — none tested in any prior batch. Structured remittance reference embedded in otherwise-unstructured field 70.

```
{1:F01TESTGB01AXXX0000000035}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111145}}
{4:
:20:TC35REF0035
:23B:CRED
:32A:260315EUR620,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:/INV/2026-04512-A
:71A:SHA
-}
```
**Expected / key checks:** `RmtInf/Ustrd` should contain the invoice reference. Since no `/ROC/` codeword is present, `EndToEndId` should still read `NOTPROVIDED` (per the rule established earlier — only `/ROC/` triggers `EndToEndId`, no other field-70 codeword does).

**Your XML result:**
```
Couldn't read this message
SemanticDecompositionGapException
Cannot semantically decompose field '70' (value=/INV/2026-04512-A): The raw value starts with /INV/ instead of the required /ROC/ prefix specified for extracting the EndToEndId.
```

---

## TC36 🟢 — Field 70 with `/RFB/` (Reference for Beneficiary) codeword
**Tests:** A short beneficiary-facing reference — the second codeword variant.

```
{1:F01TESTGB01AXXX0000000036}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111146}}
{4:
:20:TC36REF0036
:23B:CRED
:32A:260315GBP340,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:/RFB/PAYMENT REF 998877
:71A:SHA
-}
```
**Expected / key checks:** `RmtInf/Ustrd` carries the RFB reference text. `EndToEndId`=NOTPROVIDED (RFB doesn't trigger it, only ROC does).

**Your XML result:**
```
Cannot semantically decompose field '70' (value=/RFB/PAYMENT REF 998877): The raw value starts with /RFB/ instead of the required /ROC/ pattern for EndToEndId.
```

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
      <CreDtTm>2026-09-04T10:53:27.757Z</CreDtTm>
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
      <RmtInf>
        <Ustrd>/ROC/CUSTREF20260315AB</Ustrd>
      </RmtInf>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

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
      <CreDtTm>2026-09-04T10:54:50.793Z</CreDtTm>
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

## TC39 🟢 — Field 70 with ISO 11649 RF Creditor Reference (no codeword, first line only)
**Tests:** The spec's STP usage rule: *"when an ISO 11649 Creditor Reference is present in this field it must be on the first line, without any characters preceding it, and it must be the only information on that line."* No `/CODE/` prefix at all here — just the raw RF reference.

```
{1:F01TESTGB01AXXX0000000039}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111149}}
{4:
:20:TC39REF0039
:23B:CRED
:32A:260315EUR560,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:RF18539007547034
:71A:SHA
-}
```
**Expected / key checks:** Ideally recognized as a structured creditor reference (`RmtInf/Strd/CdtrRefInf/Ref`=RF18539007547034, with `Tp/CdOrPrtry/Cd`=SCOR) rather than dumped as plain unstructured text into `Ustrd`. If your engine doesn't distinguish this from ordinary free text, confirm it at least preserves the value somewhere rather than corrupting it.

**Your XML result:**
```
Cannot semantically decompose field '70' (value=RF18539007547034): The raw value does not begin with the required prefix /ROC/ as specified by the pattern description.
```

---

## TC40 🟢 — Field 70 with `/TSU/` (Trade Services Utility) codeword
**Tests:** The most structurally complex field-70 codeword — three sub-parts (TSU transaction ID / invoice number / amount paid) packed into one slash-delimited string.

```
{1:F01TESTGB01AXXX0000000040}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111150}}
{4:
:20:TC40REF0040
:23B:CRED
:32A:260315USD2600,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:70:/TSU/00000089963-0820-01/ABC-15/256214,
:71A:SHA
-}
```
**Expected / key checks:** At minimum, the full TSU string should land intact in `RmtInf/Ustrd` (this codeword has no dedicated structured pacs.008 element, unlike `/ROC/`). Check the three comma/slash-delimited sub-parts (transaction ID, invoice number, amount) aren't truncated or split incorrectly by a parser that isn't expecting embedded slashes within the value.

**Your XML result:**
```
Cannot semantically decompose field '70' (value=/TSU/00000089963-0820-01/ABC-15/256214,): The raw value does not begin with the required /ROC/ prefix pattern specified for extracting the EndToEndId.
```

---

*End of Batch 4 (TC31–TC40). Send completed results whenever ready, or say "next batch" for TC41–TC50 (Time indication, instruction codes, regulatory reporting, and full "kitchen sink" integration cases).*