# MT202 / MT202 COV -> pacs.009.001.08 Strict Mapping Audit

**Review date:** 2026-08-31
**Deliverables:** `MT202_TO_PACS00900108.yaml` v1.0, `pacs.009.001.08.xsd`
**Target profile:** SWIFT CBPR+ / FINplus cross-border payment translation practice, consistent with the companion
MT103 -> pacs.008.001.08 mapping document. Not TARGET2-specific (TARGET2 has not used MT since its November 2022
migration to native ISO 20022).

## Executive summary

This is a from-scratch build, not a correction pass - there was no prior MT202 mapping to audit. Every field-level rule
below is grounded in one of: (a) the official Swift MT202/MT202COV Standards Category 2 format specifications, (b) the
actual pacs.009.001.08 XSD (fetched and structurally verified, not reconstructed from memory), or (c) a bank's published,
worked MT202COV -> pacs.009 COV translation example that was cross-checked line-by-line against both (a) and (b). Where
none of these three sources gave an unambiguous answer, the mapping is explicitly marked unresolved in
`known_limitations` rather than guessed - per the explicit instruction for this task.

The single most important finding is structural, not a bug: **the same MT field name maps to a different target shape
depending on which sequence it appears in.** Getting this backwards would silently misfile every agent in a cover
payment.

## 1. The CORE-vs-COV / Sequence-A-vs-Sequence-B distinction

MT202 (plain) and MT202 COV share an identical Sequence A (General Information). MT202 COV adds a second, mandatory
Sequence B (Underlying Customer Credit Transfer Details) that exists specifically because MT202 COV's entire purpose is
to carry a cover payment for an underlying customer credit transfer (typically an MT103). Official Swift usage rules are
explicit that a plain MT202 must never carry this data: "This message must not be used to order the movement of funds
related to an underlying customer credit transfer that was sent with the cover method. For these payments the MT 202 COV
or MT 205 COV must be used." A converter must determine COV-vs-plain from the message itself - specifically the
`{3:{119:COV}}` validation flag Swift requires in the User Header to trigger MT202COV format validation - not from any
assumption baked into the mapping.

This matters structurally because of how pacs.009.001.08 is actually typed (confirmed directly from the XSD, not
inferred): the outer `CdtTrfTxInf` (type `CreditTransferTransaction36`) types `Dbtr` and `Cdtr` as
`BranchAndFinancialInstitutionIdentification6` - i.e. **institutions**, because Sequence A is pure interbank transfer. The
nested `UndrlygCstmrCdtTrf` (type `CreditTransferTransaction37`) types its own `Dbtr`/`Cdtr` as `PartyIdentification135`
- i.e. **customers**, identical in shape to pacs.008's Dbtr/Cdtr, because Sequence B is a verbatim copy of the underlying
customer transfer.

The practical consequence: field 52a ("Ordering Institution") appears in *both* sequences with the *same name* but a
*different target*:

| | Sequence A 52a | Sequence B 52a |
|---|---|---|
| Target | outer `Dbtr` (an FI) | `UndrlygCstmrCdtTrf/DbtrAgt` (an FI acting as agent for the *customer* Dbtr) |
| Why | At Sequence A level, Dbtr itself IS the ordering institution | At Sequence B level, Dbtr is the underlying customer (from 50a); 52a plays the same "agent for the customer" role it plays in MT103 -> pacs.008 |

The same pattern applies to 56a/57a (Intermediary / Account With Institution) across the two sequences, and to the
option-letter sets available in each (Sequence A's 56a only offers A/D; Sequence B's 56a additionally offers C - confirmed
directly from the official Swift format specification table, not assumed). This document keeps the two sequences in
clearly separate `field_mappings` blocks specifically to make this impossible to conflate accidentally.

## 2. Field 20 / 21 -> MsgId / InstrId / EndToEndId

Field 20 (Transaction Reference Number) populates *both* `GrpHdr/MsgId` and `CdtTrfTxInf/PmtId/InstrId` with the same
value; field 21 (Related Reference) populates `PmtId/EndToEndId`. This is a deliberate dual-write of field 20, not a
duplicate-mapping error - `MsgId` and `InstrId` serve different structural purposes (message-level vs. instruction-level
identifier) even when populated from the same source value. This was confirmed against a complete, self-consistent worked
translation example (field 20 `FTT6765850246` -> both `MsgId` and `InstrId` equal to `FTT6765850246`; field 21
`IMSU103DIR020533` -> `EndToEndId` of the same value), not assumed by analogy with MT103.

