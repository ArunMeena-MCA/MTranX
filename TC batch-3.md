# MT103 → pacs.008 Test Suite — Batch 3 (TC21–TC30)
## Focus: Correspondents & Intermediaries — 53a/54a/55a/56a/57a, incl. conditional-rule violations

**How to use:** run each MT103 through your engine, paste the XML (or error output) into the slot below it, and send the file back.

---

## TC21 🟢 — 53A: Sender's Correspondent (basic)
**Tests:** Baseline reimbursement-path institution — the bank through which the Sender will reimburse the Receiver.

```
{1:F01TESTGB01AXXX0000000021}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111131}}
{4:
:20:TC21REF0021
:23B:CRED
:32A:260315USD1000,00
:50K:/11112222
JOHN SMITH
LONDON
:53A:CHASUS33
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** A reimbursement-agent element populated with BIC=CHASUS33 (typically `InstgRmbrsmntAgt` or equivalent — check which element your engine's mapping doc designates for 53a). Rest of message unaffected.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC21REF0021</MsgId>
      <CreDtTm>2026-09-04T10:11:45.146Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>COVE</SttlmMtd>
        <InstgRmbrsmntAgt>
          <FinInstnId>
            <BICFI>CHASUS33</BICFI>
          </FinInstnId>
        </InstgRmbrsmntAgt>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC21REF0021</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111131</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1000.00</IntrBkSttlmAmt>
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

## TC22 🟢 — 53B: Party Identifier (account) only, no BIC
**Tests:** Per the spec's usage rule — when there are multiple direct account relationships in the transaction currency, 53B is used with **only** a party identifier (account number), no location, to indicate which account settles this transaction.

```
{1:F01TESTGB01AXXX0000000022}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111132}}
{4:
:20:TC22REF0022
:23B:CRED
:32A:260315USD2500,00
:50K:/11112222
JOHN SMITH
LONDON
:53B:/12345678901
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** The account number `12345678901` should be captured (likely as a settlement/reimbursement account reference) — confirm it isn't mistaken for a BIC or dropped for lacking one.

**Your XML result:**
```
No mapping rule for source field '53B'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.
```

---

## TC23 🟢 — Full reimbursement chain: 55a present, requiring both 53a and 54a (Rule C7)
**Tests:** Rule **C7** — if field 55a (Third Reimbursement Institution) is present, both 53a and 54a *must* also be present. This is the valid, complete 3-institution chain.

```
{1:F01TESTGB01AXXX0000000023}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111133}}
{4:
:20:TC23REF0023
:23B:CRED
:32A:260315USD5000,00
:50K:/11112222
JOHN SMITH
LONDON
:53A:CHASUS33
:54A:IRVTUS3N
:55A:BNPAFRPP
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** All three institutions (CHASUS33, IRVTUS3N, BNPAFRPP) present in the output as distinct elements, in the correct role each (Sender's correspondent / Receiver's correspondent / Third reimbursement institution) — not collapsed into one, not reordered.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC23REF0023</MsgId>
      <CreDtTm>2026-09-04T10:13:44.676Z</CreDtTm>
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
        <InstrId>TC23REF0023</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111133</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">5000.00</IntrBkSttlmAmt>
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
Mapping document for 'MT103_TO_PACS008' is incomplete - refusing to guess.
Missing/ambiguous items (1):
  - XSD validation error: cvc-pattern-valid: Value '//FW021000089
CHASUS33' is not facet-valid with respect to pattern '[A-Z0-9]{4,4}[A-Z]{2,2}[A-Z0-9]{2,2}([A-Z0-9]{3,3}){0,1}' for type 'BICFIDec2014Identifier'.
```

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
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC25REF0025</MsgId>
      <CreDtTm>2026-09-04T10:22:35.733Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC25REF0025</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111135</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1800.00</IntrBkSttlmAmt>
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
      <CreDtTm>2026-09-04T10:23:58.106Z</CreDtTm>
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

## TC27 🟢 — 57D: Account With Institution by name/address only (no BIC)
**Tests:** Same "exceptional circumstances" pattern as TC20 (52D) — but on the Creditor Agent side this time. Given TC20 exposed a hardcoded-mandatory-BICFI bug on `DbtrAgt`, this checks whether the identical flaw exists symmetrically on `CdtrAgt`.

```
{1:F01TESTGB01AXXX0000000027}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111137}}
{4:
:20:TC27REF0027
:23B:CRED
:32A:260315GBP1400,00
:50K:/11112222
JOHN SMITH
LONDON
:57D:SMALL LOCAL BANK
123 VILLAGE ROAD
RURAL TOWN
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** `CdtrAgt/FinInstnId/Nm`=SMALL LOCAL BANK, `PstlAdr/AdrLine` for the remaining lines, **no `BICFI` element**. If your engine throws the same "mandatory BICFI missing" error as TC20, that confirms the bug is systemic (applies to every agent role, not just `DbtrAgt`) rather than a one-off.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC27REF0027</MsgId>
      <CreDtTm>2026-09-04T10:25:05.371Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC27REF0027</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111137</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">1400.00</IntrBkSttlmAmt>
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
          <Nm>SMALL LOCAL BANK</Nm>
          <PstlAdr>
            <AdrLine>123 VILLAGE ROAD</AdrLine>
            <AdrLine>RURAL TOWN</AdrLine>
          </PstlAdr>
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

## TC28 🔴 — INVALID: 23B=SPRI with 53D (violates Rule C4)
**Tests:** Rule **C4** — under the Priority service level (SPRI), field 53a must **not** be used with option D (name/address only; SPRI requires machine-readable BIC-identified correspondents for STP). This is a service-level-specific restriction, a different validation category from anything tested so far.

```
{1:F01TESTGB01AXXX0000000028}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111138}}
{4:
:20:TC28REF0028
:23B:SPRI
:32A:260315USD900,00
:50K:/11112222
JOHN SMITH
LONDON
:53D:SOME CORRESPONDENT BANK
456 FINANCE STREET
:59:/33334444
MARY JONES
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch that SPRI + 53D is an invalid combination? This is a *cross-field* rule (one field's value restricts another field's allowed options) — the hardest category to validate. Given everything found so far, predict this also slips through untouched.

**Your XML result:**
```
No mapping rule for source field '53D'. These source field(s) were found in the input but have no entry in field_mappings for conversion 'MT103_TO_PACS008'. Add explicit rules for them, or set unmapped_fields_policy to 'ignore' or 'passthrough' if that is truly intended.
```

---

## TC29 🟢 — 23E=CHQB correctly paired with no account in 59 (Rule C13 satisfied)
**Tests:** Rule **C13** — if 23E contains CHQB (pay by cheque), the beneficiary account in 59a must **not** be present (there's no account to credit — it's a cheque). This is the valid, correctly-paired usage.

```
{1:F01TESTGB01AXXX0000000029}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111139}}
{4:
:20:TC29REF0029
:23B:CRED
:23E:CHQB
:32A:260315GBP450,00
:50K:/11112222
JOHN SMITH
LONDON
:59:JANE COLLECTOR
:71A:SHA
-}
```
**Expected / key checks:** `Cdtr/Nm`=JANE COLLECTOR, **`CdtrAcct` entirely absent** (correct — no account was given, consistent with cheque payment). Confirm the engine doesn't invent an empty account element, and that the CHQB instruction itself surfaces somewhere (e.g. `InstrForCdtrAgt`) rather than being silently dropped like the 51A/identity-document cases in Batch 2.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC29REF0029</MsgId>
      <CreDtTm>2026-09-04T10:26:11.252Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC29REF0029</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111139</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>CRED</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="GBP">450.00</IntrBkSttlmAmt>
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
        <Nm>JANE COLLECTOR</Nm>
      </Cdtr>
      <InstrForCdtrAgt>
        <Cd>CHQB</Cd>
      </InstrForCdtrAgt>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

## TC30 🔴 — INVALID: 23B=SPRI with no account in 59 (violates Rule C12)
**Tests:** Rule **C12** — under SPRI (and SSTD/SPAY), the beneficiary account is **mandatory**, the opposite constraint from TC29's CHQB case. This message omits it, which a real FIN gateway would reject with error E10 before ever reaching a translator.

```
{1:F01TESTGB01AXXX0000000030}
{2:I103TESTGB02XXXXN}
{3:{121:aaaaaaaa-1111-4111-8111-111111111140}}
{4:
:20:TC30REF0030
:23B:SPRI
:32A:260315USD1600,00
:50K:/11112222
JOHN SMITH
LONDON
:59:BENEFICIARY NAME ONLY
:71A:SHA
-}
```
**Expected / key checks:** Does the engine catch that SPRI requires a beneficiary account and this one has none? Given C12 is structurally identical in shape to C1 (a value in one field mandates presence in another) — and TC08 (C1) was correctly caught — this is a fair test of whether that same validation *pattern* was applied consistently to party-level rules, not just amount-level ones.

**Your XML result:**
```
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>TC30REF0030</MsgId>
      <CreDtTm>2026-09-04T10:27:22.926Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>INDA</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>TC30REF0030</InstrId>
        <EndToEndId>NOTPROVIDED</EndToEndId>
        <UETR>aaaaaaaa-1111-4111-8111-111111111140</UETR>
      </PmtId>
      <PmtTpInf>
        <LclInstrm>
          <Prtry>SPRI</Prtry>
        </LclInstrm>
      </PmtTpInf>
      <IntrBkSttlmAmt Ccy="USD">1600.00</IntrBkSttlmAmt>
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
        <Nm>BENEFICIARY NAME ONLY</Nm>
      </Cdtr>
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>

```

---

*End of Batch 3 (TC21–TC30). Send completed results whenever ready, or say "next batch" for TC31–TC40 (Beneficiary & remittance: 59/59A/59F, field 70 codewords).*