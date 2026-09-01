# MT103 -> pacs.008.001.08 Strict Mapping Audit (v2.1)

**Review date:** 2026-08-31
**Source:** `MT103_TO_PACS00800108_SWIFT_CORRECTED.yaml` v2.0
**Corrected file:** `MT103_TO_PACS00800108_SWIFT_CORRECTED_v2.1.yaml` v2.1
**Target profile decision:** SWIFT CBPR+ / FINplus cross-border payment translation practice, not TARGET2-specific. (v2.1 confirms this decision is now applied *consistently* throughout the document — see §0.)

## v2.1 summary — what changed since v2.0

v2.0 fixed the business-mapping problems identified against the original v1.18 file (empty Dbtr/Cdtr, UETR regex, 52D/57D address loss, over-broad settlement rule, fail-closed posture on 50F/59F/71F/71G). This v2.1 pass is an **independent verification** of v2.0 itself, cross-checked against public Swift MT103 Standards field definitions, PMPG/Wolfsberg guidance, and current CBPR+ material. It found and corrected five issues v2.0 did not catch:

0. **A direct self-contradiction on the target network** (Section 0, below) — the single most serious finding, since it undermines the document's central claim that TARGET2 was correctly ruled out.
1. A genuine duplicate mapping (not merely "harmless") for 50K and bare-59 account extraction.
2. A likely functional bug: 52D/57D extracted a Party Identifier with no defined destination for it.
3. An inconsistent settlement-method presence check (54A missing while 53A/55A were present).
4. A missing operational-scope statement, given that live Swift cross-border traffic has been MX-native since November 2025.

No field mappings, transformations, or fail-closed decisions from v2.0 were reversed — all five fixes either correct sourcing/citations, close a data-loss gap, remove a redundant write path, or add a missing check_fields entry.

---

## Section 0. CRITICAL: TARGET2/CBPR+ self-contradiction (new in this review)

v2.0's `scope_notes` states, as the final target-profile decision:
> "The user does not target TARGET2 specifically... No TARGET2-specific rule is treated as a universal SWIFT rule."

But the `__MT_RECEIVER_BIC__` entry (the CreditorAgent fallback used when 57A/57D are both absent) justified itself with:
> "User confirmed (2026-08-31): this deployment targets TARGET2 specifically... See scope_notes for the confirmed TARGET2 declaration."

`scope_notes` contains no such declaration — it contains the opposite one. This was left over from that entry's original v1.9 drafting, before the target profile was changed to CBPR+, and was never reconciled.

It also doesn't hold up independently: TARGET2 fully migrated off MT to native ISO 20022 in November 2022, so an ongoing "MT103->pacs.008 translation rule" attributed to TARGET2 doesn't correspond to anything TARGET2 does today; and TARGET2's SettlementMethod is `CLRG`, not the INDA/COVE binary this same document's `GENERATED:SttlmMtd` rule implements — so even on its own terms, the document was never actually configured as a TARGET2 deployment.

**Fix applied:** the false TARGET2 citation is removed. The underlying fallback behavior (Receiver -> CdtrAgt, Sender -> DbtrAgt, when 57a/52a are absent) is **retained**, because it turns out to be independently correct — just mis-cited. It is now grounded in the verified, official Swift MT103 Standards field definitions:

- **Field 52a (Ordering Institution):** "Specifies the Financial Institution of the Ordering Customer, **when different from the Sender**." → 52a absent means the Sender *is* the ordering institution.
- **Field 57a (Account With Institution):** "**When field 57a is not present, it means that the Receiver is also the account with institution.**"

Both are universal Swift FIN usage rules on the MT103 *source side* — independent of which MX target profile (CBPR+, TARGET2, or otherwise) is in play — so mapping them to DbtrAgt/CdtrAgt is a faithful translation regardless of target network. This is a stronger, more defensible position than either the v2.0 TARGET2 citation or an unqualified "user-provided rule."

---

## Section 1. Duplicate mapping (escalated from v2.0's "harmless" framing)

v2.0 acknowledged, in its own notes, that 50K's name/address entry and a separate standalone account-only entry both wrote to `CdtTrfTxInf.DbtrAcct.Id.Othr.Id` from the same raw 50K value — and called this "redundant-but-harmless." The same pattern existed for bare 59 -> `CdtTrfTxInf.CdtrAcct.Id.Othr.Id`.

This is a genuine duplicate, not a harmless one: the two entries parsed the identical source string through **two different mechanisms** (an LLM-assisted free-text instruction vs. a deterministic `account_line_pattern` regex inside `decompose_party`), with no cross-check between them. Nothing guarantees the two mechanisms stay in agreement on edge cases (stray whitespace, a second slash-prefixed line, etc.); any future divergence between them would go undetected, since it's last-write-wins with no consistency check.

**Fix applied:** removed the standalone account-only entries for 50K and bare-59. The existing `decompose_party` name/address entries (added in v1.16) already extract the account portion via `account_sub_element`, so no coverage was lost — the "malformed empty account line" edge case from the removed entries was carried forward into the surviving entries.

---

## Section 2. 52D/57D Party Identifier had no write destination