This is a genuinely different rule from MT103 -> pacs.008 (where field 20 populates only `InstrId`, and `EndToEndId`
comes from `/ROC/` in field 70, defaulting to `NOTPROVIDED` otherwise) - MT202/MT202COV Sequence A has no equivalent
customer-supplied end-to-end reference, so field 21 fills that structural role instead. Applying the MT103 pattern here
by analogy, without checking, would have been a real error.

## 3. Deprecated-element finding: 53a/54a and the reimbursement-agent fields

The bank reference guide consulted for this document explicitly marks `GrpHdr/SttlmInf/InstgRmbrsmntAgt`(+Acct) and
`GrpHdr/SttlmInf/InstdRmbrsmntAgt`(+Acct) as **"Elements Marked to be Removed"** from current CBPR+ guidance. This
document therefore does not map 53a's/54a's institution-identifying content (e.g. a bare BIC) into those elements -
writing to an element the current standard is retiring would not be a safe default. Only the settlement-account portion
of 53a/54a (via `SttlmAcct`) is mapped, matching the reference guide's own worked correction example ("Use the Settlement
Account Node... Equivalent of field 53 in MT 202 format"). The institution-identifying content itself is left
**unresolved** pending confirmation of the current live CBPR+ Translation Portal rule - flagged in `known_limitations`
rather than guessed at.

## 4. Deliberate non-mapping: outer DbtrAgt/DbtrAgtAcct

No MT202 Sequence A field populates the outer `CdtTrfTxInf/DbtrAgt` or `DbtrAgtAcct` - confirmed by the absence of any
such row in the reference mapping's field table. The outer `Dbtr` (from 52a, or the Sender fallback) IS the debtor
institution directly; MT202 Sequence A has no concept of "an agent acting for the debtor institution, distinct from the
debtor institution itself." These elements are intentionally left unpopulated, not omitted by oversight - stated
explicitly in `scope_notes` so a future maintainer doesn't "fix" what isn't broken.

## 5. Fallback rules: what's a genuine universal rule vs. what's unresolved

Following the same discipline established in the companion MT103 document (whose main correction, in the prior review,
was replacing a false network-specific citation with the actual universal MT usage rule):

- **Sequence A 52a absent -> Dbtr = Sender.** Grounded in field 52a's own official usage rule ("Specifies the ordering
  financial institution when other than the Sender of the message"). This rule is self-contained within Sequence A - the
  Sender of the MT202/MT202COV message is a well-defined, available value.
- **Sequence A Receiver has no equivalent fallback role for 58a.** 58a is mandatory with no documented "assume the
  Receiver" rule (unlike 57a in MT103, which does have such a rule). This document does not invent one just for symmetry
  with MT103 - if 58A/58D are both absent, VR-202-03 requires rejection.
- **Sequence B 52a/57a absent -> UNRESOLVED, not defaulted.** Sequence B is a verbatim copy of the *underlying MT103's*
  data, not of this MT202COV message's own data. A fallback of "use this message's own Sender/Receiver" would be
  incorrect in principle, since the underlying MT103's Sender/Receiver are not guaranteed to be the same institutions as
  this MT202COV's Sender/Receiver - they are two different messages, potentially handled at different points in the
  correspondent chain. Rather than assume equivalence with no way to verify it, both are left fail-closed with the
  reasoning documented inline.

This last point is the kind of distinction that's easy to get wrong by pattern-matching against the already-solved MT103
case rather than checking what Sequence B actually is.

## 6. UETR cross-message rule

Confirmed via official Swift SR2018 documentation: an MT202COV's field 121 (UETR) must be **identical** to the UETR of
the underlying MT103 it covers - not merely present and well-formed. A single-message mapping cannot enforce this (it
never sees the paired MT103), so VR-202-06 documents the requirement and limits automated enforcement to UUIDv4
format-validity; a full pipeline with access to both messages should add the actual equality check.

## 7. What was verified and could not be improved further without guessing

