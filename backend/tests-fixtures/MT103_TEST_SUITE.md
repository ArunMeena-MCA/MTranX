# MT103 → pacs.008.001.08 Test Suite

Test data here is built from the **external, standard SWIFT FIN field specification**
(Category 1 MT103 field formats/option letters/code lists as publicly documented by
SWIFT, and the standard ISO 20022 `pacs.008.001.08` XSD you supplied) — deliberately
**not** derived from this project's own mapping doc, since the doc's own rules are
exactly what's under test. Where a test's expected result depends on this engine's
specific (documented, possibly-debatable) business-rule choices rather than a hard
external standard, that's called out explicitly under "Basis".

## How to use this

For each test case:
1. Send the raw MT103 text (between the `text` fences) to `POST /api/convert` (or via
   the frontend) with `source_format=MT103`, `target_format=pacs.008.001.08`.
2. Compare against "Expected" — either a specific field value/structure, or an
   expected failure (with the reason).
3. Record pass/fail. Anything that doesn't match "Expected" is either a real bug, or
   a case where our documented business-rule choice should be revisited — both are
   useful outcomes.

Test BICs used throughout (fictitious test data in realistic SWIFT BIC8/11 format,
not tied to any real transaction):
`APACGB61XXX` (sender/DbtrAgt), `CITIGB2LXXX` (receiver/InstdAgt), `LOYDGB21XXX`,
`CHASUS33XXX`, `DEUTDEFFXXX`, `HSBCHKHHXXX`, `ICICINBBXXX`, `ROYCCAT2XXX`.

All amounts/dates/references are synthetic test data.

---

## Part A — Individual field tests (one field varied at a time)

### Base message (A0)

Used as the unmodified baseline for every "absence" comparison below. Contains only
the fields this engine's `unmapped_fields_policy: error` requires to be explicitly
mapped, at their simplest valid form, satisfying the baseline MT103 mandatory-field
set (SWIFT FIN Category 1: 20, 23B, 32A, 50a, 59a, 71A) and the two customer-party
schema-mandatory elements (`Dbtr`, `Cdtr`).

**Basis**: SWIFT FIN MT103 Category 1 field specification (field 20 = Transaction
Reference Number, max 16x; 23B = Bank Operation Code, code list; 32A = Value
Date/Currency/Interbank Settled Amount; 50a/59a = Ordering/Beneficiary Customer).

```text
{1:F01APACGB61AXXX0000000001}{2:I103CITIGB2LXXXXN}{3:{121:0f2464a4-03d1-4a9a-b38a-fb658d24201d}}{4:
:20:A0BASEREF001
:23B:CRED
:32A:250310USD1500,00
:50K:JOHN SMITH
:59:JANE DOE
:71A:SHA
-}
```

**Expected**: Conversion succeeds. `GrpHdr/MsgId`=`InstrId`=`A0BASEREF001`,
`IntrBkSttlmAmt Ccy="USD"`=`1500.00`, `IntrBkSttlmDt`=`2025-03-10`, `ChrgBr`=`SHAR`,
`Dbtr/Nm`=`JOHN SMITH`, `Cdtr/Nm`=`JANE DOE`, `DbtrAgt/BICFI`=`APACGB61XXX` (Sender
fallback, since 52a absent), `CdtrAgt/BICFI`=`CITIGB2LXXX` (Receiver fallback, since
57a absent), `SttlmMtd`=`INDA` (no 53/54/55A present).

---

### A1 — Field 20 (Transaction Reference Number)

**Basis**: SWIFT FIN field 20 is `16x` (max 16 characters, no leading/trailing/double
slash).

**A1.1 — valid 16-char reference (boundary, max length)**
```text
{1:F01APACGB61AXXX0000000001}{2:I103CITIGB2LXXXXN}{3:{121:0f2464a4-03d1-4a9a-b38a-fb658d24201d}}{4:
:20:1234567890123456
:23B:CRED
:32A:250310USD1500,00
:50K:JOHN SMITH
:59:JANE DOE
:71A:SHA
-}
```
**Expected**: Success. `InstrId`=`MsgId`=`1234567890123456` (exactly 16 chars, no truncation).

