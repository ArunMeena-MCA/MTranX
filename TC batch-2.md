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
      <CreDtTm>2026-09-04T09:12:57.633Z</CreDtTm>
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
      <CreDtTm>2026-09-04T09:10:50.701Z</CreDtTm>
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
      <CreDtTm>2026-09-04T09:13:25.733Z</CreDtTm>
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
      <CreDtTm>2026-09-04T09:14:17.608Z</CreDtTm>
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
      <CreDtTm>2026-09-04T09:14:56.255Z</CreDtTm>
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
      <CreDtTm>2026-09-04T09:15:32.368Z</CreDtTm>
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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC07REF0007</MsgId>
      <CreDtTm>2026-09-04T09:17:01.597Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC07REF0007</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111117</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">970.00</IntrBkSttlmAmt>
      <IntrBkSttlmDt>2026-03-15</IntrBkSttlmDt>
      <ChrgBr>CRED</ChrgBr>
      <ChrgsInf>
        <Amt Ccy="GBP">15.00</Amt>
        <Agt>
          <FinInstnId>
            <BICFI>TESTGB01XXX</BICFI>
          </FinInstnId>
        </Agt>
      </ChrgsInf>
      <ChrgsInf>
        <Amt Ccy="GBP">15.00</Amt>
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
      <CreDtTm>2026-09-04T09:19:57.352Z</CreDtTm>
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
Validation failed after retries. Errors: [[VR009] Field 71G (Receiver's Charges) amount must not equal zero, per SWIFT Network Validated Rule D57. - CdtTrfTxInf.ChrgsInf#1.Amt=0.00 must not equal zero]
```

---

*End of Batch 1 (TC01–TC10).*

---
---

# Batch 2 — Debtor side: 50A/50F/50K, 51A, 52A/52D

## TC11 🟢 — 50A: BIC-only Ordering Customer
**Tests:** Structural parallel to 59A (established earlier) — option A gives *only* account + BIC, no name/address possible. Confirms Debtor identification via `AnyBIC` mirrors what we verified for Creditor.

```
{1:F01TESTGB01AXXX0000000011}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111121}}
{4:
:20:TC11REF0011
:23B:CRED
:32A:260315EUR1500,00
:50A:/12345678
DEUTDEFF
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `Dbtr/Id/OrgId/AnyBIC`=DEUTDEFF, **no** `Dbtr/Nm` (option A has no name field). `DbtrAcct/Id/Othr/Id`=12345678.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC11REF0011</MsgId>
      <CreDtTm>2026-09-04T09:27:50.337Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC11REF0011</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111121</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
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
        <Id>
          <OrgId>
            <AnyBIC>DEUTDEFF</AnyBIC>
          </OrgId>
        </Id>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>12345678</Id>
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

## TC12 🟢 — 50F: basic structured (Name / Address / Country+Town)
**Tests:** Baseline structured-option debtor. Per the SWIFT MT103 field spec, structured "F" options resolve to **structured postal-address elements**, not `AdrLine` — this checks that distinction (established for 59F) holds symmetrically for 50F.

```
{1:F01TESTGB01AXXX0000000012}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111122}}
{4:
:20:TC12REF0012
:23B:CRED
:32A:260315GBP2000,00
:50F:/23456789
1/JANE DOE
2/221B BAKER STREET
3/GB/LONDON
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `Dbtr/Nm`=JANE DOE. `PstlAdr` should carry **structured** elements — `StrtNm`="221B BAKER STREET" (or split further), `TwnNm`="LONDON", `Ctry`="GB" — **not** `AdrLine`. `DbtrAcct/Id/Othr/Id`=23456789.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC12REF0012</MsgId>
      <CreDtTm>2026-09-04T09:30:08.708Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC12REF0012</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111122</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">2000.00</IntrBkSttlmAmt>
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
        <Nm>JANE DOE</Nm>
        <PstlAdr>
          <TwnNm>LONDON</TwnNm>
          <Ctry>GB</Ctry>
          <AdrLine>221B BAKER STREET</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>23456789</Id>
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

## TC13 🟢 — 50F with identity-document Party Identifier (CCPT — Passport)
**Tests:** A structural nuance easy to get wrong: when subfield 1 (Party Identifier) uses the `(Code)(Country)(Identifier)` format instead of `/Account`, **no account is present at all** — the two are mutually exclusive per the spec's line-format rule. This checks whether the engine (a) correctly omits `DbtrAcct` entirely, and (b) captures the passport identity data somewhere rather than silently dropping it.

```
{1:F01TESTGB01AXXX0000000013}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111123}}
{4:
:20:TC13REF0013
:23B:CRED
:32A:260315USD800,00
:50F:CCPT/US/AB1234567
1/JOHN DOE
2/5TH AVENUE 100
3/US/NEW YORK
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `DbtrAcct` should be **absent** (no account was given — `CCPT/US/AB1234567` occupies the identifier slot, not an account). The passport identity (code CCPT, country US, number AB1234567) should land somewhere meaningful — typically `Dbtr/Id/PrvtId/Othr` with a scheme/proprietary code indicating passport — rather than being silently discarded or misread as an account number.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC13REF0013</MsgId>
      <CreDtTm>2026-09-04T09:31:26.861Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC13REF0013</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111123</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">800.00</IntrBkSttlmAmt>
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
        <Nm>JOHN DOE</Nm>
        <PstlAdr>
          <TwnNm>NEW YORK</TwnNm>
          <Ctry>US</Ctry>
          <AdrLine>5TH AVENUE 100</AdrLine>
        </PstlAdr>
      </Dbtr>
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

## TC14 🟢 — 50F with repeated Address Line (number "2" twice)
**Tests:** The spec explicitly permits numbers 1, 2, and 3 to repeat (max twice each) — e.g. a two-line street address. Checks the engine captures **both** occurrences rather than the second overwriting the first.

```
{1:F01TESTGB01AXXX0000000014}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111124}}
{4:
:20:TC14REF0014
:23B:CRED
:32A:260315EUR3000,00
:50F:/34567890
1/ROBERT KLEIN
2/INDUSTRIESTRASSE 45
2/GEBAUDE C, 3RD FLOOR
3/DE/MUNICH
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Both address fragments ("INDUSTRIESTRASSE 45" and "GEBAUDE C, 3RD FLOOR") must appear in the output — whether as two `AdrLine`/sub-elements or concatenated, **not with the first line lost**. `TwnNm`=MUNICH, `Ctry`=DE.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC14REF0014</MsgId>
      <CreDtTm>2026-09-04T09:34:26.385Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC14REF0014</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111124</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">3000.00</IntrBkSttlmAmt>
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
        <Nm>ROBERT KLEIN</Nm>
        <PstlAdr>
          <TwnNm>MUNICH</TwnNm>
          <Ctry>DE</Ctry>
          <AdrLine>INDUSTRIESTRASSE 45</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>34567890</Id>
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

## TC15 🟢 — 50F with Date and Place of Birth (numbers 4 & 5, valid pairing)
**Tests:** Numbers 4 (DOB) and 5 (place of birth) are a mandatory pair — spec: *"Number 4 must not be used without number 5 and vice versa."* This is the **valid** pairing; TC16 tests the violation.

```
{1:F01TESTGB01AXXX0000000015}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111125}}
{4:
:20:TC15REF0015
:23B:CRED
:32A:260315EUR1200,00
:50F:/45678901
1/MARIA GARCIA
2/CALLE MAYOR 5
3/ES/MADRID
4/19850615
5/ES/MADRID
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Birth date/place should map to `Dbtr/Id/PrvtId/DtAndPlcOfBirth` — `BirthDt`=1985-06-15, `CityOfBirth`=MADRID, `CtryOfBirth`=ES — if your engine supports this ISO element. If it doesn't, confirm it at least doesn't silently corrupt other fields trying to parse it.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC15REF0015</MsgId>
      <CreDtTm>2026-09-04T09:35:45.034Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC15REF0015</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111125</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1200.00</IntrBkSttlmAmt>
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
        <Nm>MARIA GARCIA</Nm>
        <PstlAdr>
          <TwnNm>MADRID</TwnNm>
          <Ctry>ES</Ctry>
          <AdrLine>CALLE MAYOR 5</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>45678901</Id>
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

## TC16 🔴 — INVALID 50F: number 4 (DOB) present without number 5 (place of birth)
**Tests:** Direct violation of the mandatory pairing rule. Tests validation-layer robustness on a *sub-field* rule (harder to catch than a whole-field rule like C1) rather than translation logic.

```
{1:F01TESTGB01AXXX0000000016}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111126}}
{4:
:20:TC16REF0016
:23B:CRED
:32A:260315EUR1200,00
:50F:/56789012
1/KLAUS WEBER
2/RINGSTRASSE 9
3/AT/VIENNA
4/19700101
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does your engine catch the missing pairing (reject/flag), or does it silently populate `BirthDt` with no `CityOfBirth`/`CtryOfBirth` (or drop the whole subfield 4 silently)? Given TC09/TC10 already showed a validation gap on charge-field rules, this checks whether that gap extends to party sub-field rules too.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC16REF0016</MsgId>
      <CreDtTm>2026-09-04T09:37:08.314Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC16REF0016</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111126</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="EUR">1200.00</IntrBkSttlmAmt>
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
        <Nm>KLAUS WEBER</Nm>
        <PstlAdr>
          <TwnNm>VIENNA</TwnNm>
          <Ctry>AT</Ctry>
          <AdrLine>RINGSTRASSE 9</AdrLine>
        </PstlAdr>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <Othr>
            <Id>56789012</Id>
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

## TC17 🟢 — 50K with no account line (uses full 4-line capacity for name+address)
**Tests:** Account is optional in option K (`[/34x]` — square brackets). This checks (a) `DbtrAcct` is correctly **absent** rather than populated with garbage, and (b) all 4 available lines of name+address are captured, not truncated at 3.

```
{1:F01TESTGB01AXXX0000000017}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111127}}
{4:
:20:TC17REF0017
:23B:CRED
:32A:260315GBP4500,00
:50K:GLOBAL TRADING CO LTD
UNIT 5 INDUSTRIAL PARK
MANCHESTER ROAD
BIRMINGHAM UNITED KINGDOM
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `DbtrAcct` element **entirely absent** (not an empty `<Othr><Id></Id></Othr>`). `Dbtr/Nm`=GLOBAL TRADING CO LTD, and all three remaining lines present as `AdrLine`s (unstructured — correct for option K).

**Your XML result:**
```
[paste here]<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC17REF0017</MsgId>
      <CreDtTm>2026-09-04T09:38:08.345Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC17REF0017</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111127</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">4500.00</IntrBkSttlmAmt>
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
        <Nm>GLOBAL TRADING CO LTD</Nm>
        <PstlAdr>
          <AdrLine>UNIT 5 INDUSTRIAL PARK</AdrLine>
          <AdrLine>MANCHESTER ROAD</AdrLine>
          <AdrLine>BIRMINGHAM UNITED KINGDOM</AdrLine>
        </PstlAdr>
      </Dbtr>
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
No mapping rule for source field '51A'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.
```

---

## TC19 🟢 — 52A with UK national clearing code (Party Identifier `//SC`)
**Tests:** Field 52a option A allows a national clearing system code (here, UK Domestic Sort Code) in the Party Identifier subfield, preceded by `//`, in addition to the BIC. Checks whether the engine captures the clearing code at all, or drops it and keeps only the BIC.

```
{1:F01TESTGB01AXXX0000000019}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111129}}
{4:
:20:TC19REF0019
:23B:CRED
:32A:260315GBP750,00
:50K:/11112222
JOHN SMITH
LONDON
:52A://SC123456
LOYDGB2LXXX
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `DbtrAgt/FinInstnId/BICFI`=LOYDGB2LXXX. The sort code `123456` ideally surfaces in `DbtrAgt/FinInstnId/ClrSysMmbId` (`ClrSysId/Cd`=GBDSC, `MmbId`=123456) — if your engine doesn't support this, at minimum confirm the BIC extraction isn't corrupted by the presence of the `//SC` prefix line.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC19REF0019</MsgId>
      <CreDtTm>2026-09-04T09:43:16.185Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC19REF0019</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111129</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">750.00</IntrBkSttlmAmt>
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
          <BICFI>LOYDGB2LXXX</BICFI>
          <ClrSysMmbId>
            <ClrSysId>
              <Cd>GBDSC</Cd>
            </ClrSysId>
            <MmbId>123456</MmbId>
          </ClrSysMmbId>
        </FinInstnId>
      </DbtrAgt>
      <DbtrAgtAcct>
        <Id>
          <Othr>
            <Id>/SC123456</Id>
          </Othr>
        </Id>
      </DbtrAgtAcct>
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

## TC20 🟢 — 52D: Ordering Institution by name/address only (no BIC)
**Tests:** Option D is the "exceptional circumstances" fallback — no BIC available for the ordering institution, name/address only. Confirms `DbtrAgt` correctly falls back to `FinInstnId/Nm`+`PstlAdr` instead of `BICFI` when no BIC exists in the source.

```
{1:F01TESTGB01AXXX0000000020}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111130}}
{4:
:20:TC20REF0020
:23B:CRED
:32A:260315EUR900,00
:50K:/11112222
JOHN SMITH
LONDON
:52D:FINANZBANK AG
HAUPTSTRASSE 1
EISENSTADT
AUSTRIA
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `DbtrAgt/FinInstnId/Nm`=FINANZBANK AG, `PstlAdr/AdrLine` carrying the remaining 3 lines. **No `BICFI` element** should be present (none was given) — check the engine doesn't leave a stray empty `<BICFI></BICFI>` tag.

**Your XML result:**
```
Validation failed after retries. Errors: [Mandatory target field 'CdtTrfTxInf.DbtrAgt.FinInstnId.BICFI' (from source '__MT_SENDER_BIC__') is missing from the converted output.]
```

---
---

*End of Batch 2 (TC11–TC20). Send completed results whenever ready, or say "next batch" for TC21–TC30 (Correspondents & intermediaries: 53a/54a/55a/56a/57a, incl. conditional-rule violations).*