Both 52D and 57D declared `account_sub_element: "Id"` (to capture an optional leading `/`-prefixed Party Identifier line) but neither entry's `sub_element_targets` actually mapped `Id` anywhere. This directly contradicted both entries' own notes, which said the identifier "must not be mislabelled as the institution name" / "must be preserved" — implying it was being kept somewhere, when in fact it had no defined destination at all.

**Fix applied:** added `Id -> FinInstnId.Othr.Id` for both DbtrAgt (52D) and CdtrAgt (57D). This reuses `FinancialInstitutionIdentification18`'s generic `Othr` identifier container — the same safe, no-guess choice already used elsewhere in this document (v1.13) for account-portion values under `CashAccount38/Othr/Id`. It is deliberately **not** routed to `ClrSysMmbId`, since that requires a specific clearing-system scheme code that cannot be reliably inferred from an arbitrary MT text line without a configured clearing-system mapping table (already listed as a follow-up item in the original audit's "Recommended next engineering changes").

---

## Section 3. Settlement-method check_fields asymmetry

`GENERATED:SttlmMtd`'s COVE/INDA presence check listed `53A, 53D, 54B, 54D, 55A, 55B, 55D` — every option-A field except **54A**, even though 53A and 55A (54A's exact counterparts on the other two reimbursement-agent fields) were both included, and 54A itself is mapped elsewhere in the same document as an ordinary field. A message carrying only 54A would previously have been misclassified as INDA despite an explicit correspondent-agent relationship being present — precisely the kind of misclassification the v2.0 narrowing was meant to prevent.

**Fix applied:** added `54A` to `check_fields`.

---

## Section 4. Operational scope: MT103 after the Swift MX cutover

Swift's CBPR+ MT/MX coexistence period for cross-border payment instructions ended in **November 2025**; live cross-border FI-to-FI traffic on the Swift network is now native ISO 20022 (pacs.008), not MT103. That doesn't make this mapping document obsolete, but it does mean the document should state what real-world source is expected to still produce MT103 input after that date (legacy/archival conversion, a domestic or non-Swift rail, back-office reconciliation, or test/reference tooling) — because that context affects how strict the fail-closed decisions throughout this document should be in practice.

**Fix applied:** added an `OPERATIONAL SCOPE` paragraph to `scope_notes` flagging this and asking the deploying team to confirm the actual source of incoming MT103 messages before production use.

---

## What was verified and left unchanged

- **Field-option coverage:** confirmed against Swift/PMPG Market Practice Guidelines that field 50a has exactly three options (A, F, K) and field 59a has exactly three (A, F, no-letter). The document's 50A/50F/50K and 59/59A/59F entries match this exactly — no invented or missing options.
- **71A charge-bearer code list** (BEN→CRED, OUR→DEBT, SHA→SHAR): matches the standard ChargeBearerType1Code mapping used throughout CBPR+ implementation guides.
- **71F/71G fail-closed posture, 50F/59F fail-closed posture, empty Dbtr/Cdtr removal, UETR UUIDv4 tightening:** all re-checked and left as-is — these v2.0 corrections hold up.
- **23B → LclInstrm/Prtry "corroborated, not portal-certified" labeling:** left as-is; this is an honest characterization given what's publicly retrievable, and no evidence was found to upgrade or downgrade its status.

## Recommended next engineering changes (carried forward + new)

1. Add a deterministic `extract_subfield` / composite-field transformation so 32A does not require an LLM.
2. Add a structured repeated-element builder for `ChrgsInf` so 71F/71G can be implemented safely.
3. Add occurrence-indexed extraction for repeated `/INS/` in field 72.
4. Add deterministic F-option parser for 50F/59F including 3/ country/town hybrid syntax.
5. Add clearing-system mapping tables for `//` Party Identifiers — this would let the new 52D/57D `Othr/Id` routing be upgraded to `ClrSysMmbId` where the identifier matches a known clearing code.
6. Add 77B repeated `RgltryRptg` support.
7. Validate the final mapping against the current Swift MyStandards/Translation Portal test cases before production certification.
8. **(new)** Confirm and document the actual operational source of MT103 input this converter will process, given the Nov 2025 CBPR+ MX cutover.

## Sources consulted (v2.1 additions)

- Swift Standards MT Category 1, Field 52a (Ordering Institution) definition: https://pinas.synology.me/Swift_Manual/2017/books/us1m/aih016.htm (parallel Category 1/2 field 57a definition, same source family)
- Swift/PMPG Market Practice Guidelines for fields 50a/59a: https://www.swift.com/swift-resource/202336/download
- CBPR+ settlement method codes (INDA/INGA/COVE/CLRG): https://paymentssignal.com/learn/iso-20022/settlement-method-inga-vs-inda
- TARGET2 ISO 20022 migration (MX-native since Nov 2022) and CLRG usage: https://www.iso20022payments.com/target-services-t2/addressing-payments-in-t2/
- CBPR+ Nov 2025 MT/MX coexistence-period end: https://pacs008.com/faq/, https://www.jpmorgan.com/insights/payments/fx-cross-border/iso-20022-migration

All sources from the original v1.18/v2.0 audit (Swift CBPR+ pages, Swift MT103 Message Reference Guide, PMPG structured customer data guidance, Prowide Integrator release notes, Bank of America formatting guide) remain applicable and are not repeated here.