**A1.2 — 17-char reference (exceeds field 20's real max length)**
```text
:20:12345678901234567
```
(same message otherwise as A0, with this substituted)

**Expected**: Rejected. `max_length=16` violation reported as a deterministic error
(not silently truncated — the doc's InstrId entry explicitly says "never silently
truncate").

---

### A2 — Field 23B (Bank Operation Code)

**Basis**: SWIFT FIN field 23B code list is exactly `CRED`, `CRTS`, `SPAY`, `SPRI`,
`SSTD` (4!c).

**A2.1 — CRED (base case, already covered by A0)**

**A2.2 — SPRI (priority payment — valid SWIFT code, NOT in this engine's mapping doc at all)**
```text
:23B:SPRI
```
**Expected**: This is the key test — our mapping doc only ever documents `CRED`
being copied verbatim into `LclInstrm/Prtry`; there's no `code_list_lookup` or
validation restricting 23B to `CRED` only. Confirm whether `SPRI` also flows through
unvalidated (likely — `direct_copy` has no restriction) or whether it should be
rejected/flagged, since our own `71A`-style `code_list_lookup` pattern was NOT
applied to 23B despite 23B also being a closed external code list.

**A2.3 — invalid code (not a real SWIFT 23B value)**
```text
:23B:XXXX
```
**Expected**: Externally, `XXXX` is not a valid 23B code per the SWIFT standard —
but since this engine's 23B entry uses `direct_copy` (no `code_list_lookup`, no
`allowed_pattern`), expect this to currently pass through UNVALIDATED into
`LclInstrm/Prtry`. This is a real gap worth flagging if confirmed: unlike `71A`
(which correctly uses `code_list_lookup` and would reject an unknown code), 23B has
no such guard despite being an equally closed code list.

---

### A3 — Field 32A (Value Date / Currency / Interbank Settled Amount)

**Basis**: SWIFT FIN field 32A is `6!n3!a15d` — 6-digit date (YYMMDD), 3-letter ISO
4217 currency, amount (comma decimal, up to 15 digits total).

**A3.1 — whole-number amount, no decimal (standard SWIFT convention)**
```text
:32A:250115GBP2500,
```
**Expected**: Success. `IntrBkSttlmAmt Ccy="GBP"`=`2500` (bare integer, no invented
`.00`). Regression check for the earlier trailing-comma bug fix.

**A3.2 — fractional amount, 2 decimals**
```text
:32A:250115EUR999,99
```
**Expected**: Success. `IntrBkSttlmAmt Ccy="EUR"`=`999.99`.

**A3.3 — year-window boundary (YY=60 → 2060, last valid year per legacy 2-digit window)**
```text
:32A:600101USD100,00
```
**Expected**: Success. `IntrBkSttlmDt`=`2060-01-01`.

**A3.4 — year-window boundary (YY=61 → invalid, outside 01-60 legacy window)**
```text
:32A:610101USD100,00
```
**Expected**: Rejected — "Year not in range 01-60" per this doc's own documented
YYMMDD windowing rule (YY<80→20YY is the engine's actual implementation, so YY=61
maps to 2061, which per this doc's own note about a real sample producing "T50 Year
not in range 01-60" should reject; confirm actual current behavior — this specific
boundary interacts with the engine's YY<80 windowing choice, worth re-verifying
directly).

**A3.5 — malformed date (non-numeric)**
```text
:32A:25AB15USD100,00
```
**Expected**: Rejected — first 6 characters not all digits.

**A3.6 — invalid calendar date (Feb 30)**
```text
:32A:250230USD100,00
```
**Expected**: Rejected — not a valid calendar date.

---

### A4 — Field 33B (Currency/Instructed Amount) — optional

**Basis**: SWIFT FIN field 33B is `3!a15d`.

**A4.1 — present, same currency as 32A**
```text
:32A:250115USD1500,00
:33B:USD1500,00
```
**Expected**: Success. `InstdAmt Ccy="USD"`=`1500.00`. VR001 doesn't trigger (same currency).

**A4.2 — present, different currency from 32A, WITH field 36 (XchgRate) present**
```text
:32A:250115USD1500,00
:33B:EUR1380,00
:36:1,0870
```
**Expected**: Success. `InstdAmt Ccy="EUR"`, `XchgRate` present — VR001 satisfied.

**A4.3 — present, different currency from 32A, WITHOUT field 36 — VR001 trigger test**
```text
:32A:250115USD1500,00
:33B:EUR1380,00
```
**Expected**: **Validation error** — VR001 (`[VR001] ... - CdtTrfTxInf.XchgRate is
required because CdtTrfTxInf.InstdAmt (Ccy=EUR) differs from
CdtTrfTxInf.IntrBkSttlmAmt (Ccy=USD)...`). This directly exercises the new
`conditional_currency_mismatch_requires` rule engine.

**A4.4 — absent**: covered by A0 (no `InstdAmt` in output at all).

---

### A5 — Field 36 (Exchange Rate) — optional

**Basis**: SWIFT FIN field 36 is `12d` (plain decimal, comma separator, no composite prefix).

**A5.1 — present, comma decimal**
```text
:36:1,0870
```
**Expected**: Success. `XchgRate`=`1.0870` (comma converted to dot — regression check
for the v1.11 fix; this field previously used `direct_copy` and would have left the
comma un-converted).

**A5.2 — present, whole number**
```text
:36:2,
```
**Expected**: Success. `XchgRate`=`2`.

---

### A6 — Field 50a (Ordering Customer) — mandatory alternative group

**Basis**: SWIFT FIN field 50 always carries an option letter in MT103 (A, F, or K —
no bare "50"). Option A = BIC only. Option F = structured numbered lines (not
covered by this engine — documented gap). Option K = party identifier + free-text
name/address (4×35x).

**A6.1 — Option A (BIC only, no account line)**
```text
:50A:DEUTDEFFXXX
```
**Expected**: Success. `Dbtr/Id/OrgId/AnyBIC`=`DEUTDEFFXXX`. No `Dbtr/Nm` (option A
carries no name). `DbtrAcct` absent (no account line).

**A6.2 — Option A with account line**
```text
:50A:/12345678
DEUTDEFFXXX
```
**Expected**: Success. `Dbtr/Id/OrgId/AnyBIC`=`DEUTDEFFXXX`,
`DbtrAcct/Id/Othr/Id`=`12345678`.

**A6.3 — Option K, name+address, no account line (base case, already covered by A0)**

**A6.4 — Option K, with account line**
```text
:50K:/98765432
JOHN SMITH
123 HIGH STREET
LONDON
```
**Expected**: Success. `DbtrAcct/Id/Othr/Id`=`98765432`, `Dbtr/Nm`=`JOHN SMITH`,
`Dbtr/PstlAdr/AdrLine`×2 = `123 HIGH STREET`, `LONDON` (two repeated elements, in order).

**A6.5 — Option F (documented gap — expect INCOMPLETE, not a crash)**
```text
:50F:/11223344
1/JOHN SMITH
2/123 HIGH STREET
3/LONDON/GB
```
**Expected**: Per this doc's own documented limitation, 50F's numbered-line format
isn't decomposed — expect `DbtrAcct` populated (account portion still extracted) but
`Dbtr` to fall through to... **note: verify this doesn't now hard-fail**, since the
empty-Dbtr placeholder was intentionally removed (v2.1 "fail closed" decision) and
there is genuinely no other 50F-name entry. This is exactly the scenario that
decision was meant to make fail loudly — confirm it does (structured completeness
check should report `CdtTrfTxInf.Dbtr` missing), not silently produce an empty-but-valid Dbtr.

**A6.6 — Field 50 entirely absent — VR005 trigger test**

(Remove `:50K:...` from A0 entirely)

**Expected**: **Validation error** — VR005 (`none of ['50A', '50F', '50K'] present in
the source message`). Also likely fails earlier via the schema completeness check
(`Dbtr` missing) or `CompletenessAuditor`-adjacent path — confirm which error surfaces
first.

---

### A7 — Field 52a (Ordering Institution) — optional, DbtrAgt driver

**Basis**: SWIFT FIN field 52 options A (BIC) and D (name/address) are both valid in
MT103. Per field 52a's official SWIFT usage note: "Specifies the Financial
Institution of the Ordering Customer, when different from the Sender" — i.e.
absence means the Sender IS the ordering institution.

**A7.1 — Option A present**
```text
:52A:HSBCHKHHXXX
```
**Expected**: Success. `DbtrAgt/BICFI`=`HSBCHKHHXXX` (NOT the Sender fallback).

**A7.2 — Option D present**
```text
:52D:/87654321
HSBC HONG KONG
1 QUEENS ROAD
```
**Expected**: Success. `DbtrAgt/FinInstnId/Nm`=`HSBC HONG KONG`,
`DbtrAgt/FinInstnId/PstlAdr/AdrLine`=`1 QUEENS ROAD`,
`DbtrAgt/FinInstnId/Othr/Id`=`87654321`.

**A7.3 — absent (base case, already covered by A0)**: `DbtrAgt/BICFI` = Sender BIC
(`APACGB61XXX`), per the field 52a usage-note fallback.

---

### A8 — Fields 53A/54A/55A (Reimbursement chain) — optional, SttlmMtd driver

**Basis**: SWIFT FIN fields 53a (Sender's Correspondent), 54a (Receiver's
Correspondent), 55a (Third Reimbursement Institution) — all option A only tested
here (BIC).

**A8.1 — none present (base case, covered by A0)**: `SttlmMtd`=`INDA`, no `SttlmInf` reimbursement agents.

**A8.2 — 53A present only — VR003 + COVE trigger**
```text
:53A:CHASUS33XXX
```
**Expected**: Success. `SttlmMtd`=`COVE` (conditional rule fires),
`SttlmInf/InstgRmbrsmntAgt/FinInstnId/BICFI`=`CHASUS33XXX`. VR003 should NOT fire
(gate satisfied: trigger present AND SttlmMtd=COVE).

**A8.3 — all three present**
```text
:53A:CHASUS33XXX
:54A:DEUTDEFFXXX
:55A:HSBCHKHHXXX
```
**Expected**: Success. `SttlmMtd`=`COVE`, all three
`SttlmInf/{InstgRmbrsmntAgt,InstdRmbrsmntAgt,ThrdRmbrsmntAgt}/FinInstnId/BICFI` populated in that schema order.

**A8.4 — VR003 forced-failure test**: not achievable via raw MT103 input alone (the
engine's own `GENERATED:SttlmMtd` rule and VR003's gate are mechanically
tied together — whenever a reimbursement agent is present, `SttlmMtd` is
automatically set to `COVE` by the SAME trigger condition VR003 checks). Confirm
this is correctly a **structural tautology** (VR003 can never actually fire given
the current `GENERATED:SttlmMtd` rule), not a dead rule bug.

---

### A9 — Field 56A (Intermediary Institution) — optional

**Basis**: SWIFT FIN field 56a, option A.

**A9.1 — present**
```text
:56A:ROYCCAT2XXX
```
**Expected**: Success. `IntrmyAgt1/FinInstnId/BICFI`=`ROYCCAT2XXX`.

**A9.2 — absent**: covered by A0 (no `IntrmyAgt1`).

---

### A10 — Field 57a (Account With Institution) — optional, CdtrAgt driver

**Basis**: SWIFT FIN field 57a's official usage note: "When field 57a is not
present, it means that the Receiver is also the account with institution."

**A10.1 — Option A present**
```text
:57A:LOYDGB21XXX
```
**Expected**: Success. `CdtrAgt/BICFI`=`LOYDGB21XXX`.

**A10.2 — Option D present**
```text
:57D:/55667788
LLOYDS BANK
25 GRESHAM STREET
```
**Expected**: Success. `CdtrAgt/FinInstnId/Nm`=`LLOYDS BANK`, address line, `Othr/Id`=`55667788`.

**A10.3 — absent (base case, covered by A0)**: `CdtrAgt/BICFI` = Receiver BIC
(`CITIGB2LXXX`), per the field 57a usage-note fallback.

---

### A11 — Field 59a (Beneficiary Customer) — mandatory alternative group

**Basis**: SWIFT FIN field 59 options: bare (no letter), A (BIC), F (structured
numbered lines). No 59D/59K exist in the real MT103 spec.

**A11.1 — bare, no account line (base case, covered by A0)**

**A11.2 — bare, with account line**
```text
:59:/44556677
JANE DOE
456 LOW STREET
MANCHESTER
```
**Expected**: Success. `CdtrAcct/Id/Othr/Id`=`44556677`, `Cdtr/Nm`=`JANE DOE`, 2 `AdrLine`s.

**A11.3 — Option A**
```text
:59A:DEUTDEFFXXX
```
**Expected**: Success. `Cdtr/Id/OrgId/AnyBIC`=`DEUTDEFFXXX`.

**A11.4 — Option F (documented gap, same caveat as A6.5)**
```text
:59F:/99887766
1/JANE DOE
2/456 LOW STREET
3/MANCHESTER/GB
```
**Expected**: Account portion populated; `Cdtr` name/address gap — verify fails
closed (schema completeness check on `Cdtr`), consistent with A6.5.

**A11.5 — Field 59 entirely absent — VR005 trigger test (paired with A6.6)**

(Remove `:59:...` from A0 entirely)

**Expected**: **Validation error** — VR005 (`none of ['59', '59A', '59F'] present`).

---

### A12 — Field 70 (Remittance Information) — optional

**Basis**: SWIFT FIN field 70, 4×35x.

**A12.1 — plain free text, no /ROC/ prefix**
```text
:70:INVOICE 2025-0042 CONSULTING SERVICES
```
**Expected**: Success. `RmtInf/Ustrd`=`INVOICE 2025-0042 CONSULTING SERVICES`.
`EndToEndId`=`NOTPROVIDED` (UHB-cited default — no `/ROC/` present).

**A12.2 — with /ROC/ end-to-end reference prefix**
```text
:70:/ROC/E2E-REF-99887/INVOICE 2025-0099
```
**Expected**: Success. `PmtId/EndToEndId`=`E2E-REF-99887`,
`RmtInf/Ustrd`=`INVOICE 2025-0099` (content after the `/ROC/.../ ` prefix).

**A12.3 — absent**: covered by A0 (no `RmtInf`, `EndToEndId`=`NOTPROVIDED`).

---

### A13 — Field 71A (Details of Charges) — mandatory, closed code list

**Basis**: SWIFT FIN field 71A code list: `BEN`, `OUR`, `SHA` (3!a), exhaustive —
maps to ISO `ChargeBearerType1Code`: `BEN→CRED`, `OUR→DEBT`, `SHA→SHAR`.

**A13.1 — SHA (base case, covered by A0)**: `ChrgBr`=`SHAR`.

**A13.2 — OUR**
```text
:71A:OUR
```
**Expected**: Success. `ChrgBr`=`DEBT`.

**A13.3 — BEN**
```text
:71A:BEN
```
**Expected**: Success. `ChrgBr`=`CRED`.

**A13.4 — invalid code (not in the closed list)**
```text
:71A:XXX
```
**Expected**: Rejected — `code_list_lookup` correctly has no entry for `XXX`,
refuses to guess.

---

### A14 — Fields 71F/71G (Sender's/Receiver's Charges) — optional

**Basis**: SWIFT FIN fields 71F/71G, `3!a15d`. Field definitions specify the
charging agent implicitly: 71F = "charges deducted BY THE SENDER", 71G = "charges
to be deducted BY THE RECEIVER" (v2.6 — now used to populate `Charges7/Agt`, not
just `Amt`).

**A14.1 — 71F present**
```text
:71F:USD25,00
```
**Expected**: Success. One `ChrgsInf` occurrence: `Amt Ccy="USD"`=`25.00`,
`Agt/FinInstnId/BICFI`= the MT header Sender BIC (`APACGB61XXX` in the A0 base
message).

**A14.2 — 71G present**
```text
:71G:GBP15,00
```
**Expected**: Success. One `ChrgsInf` occurrence: `Amt Ccy="GBP"`=`15.00`,
`Agt/FinInstnId/BICFI`= the MT header Receiver BIC (`CITIGB2LXXX` in the A0 base
message).

**A14.3 — both 71F and 71G present — repeated-element test**
```text
:71F:USD25,00
:71G:GBP15,00
```
**Expected**: Success. **Two** separate `ChrgsInf` occurrences (not one overwriting
the other) — first with the Sender's USD/25.00 charge, second with the Receiver's
GBP/15.00 charge, in that order.

**A14.4 — absent**: covered by A0 (no `ChrgsInf` at all, no warning — none of the six paired entries fire).

---

### A15 — Field 72 (Sender to Receiver Information) — optional, codeword-dependent

**Basis**: SWIFT FIN field 72 is a general-purpose free-text field carrying various
codewords (`/INS/`, `/REC/`, `/ACC/`, `/BNF/`, `/INT/`, `/PHON/`, `/TELE/`, `/CHGS/`,
etc.) depending on context — NOT exclusively `/INS/`. Only `/INS/` (up to 3
occurrences) is currently mapped by this engine, to `PrvsInstgAgt1/2/3`.

**A15.1 — single /INS/ occurrence**
```text
:72:/INS/CHASUS33XXX
```
**Expected**: Success. `PrvsInstgAgt1/FinInstnId/BICFI`=`CHASUS33XXX`.

**A15.2 — three /INS/ occurrences — VR004 setup**
```text
:72:/INS/CHASUS33XXX
/INS/DEUTDEFFXXX
/INS/HSBCHKHHXXX
```
**Expected**: Success. All three `PrvsInstgAgt1/2/3` populated in order.

**A15.3 — three /INS/ occurrences PLUS field 56A — VR004 trigger test**
```text
:56A:ROYCCAT2XXX
:72:/INS/CHASUS33XXX
/INS/DEUTDEFFXXX
/INS/HSBCHKHHXXX
```
**Expected**: **Validation warning** — VR004 fires (`CdtTrfTxInf.IntrmyAgt1 should
not be present because all of [PrvsInstgAgt1,2,3] are already populated`). Severity
is `warning`, not `error` — confirm conversion still succeeds with the warning surfaced.

**A15.4 — non-/INS/ codeword present (real-world common case) — regression test for the v1.22 bug fix**
```text
:72:/REC/FX PROCESSED AT SOURCE
```
**Expected**: Success — this is the exact scenario that previously hard-failed the
whole conversion (the `/INS/` regex not matching, and `optional: true` having been
missing). Confirm the conversion now succeeds with no `PrvsInstgAgt*` populated,
rather than throwing.

---

### A16 — Block 1/2/3 header fields (Sender, Receiver, UETR, MUR)

**Basis**: SWIFT FIN Block 1 (Basic Header) carries the Sender's 12-char Logical
Terminal address; Block 2 Input carries the Receiver's; Block 3 (User Header) field
121 carries the UETR (mandatory in SWIFT gpi/CBPR+ traffic), field 108 carries the
Message User Reference (MUR) — a free-text sender-assigned reference, not mapped to
any pacs.008 field.

**A16.1 — Sender LT address with non-default terminal identifier (regression test for the v2.3 BIC-fix)**
```text
{1:F01APACGB61BXXX0000000001}
```
(terminal identifier `B` instead of `A` — same message otherwise as A0, with 52a
absent so DbtrAgt falls back to Sender)

**Expected**: Success. `DbtrAgt/BICFI`=`APACGB61XXX` (terminal identifier `B`
correctly dropped, branch `XXX` correctly retained — NOT `APACGB61`).

**A16.2 — Block 3 with BOTH field 108 (MUR) and field 121 (UETR) — regression test for the v1.21 fix**
```text
{3:{108:MUR-TEST-REF-001}{121:14a8ff60-f917-47d4-bd50-8094c24078d2}}
```
**Expected**: Success. `UETR`=`14a8ff60-f917-47d4-bd50-8094c24078d2`. `108` (MUR) is
silently ignored (not mapped, not an unmapped-field error) — this is the exact
scenario that previously hard-failed with `UnmappableFieldException` for field
`108`.

**A16.3 — Block 3 absent entirely (no UETR)**

(Omit `{3:...}` entirely)

**Expected**: Success, but `PmtId/UETR` absent from output (UETR is optional in the
schema — `maxOccurs=1 minOccurs=0` on `PaymentIdentification7/UETR`). Confirm this
does NOT fail — UETR is commonly described as "mandatory in SWIFT gpi" but the bare
pacs.008.001.08 XSD itself does not make it schema-mandatory.

**A16.4 — malformed UETR (not a valid UUID v4 format)**
```text
{3:{121:not-a-valid-uuid}}
```
**Expected**: Rejected — `allowed_pattern` on the `121` entry
(`^[0-9a-f]{8}-[0-9a-f]{4}-...$`) correctly refuses a malformed UETR rather than
passing it through.

---

## Part B — Combination tests

### B1 — Correspondent banking chain (53A + 54A + 55A + 57A together)

```text
{1:F01APACGB61AXXX0000000010}{2:I103CITIGB2LXXXXN}{3:{121:a1b2c3d4-e5f6-4789-a012-3456789abcde}}{4:
:20:B1COMBOREF01
:23B:CRED
:32A:250401USD5000,00
:50K:/11112222
ACME CORP
100 MAIN STREET
NEW YORK
:52A:CHASUS33XXX
:53A:DEUTDEFFXXX
:54A:HSBCHKHHXXX
:55A:ROYCCAT2XXX
:57A:LOYDGB21XXX
:59A:CITIGB2LXXX
:71A:SHA
-}
```
**Expected**: Success. `DbtrAgt`=`CHASUS33XXX` (52A, not Sender fallback),
`CdtrAgt`=`LOYDGB21XXX` (57A, not Receiver fallback), `SttlmMtd`=`COVE`, all three
reimbursement agents populated, `Cdtr/Id/OrgId/AnyBIC`=`CITIGB2LXXX` (59A).

### B2 — Full remittance + charges + FX chain

```text
{1:F01APACGB61AXXX0000000011}{2:I103CITIGB2LXXXXN}{3:{121:b2c3d4e5-f6a7-4890-b123-456789abcdef}}{4:
:20:B2COMBOREF02
:23B:CRED
:32A:250401GBP2000,00
:33B:USD2540,00
:36:1,2700
:50A:/33334444
DEUTDEFFXXX
:59:/55556666
BENEFICIARY NAME
:70:/ROC/INV-2025-777/PAYMENT FOR SERVICES RENDERED
:71A:OUR
:71F:GBP15,00
-}
```
**Expected**: Success (VR001 satisfied, `XchgRate` present). `InstdAmt Ccy="USD"
2540.00`, `XchgRate=1.27`, `ChrgBr=DEBT`, `EndToEndId=INV-2025-777`,
`RmtInf/Ustrd=PAYMENT FOR SERVICES RENDERED`, plus one `ChrgsInf` occurrence
(`Amt Ccy="GBP"`=`15.00`, `Agt`= the Sender BIC, per the v2.6 71F fix — no warning).

### B3 — Intermediary chain without triggering VR004 (only 2 of 3 /INS/ + IntrmyAgt)

```text
:56A:ROYCCAT2XXX
:72:/INS/CHASUS33XXX
/INS/DEUTDEFFXXX
```
**Expected**: Success, NO VR004 warning (only 2 of 3 `PrvsInstgAgt` populated — the
mutual-exclusion condition requires all three).

---

## Part C — Maximum field-coverage test

Every currently-mapped optional AND mandatory field populated simultaneously —
stress-tests element ordering, all repeated-element handling, and every currency
attribute in one pass.

```text
{1:F01APACGB61AXXX0000000099}{2:I103CITIGB2LXXXXN}{3:{108:MUR-FULL-TEST}{121:c3d4e5f6-a7b8-4901-c234-56789abcdef0}}{4:
:20:C1FULLCOVER01
:23B:CRED
:32A:250601EUR10000,00
:33B:USD10870,00
:36:1,0870
:50K:/77778888
FULL COVERAGE DEBTOR
1 TEST AVENUE
TESTVILLE
:52A:HSBCHKHHXXX
:53A:CHASUS33XXX
:54A:DEUTDEFFXXX
:55A:ROYCCAT2XXX
:56A:ICICINBBXXX
:57A:LOYDGB21XXX
:59:/99990000
FULL COVERAGE CREDITOR
2 TEST BOULEVARD
TESTBOROUGH
:70:/ROC/FULLCOVER-E2E-001/FULL FIELD COVERAGE TEST PAYMENT
:71A:SHA
:71F:EUR20,00
:71G:EUR10,00
:72:/INS/CHASUS33XXX
-}
```
**Expected**: Success with no warnings. Two `ChrgsInf` occurrences (71F's Sender-BIC
charge, then 71G's Receiver-BIC charge, per the v2.6 fix). Verify EVERY element
renders in correct schema sequence order (this is the single best regression test
for the `XsdOrderingIndex` ordering logic across the widest span of the schema at
once) and that `NbOfTxs=1`, `MsgId=InstrId=C1FULLCOVER01`
(field 20, current documented behavior), `EndToEndId=FULLCOVER-E2E-001`.

---

## Part D — Validation-rule-specific tests (VR001–VR008)

| Rule | Covered by | Notes |
|---|---|---|
| VR001 | A4.3 (fail), A4.2/B2 (pass) | conditional_currency_mismatch_requires |
| VR002 | — | Not machine-checked (ChrgsInf unsupported) — no test possible against current output |
| VR003 | A8.2/A8.3 (satisfied) | Structurally tied to GENERATED:SttlmMtd — see A8.4 note; cannot currently be forced to fail |
| VR004 | A15.3 (fires), B3 (doesn't fire) | mutual_exclusion |
| VR005 | A6.6, A11.5 | source_alternative_group_required — test each alternative group independently |
| VR006 | A6.5, A11.4 | naturally enforced via schema completeness check, not a separate rule_type |
| VR007 | D1, D2 below | currency_precision_check |
| VR008 | D3 below | structured_address_required — date-gated to 2026-11-14, cannot fire before then |

### D1 — VR007: JPY with fractional digits (should fail — JPY has 0 decimal places)

```text
:32A:250601JPY150000,50
```
(substituted into A0's 32A line)

**Expected**: **Validation error** — VR007 fires: `CdtTrfTxInf.IntrBkSttlmAmt=150000.50
JPY has 1 fractional digit(s), but JPY allows at most 0`.

**Basis**: ISO 4217 — JPY is a published 0-decimal currency.

### D2 — VR007: KWD with 3 valid decimal places (should pass — KWD allows 3)

```text
:32A:250601KWD500,123
```
**Expected**: Success, no VR007 warning. `IntrBkSttlmAmt Ccy="KWD"`=`500.123`.

**Basis**: ISO 4217 — KWD (Kuwaiti Dinar) is a published 3-decimal currency.

### D3 — VR008: free-text-only address, tested AFTER the 2026-11-14 effective date

Not testable via a normal conversion call today (the rule is correctly date-gated
off until 2026-11-14). **To test this rule's logic specifically**, either:
(a) re-run test A0/A6.4 (which has `AdrLine` but no `TwnNm`/`Ctry`) after that date
and confirm it now fails, or
(b) temporarily set `params.effective_date` to a past date in a scratch copy of the
mapping doc and confirm A6.4-style input then correctly fails with `VR008`.

---

## Part E — Negative / malformed-input tests

### E1 — Missing Block 4 entirely
```text
{1:F01APACGB61AXXX0000000001}{2:I103CITIGB2LXXXXN}
```
**Expected**: Rejected at the parse stage — `ParsingException`, "No block 4 found."

### E2 — Field present in Block 4 with NO mapping entry at all (unmapped_fields_policy=error)
```text
:77B:/BENEFRES/US
```
(added to A0)

**Expected**: Rejected — `UnmappableFieldException` for field `77B` (this document
explicitly documents 77B as excluded from `field_mappings`, so `unmapped_fields_policy:
error` should correctly reject it rather than silently drop it).

### E3 — Amount with an extra decimal place beyond what 32A's format allows conceptually, but syntactically parseable
```text
:32A:250601USD100,999
```
**Expected**: Parses fine at the MT/regex level (this is syntactically valid per this
engine's `decimal_comma_to_dot`), but should trigger VR007 (USD allows 2, not 3) — a
second angle on D1/D2, using a mainstream currency instead of an exotic one.

### E4 — 50A and 50K both present (should never happen in real SWIFT traffic — one field, one option)
Not a valid SWIFT MT103 in the first place (field 50 appears exactly once, with one
option letter) — Prowide's parser behavior here is untested; worth confirming it
either rejects at parse time or that our own field_mappings' `unmapped_fields_policy`
doesn't do something strange if a hand-crafted (invalid) message contains both.
