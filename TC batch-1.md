# MT103 → pacs.008 Test Suite (SWIFT 2026 / CBPR+ Standards)

**Purpose:** Validate an MT103-to-pacs.008.001.08 translation engine against SWIFT Standards MT (Nov 2021 field spec, still current) and CBPR+ Usage Guidelines (SR2025, in force since Nov 2025).

**How to use this doc:**
1. Run each MT103 below through your translation engine.
2. Paste the resulting pacs.008 XML into the **"Your XML Result"** slot under each test case.
3. Send the completed batch back — I'll verify each one against the "Expected / Key Checks" column and flag deviations.

**Coverage plan (50 cases, 5 batches of 10):**
| Batch | Focus |
|---|---|
| 1 (this one) | Amounts (32A/33B/36) and Charge Bearer (71A/71F/71G) — incl. negative/invalid cases |
| 2 | Debtor side — 50A/50F/50K, 51A, 52A/52D |
| 3 | Correspondents & intermediaries — 53a/54a/55a/56a/57a, incl. conditional-rule violations |
| 4 | Beneficiary & remittance — 59/59A/59F, field 70 codewords |
| 5 | Time indication, instruction codes, regulatory reporting, and full "kitchen sink" integration cases |

Legend: 🟢 = valid message, engine should translate cleanly. 🔴 = deliberately invalid/edge input — tests how your engine *handles* a spec violation (reject, flag, or best-effort translate). Note which behavior your engine chose; there's no single "correct" XML for red cases, only a correct *diagnosis*.

---

## TC01 🟢 — Minimal mandatory-fields-only baseline
**Tests:** Absolute floor of a valid MT103 — nothing optional present. Establishes your baseline mapping before any edge case is layered on.