- **SttlmMtd (INDA/INGA) trigger logic**: the reference source confirms 53a/54a relate to settlement method and account,
  and the worked example confirms "no 53/54 present -> INDA," but no source gave an unambiguous general algorithm for
  every combination. Implemented as a conservative default (mirroring the companion document's COVE/INDA treatment) and
  explicitly flagged as needing corridor-specific verification before production.
- **Network validated rule C2** (Sequence B 57a's conditional-mandatory trigger): referenced by name in the official
  format specification but its exact condition was not independently confirmed from public material. Documented as
  unresolved rather than guessed.
- **Field 103/111 -> PmtTpInf/SvcLvl/Cd**: one worked example showed a specific combination translating to a specific
  code, but no general lookup table could be confirmed. Left unresolved.
- **56a/57a option C** (Sequence B only, BIC-less/name-less party identifier): not separately implemented; flagged as a
  gap rather than folded into the option-D handling by assumption.

## 8. XSD provenance and verification

The delivered `pacs.009.001.08.xsd` is the genuine ISO 20022 Standards Editor output (generated 2019-02-14,
`targetNamespace="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08"`), retrieved from a public mirror and independently
verified before use:
- Confirmed well-formed XML.
- Confirmed `FinancialInstitutionCreditTransferV08` contains exactly `GrpHdr` + `CdtTrfTxInf` (no grouping, matching the
  "does not allow for grouping" business rule).
- Confirmed `CreditTransferTransaction36`'s full element sequence (including the absence of `InstgRmbrsmntAgt`-style
  fields at this level, and the presence of `UndrlygCstmrCdtTrf` as an optional 0..1 element of type
  `CreditTransferTransaction37`).
- Confirmed `CreditTransferTransaction37`'s Dbtr/Cdtr are `PartyIdentification135` (customer-typed), matching pacs.008's
  shape, versus `CreditTransferTransaction36`'s Dbtr/Cdtr being `BranchAndFinancialInstitutionIdentification6`
  (FI-typed) - this is the structural basis for Section 1 above, not an assumption.
- Confirmed `PaymentIdentification7`'s field order (`InstrId`, `EndToEndId`, `TxId`, `UETR`, `ClrSysRef`) and that `UETR`
  is typed `UUIDv4Identifier` with the same pattern already used in the companion MT103 document.
- Scanned for non-schema content (scripts, unexpected markup); none found.

A provenance comment was added to the top of the delivered file documenting retrieval source and verification steps; no
schema content was altered.

## Recommended next engineering steps

1. Confirm the current live CBPR+ Translation Portal rule for 53a/54a institution-identifying content in pacs.009.001.08,
   given that the two previously-documented target elements are flagged for removal.
2. Confirm the exact SttlmMtd INDA/INGA trigger algorithm and network validated rule C2's condition against the Swift
   Translation Portal or MyStandards, rather than relying on this document's conservative defaults in production.
3. Confirm the field 103/111 -> PmtTpInf/SvcLvl/Cd lookup table if this element is required downstream.
4. Add a deterministic 13C time-indication parser (shared, ideally, with the 32A date-conversion engine gap already
   flagged in the companion MT103 document) to remove the current LLM-assisted workaround.
5. If MT205/MT205COV traffic exists in scope, perform a dedicated review rather than assuming this document transfers
   unchanged.
6. Build the cross-message UETR-equality check (VR-202-06) at the pipeline level where both the MT202COV and its
   underlying MT103 are available.

## Sources consulted

Official Swift:
- Swift Standards Category 2 - MT 202 COV Format Specifications (Standards MT November 2017): https://pinas.synology.me/Swift_Manual/2017/books/us2m/aic.htm
- Swift Standards Category 2 - MT 202 Usage Rules: https://pinas.synology.me/Swift_Manual/2015/books/us2m/aie.htm
- Standard MT Release 2018 - Mandatory changes in category 1 and category 2 (UETR): https://www.swift.com/news-events/news/standard-mt-release-2018-mandatory-changes-category-1-and-category-2

Official ISO 20022:
- pacs.009.001.08 XSD (FinancialInstitutionCreditTransferV08), delivered alongside this report as `pacs.009.001.08.xsd`.

Corroborative implementation evidence:
- Bank of America SWIFT Reference Guide - Financial Institution Credit Transfer (PACS.009), including a complete worked
  MT202COV -> pacs.009 COV example: https://images.em.bankofamerica.com/GTS/ISO_20022/SWIFTReferenceGuideFinancialInstitutionCreditTransfer(PACS.009).pdf
- Prowide Integrator translation release notes: https://dev.prowidesoftware.com/latest/release-notes/changelog-translations/

Supplied by / built for this review:
- `pacs.009.001.08.xsd` (retrieved and verified as described in Section 8)