```
{1:F01TESTGB01AXXX0000000001}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111111}}
{4:
:20:TC01REF0001
:23B:CRED
:32A:260315GBP2500,00
:50K:/11112222
JOHN SMITH
1 HIGH STREET
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `SttlmMtd` derived (not verifiable from text alone — flag per earlier discussion). `PmtTpInf/LclInstrm/Prtry`=CRED. `IntrBkSttlmAmt`=2500.00 GBP, `IntrBkSttlmDt`=2026-03-15. No `InstdAmt`/`XchgRate` (33B/36 absent — correct, nothing to populate). `Dbtr`/`DbtrAcct` from 50K → `AdrLine` (unstructured). `Cdtr`/`CdtrAcct` from 59 → no `PstlAdr` (no address lines given). `ChrgBr`=SHAR, no `ChrgsInf` (neither 71F nor 71G present — correct for baseline SHA). `DbtrAgt` defaults to Sender (no 52a present).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC01REF0001</MsgId>
      <CreDtTm>2026-09-04T08:31:38.667Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC01REF0001</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111111</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">2500.00</IntrBkSttlmAmt>
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
          <AdrLine>1 HIGH STREET</AdrLine>
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

## TC02 🟢 — 33B present, same currency as 32A → field 36 correctly absent
**Tests:** Network Validated Rule **C1** — when 33B's currency equals 32A's currency, field 36 is *not allowed*. This checks your engine doesn't spuriously invent an exchange rate of 1.0 or leave a dangling element.

```
{1:F01TESTGB01AXXX0000000002}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111112}}
{4:
:20:TC02REF0002
:23B:CRED
:32A:260315GBP1000,00
:33B:GBP1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `InstdAmt Ccy="GBP">1000.00` present. **`XchgRate` element must be absent entirely** — not `1.0`, not empty, just not present. If your engine emits `<XchgRate>1</XchgRate>` or similar, that's a defect.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC02REF0002</MsgId>
      <CreDtTm>2026-09-04T08:32:22.234Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC02REF0002</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111112</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <InstdAmt Ccy="GBP">1000.00</InstdAmt>
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

## TC03 🟢 — 33B present, different currency, 36 present (genuine cross-currency)
**Tests:** The mainstream cross-currency path — 33B ≠ 32A currency, so 36 is mandatory (C1) and must carry a real rate.

```
{1:F01TESTGB01AXXX0000000003}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111113}}
{4:
:20:TC03REF0003
:23B:CRED
:32A:260315GBP500,00
:33B:USD630,50
:36:0,79325
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `IntrBkSttlmAmt Ccy="GBP">500.00`, `InstdAmt Ccy="USD">630.50`, `XchgRate>0.79325`. Sanity: 630.50 × 0.79325 ≈ 500.28 — close to 32A (small residual is charges-related, not an error, since 71A=SHA here with no explicit 71F/71G shown).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC03REF0003</MsgId>
      <CreDtTm>2026-09-04T08:32:58.692Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC03REF0003</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111113</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">500.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <InstdAmt Ccy="USD">630.50</InstdAmt>
      <XchgRate>0.79325</XchgRate>
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

## TC04 🟢 — Charges OUR, with 71G present (no 71F)
**Tests:** Rule **C14** — if 71A=OUR, then 71F is *not allowed* and 71G is *optional*. Confirms `ChrgBr`=DEBT and that Receiver's charges land correctly in `ChrgsInf`.

```
{1:F01TESTGB01AXXX0000000004}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111114}}
{4:
:20:TC04REF0004
:23B:CRED
:32A:260315GBP1010,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:OUR
:71G:GBP10,00
-}
```
**Expected / key checks:** `ChrgBr`=DEBT. One `ChrgsInf` block with `Amt Ccy="GBP">10.00`. No agent BIC is textually given for 71G's payee in this input — check how your engine populates (or omits) `ChrgsInf/Agt` when the MT doesn't name one explicitly.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC04REF0004</MsgId>
      <CreDtTm>2026-09-04T08:33:45.117Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC04REF0004</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111114</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1010.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>DEBT</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">10.00</Amt>
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

## TC05 🟢 — Charges SHA, with 71F present (optional under SHA)
**Tests:** Rule **C14** second branch — under SHA, 71F is optional (unlike BEN where it's mandatory). Confirms engine doesn't wrongly treat 71F-under-SHA as an error.

```
{1:F01TESTGB01AXXX0000000005}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111115}}
{4:
:20:TC05REF0005
:23B:CRED
:32A:260315GBP995,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
:71F:GBP5,00
-}
```
**Expected / key checks:** `ChrgBr`=SHAR. `ChrgsInf/Amt Ccy="GBP">5.00`. No 71G expected (SHA forbids it per C14 — correctly absent from input too).

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC05REF0005</MsgId>
      <CreDtTm>2026-09-04T08:36:54.685Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC05REF0005</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111115</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">995.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>SHAR</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">5.00</Amt>
        <Agt>
          <FinInstnId>
            <BICFI>TESTGB01XXX</BICFI>
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

## TC06 🟢 — Charges BEN, single 71F occurrence (mandatory under BEN)
**Tests:** Rule **C14** third branch — BEN requires *at least one* 71F, forbids 71G. Baseline single-bank-deduction case.

```
{1:F01TESTGB01AXXX0000000006}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111116}}
{4:
:20:TC06REF0006
:23B:CRED
:32A:260315GBP988,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:BEN
:71F:GBP12,00
-}
```
**Expected / key checks:** `ChrgBr`=CRED. One `ChrgsInf/Amt`=12.00 GBP.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC06REF0006</MsgId>
      <CreDtTm>2026-09-04T08:40:14.066Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC06REF0006</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111116</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">988.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>CRED</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">12.00</Amt>
        <Agt>
          <FinInstnId>
            <BICFI>TESTGB01XXX</BICFI>
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

## TC07 🟢 — Charges BEN, multiple repetitive 71F (multi-bank deduction chain)
**Tests:** Field 71F's repetitive nature — per the spec, "the first occurrence specifies the charges of the first bank... the last occurrence always gives the Sender's charges." Confirms your engine emits **multiple** `ChrgsInf` blocks, in order, not just the last one.

```
{1:F01TESTGB01AXXX0000000007}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111117}}
{4:
:20:TC07REF0007
:23B:CRED
:32A:260315GBP970,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:BEN
:71F:GBP15,00
:71F:GBP15,00
-}
```
**Expected / key checks:** **Two** separate `ChrgsInf` elements, each `Amt Ccy="GBP">15.00`, in the same order as the MT. A single collapsed/summed `ChrgsInf` (e.g. one block of 30.00) is a defect.

**Your XML result:**
```
Conversion stopped
TransformationException
Value 'GBP15,00
GBP15,00' does not match extract_pattern for field 71F
```

---

## TC08 🔴 — INVALID: 33B ≠ 32A currency but field 36 missing (violates C1)
**Tests:** How your engine handles a network-rule violation on **input**. Per rule C1, if 33B's currency differs from 32A's, field 36 is *mandatory* — this message is malformed (a real FIN network gateway would reject it with error D75 before it ever reached a translator). There's no "correct" pacs.008 for this one — the correct behavior is **rejection or an explicit validation error**, not a best-effort guess at a missing exchange rate.

```
{1:F01TESTGB01AXXX0000000008}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111118}}
{4:
:20:TC08REF0008
:23B:CRED
:32A:260315GBP500,00
:33B:USD630,50
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does your engine (a) reject outright, (b) flag a validation warning but still translate, or (c) silently translate with `InstdAmt` present and `XchgRate` simply omitted? Options (a)/(b) are defensible; (c) is a silent-failure risk worth knowing about.

**Your XML result:**
```
Validation failed
ValidationFailedException
Validation failed after retries. Errors: [[VR001] If Instructed Amount is present and its currency differs from the Interbank Settlement Amount currency, Exchange Rate must be present. - CdtTrfTxInf.XchgRate is required because CdtTrfTxInf.InstdAmt (Ccy=USD) differs from CdtTrfTxInf.IntrBkSttlmAmt (Ccy=GBP), but it is absent]
```

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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC09REF0009</MsgId>
      <CreDtTm>2026-09-04T08:43:00.263Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC09REF0009</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111119</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>CRED</ChrgBr>
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

## TC10 🔴 — INVALID: 71G amount = 0 (violates Network Validated Rule D57)
**Tests:** D57 explicitly states *"Amount must not equal zero"* for field 71G. A zero-charge line has no informational value and is a malformed input.

```
{1:F01TESTGB01AXXX0000000010}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111120}}
{4:
:20:TC10REF0010
:23B:CRED
:32A:260315GBP1000,00
:50K:/11112222
JOHN SMITH
LONDON
:59:/33334444
MARY JONES
:71A:OUR
:71G:GBP0,00
-}
```
**Expected / key checks:** Does your engine reject/flag the zero amount, or does it happily emit `<ChrgsInf><Amt Ccy="GBP">0.00</Amt></ChrgsInf>`? The latter is technically well-formed XML but propagates an invalid value downstream — worth knowing whether your validation layer catches this before or only after translation.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC10REF0010</MsgId>
      <CreDtTm>2026-09-04T08:43:46.871Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC10REF0010</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111120</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1000.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>DEBT</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">0.00</Amt>
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

*End of Batch 1 (TC01–TC10). Send me the completed XML results whenever ready, or say "next batch" for TC11–TC20 (Debtor side: 50A/50F/50K, 51A, 52A/52D).*