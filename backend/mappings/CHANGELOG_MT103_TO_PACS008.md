# MT103 -> pacs.008.001.08 Mapping Changelog

Full versioned history of `backend/mappings/MT103_TO_PACS00800108.yaml`'s `known_limitations` field,
moved out of the YAML on 2026-09-08 (v2.29) since it is documentation only - confirmed unused at runtime
(`MappingDocument.getKnownLimitations()` has zero callers anywhere in the codebase). Moving it here means
the ~500-line changelog is no longer re-parsed by the app on every request; nothing else changes.

Entries are verbatim, newest first, exactly as they appeared in the YAML. New entries go here from now on,
not back into the YAML's `known_limitations` field.

```yaml
known_limitations:
- "v2.43 BUG FIX (2026-09-16, user request: 'fix it as per SR2025, i want my converter to follow rules properly',
  following directly from the two deviations disclosed in the v2.42 entry below). Both are now enforced in
  ConverterService.enrichWithStructuredAddress rather than merely documented. (1) TwnNm+Ctry mandatory together:
  PMPG's 'Hybrid Postal Address' guide (v1.12) states 'TownName and Country are mandatory elements... when a
  hybrid address is used' - required TOGETHER, not one alone. Added a check (isPresent/removeIfPresent helpers)
  covering BOTH this method's own sidecar resolution AND any earlier deterministic sub_element in the same
  decompose entry (the numbered-line 50F/59F/50K/59 entries' own '3/XX/City' regex) - if city AND country will
  not BOTH end up present, ALL structured fields for that address (including an already-written lone
  deterministic Ctry or TwnNm) are removed and AdrLine is left completely untouched, falling back to fully
  unstructured (still valid, no expiry - see v2.42's own finding). (2) AdrLine's 2-occurrence hybrid cap: if more
  than 2 AdrLine lines remain after the confident-dedup step, the same all-or-nothing fallback applies - rather
  than guess how to truncate or consolidate overflow content (real risk of silently losing data), the whole
  hybrid enrichment for that address is undone. Deliberately scoped OUT of this fallback: an address where
  TwnNm+Ctry were already reliably established by a deterministic '3/XX/City' marker before this method ran - an
  unrelated AdrLine-count overflow (e.g. extra unmarked trailing lines the numbered-line entries' own broadened
  capture picks up) must not discard an already-solid, independently-sourced TwnNm/Ctry pair; the two problems
  (missing country vs. too many address lines) are independent, and only the sidecar-established case has 'the
  whole address' as a single safe-to-undo unit. Character-length (70 chars/line) was NOT separately enforced -
  every source line already comes from an MT field line limited to 35 characters, well under that cap, so it
  cannot be violated by content originating from this converter's own MT parsing. Verified end-to-end against a
  rebuilt temp instance (port 8001, never the user's own running instance) before reporting done: the ZZZ
  CORPRATION/Colmore message's Cdtr (previously TwnNm=dallas, no Ctry) and Dbtr (previously TwnNm=birmingham, no
  Ctry) both now correctly revert to plain AdrLine-only output, matching SR2025's actual mandatory-together rule;
  the already-fully-valid cases (Chennai/India, Paris/Berlin, the numbered-50K test) are unaffected, confirming no
  regression; and a new constructed test (a numbered-line message with a real '3/US/DALLAS' marker plus 3 extra
  unmarked AdrLine lines) confirmed the AdrLine-cap fallback does NOT fire when TwnNm/Ctry came from that
  deterministic marker - Ctry=US/TwnNm=DALLAS and all 3 AdrLine lines were preserved intact, exactly as designed."
- "v2.42 DOCUMENTATION FIX + DISCLOSED FINDINGS (2026-09-16, user question: given SR2026 is on hold, why does this
  converter emit TwnNm/StrtNm/Ctry at all - isn't that an SR2026-only thing?). Verified directly against the PMPG's
  own 'Hybrid Postal Address' guide (v1.12, 5 March 2026) - fetched, and since the swift.com/ECB pages both
  blocked automated fetching, the PDF itself was downloaded and its full text extracted and read (46 pages) rather
  than relying on a summary. Finding: hybrid addressing is NOT an SR2026 feature - it was approved and 'will
  become effective as of November 2025 (SR2025)... Allowed as of November 2025 (no end-date)'. Before 22 November
  2025 structured elements and AdrLine were mutually exclusive; since SR2025 (i.e. right now, independent of
  SR2026's own postponement - see the 2026-09-15 entry above), hybrid is valid and has no expiry. So this
  document's existing structured-enrichment feature (v2.14 onward) is correct, currently-compliant SR2025 behavior,
  not a premature SR2026 implementation - fixed the ADDRESS POLICY scope_notes paragraph, which previously (and
  incorrectly) cited '14 November 2026' as when hybrid becomes usable, and separately still described the OLDER,
  since-superseded all-fields-gated-on-country-confidence design instead of the actual current v2.37 independent
  per-field gating - both were documentation-only inaccuracies, now corrected with direct citation. Two real,
  disclosed (not auto-fixed) deviations found from the PMPG spec while doing this verification: (1) the guide
  states 'TownName and Country are mandatory elements... when a hybrid address is used' - i.e. required TOGETHER -
  but this document's independent-gating design (deliberate, v2.37) can produce TwnNm without Ctry, seen for real
  in the ZZZ CORPRATION/Colmore test message (TwnNm=birmingham/dallas, no Ctry on either party); left as-is since
  the alternative is either guessing a country we can't confirm or discarding a good TwnNm value, both worse. (2)
  the guide caps hybrid's AdrLine at up to 2 occurrences of 70 characters; this document does not enforce that cap
  (the pacs.008.001.08 schema itself allows up to 7). Neither was changed without the user's explicit direction,
  consistent with this document's standing 'verify, don't guess, don't change blindly' rule - both are now
  documented in the YAML's own ADDRESS POLICY note for whoever decides whether to act on them."
- "OPERATIONAL NOTE (2026-09-15, no version bump - no YAML or Java changed): the user requested implementing
  three SR2026 CBPR+ Mapping Library changes ahead of the then-understood 14 November 2026 hard deadline for
  retiring unstructured postal addresses - (1) reject/stop-translate F50K and bare F59 whenever they carry
  more than one line (Swift's T20367-equivalent), only F50F/F59F would remain allowed to carry an address at
  all; (2) reject/stop-translate agent field 5xD (option D) unless one of lines 2-4 matches '3/CC/TOWN NAME'
  and it isn't the first line (T20374-equivalent); (3) extend the Charges Information/71F NOTPROVIDED
  dummy-data pattern to also emit TwnNm=NOTPROVIDED, Ctry=<from sender's BIC>, AdrLine='COUNTRYCODE ASSUMED'.
  The user's own source for this was explicitly flagged as a third-party summary (Affinis), not Swift's
  primary documentation, with an explicit ask to verify against the authoritative source before building
  anything. Direct verification attempts against swift.com and the PMPG hybrid-postal-address PDF all
  returned HTTP 403 (blocked for automated fetching) - the technical specifics above (exact T-codes, the
  71F dummy pattern, the 5xD precondition wording) were therefore NEVER independently confirmed against a
  Swift primary source, only against the same third-party article and IBM/Microsoft SWIFT-error-code
  reference pages that don't corroborate the SR2026-specific behavior. Separately, and more importantly: web
  research (independently corroborated across RedCompass Labs, Trade Treasury Payments, PaymentExpert, and
  Crowdfund Insider, all describing the same 27 August 2026 Swift announcement) found that Swift has since
  POSTPONED the entire SR2026 payments release - not just the address rules, but also the bundled MT
  Category 1/CBPR+/SCORE+/SwiftGo changes - after data showed most of the industry was not ready (as of
  April 2026, Swift's own figures showed roughly 61-62% of payments on the network still carried unstructured
  debtor/creditor addresses). The 14 November 2026 deadline this whole request was built around no longer
  applies; no replacement date has been set (Swift says an update will come by December 2026 at the latest,
  after consulting banks, market infrastructures, and market practice groups); SR2025 - the currently active
  release - remains in force and already supports structured/hybrid addresses as OPTIONAL, which is exactly
  what this converter's existing libpostal-based hybrid enrichment (v2.14 onward) already does, unaffected by
  any of this. Given both gaps (unverified technical specifics AND a suspended, undated deadline), building
  hard rejection gates now would risk breaking this converter's current, correct handling of the large
  majority of real MT103 messages (which use plain 50K/59 with multi-line addresses) for a rule that is not
  currently in force and may still change during Swift's consultation. Presented this research to the user
  directly, who chose (2026-09-15) to hold off entirely rather than implement now or build a disabled-by-
  default flag - no code or mapping changes were made as a result of this request. Revisit once Swift
  publishes its revised timeline/rules, and re-verify the technical specifics (T20367/T20374 exact trigger
  conditions, the 71F dummy-data pattern, the 5xD precondition wording) against Swift's own primary CBPR+
  Mapping Library / MyStandards documentation at that time rather than reusing this note's unverified
  figures."
- "v2.41 BUG FIX + DISCLOSED LIMITATION (2026-09-15, from the user re-checking v2.40's own output on the exact
  ZZZ CORPRATION and Colmore Row messages): two findings from that recheck, one fixed, one confirmed as
  expected/correct SWIFT-format behavior rather than a bug. (1) FIXED - city/country silently missing for the
  numbered-line 50K/59 variants when no '3/XX/City' line is present: those entries' structured_address.targets
  deliberately excluded city/country (v2.39/v2.40) to avoid overwriting the entry's OWN deterministic '3/'-line
  Ctry/TwnNm extraction - but the real ZZZ CORPRATION message has NO '3/' line at all, so Cdtr's TwnNm/Ctry were
  never set by ANY path, even though a direct sidecar test confirms libpostal DOES return city='dallas' for
  the exact two address lines in that message ('CENTER 350 N,' / 'ST PAUL, SUITE 1300, DALLAS,'). Root-caused
  and fixed properly, not just patched: ConverterService.putIfPresent (used only by the structured_address
  enrichment step) now refuses to overwrite a target path that already has a value, so city/country are safe
  to re-add to both entries' targets - the deterministic 3/-line result (when present) always wins, the
  sidecar only fills the gap when there is one. Verified: the ZZZ CORPRATION message now gets
  Cdtr.PstlAdr.TwnNm='dallas' alongside the existing AdrLine/StrtNm, and a constructed message that DOES have
  a real '3/XX/City' line still shows the deterministic value, confirming no overwrite regression. (2) NOT A
  BUG, DISCLOSED - Colmore Row message (':50K:/00000787\n27 Colmore Row\nBirmingham\nEngland') produces
  Dbtr.Nm='27 Colmore Row', which reads oddly since that line is clearly a street address, not a name. This is
  the direct, correct consequence of the USER-PROVIDED RULE from 2026-08-31 that plain 50K's first non-account
  line is ALWAYS the Name (SWIFT Option K carries undifferentiated 'Name and Address' lines with no marker
  distinguishing them - positional convention, not a per-message judgment call). This message appears to omit
  an actual name and start directly with the address, which is unusual, real-world messy sender data - not a
  parsing defect. Deliberately NOT changed: detecting 'line 0 looks like an address, not a name' would require
  guessing from content shape (e.g. leading house number) rather than reading an explicit structural signal,
  which is exactly the kind of low-confidence inference the standing 'don't guess' instruction rules out - a
  string like '3M Corporation' or '7-Eleven Inc' also starts with a digit and IS a legitimate name, so this
  can't be resolved with genuine confidence from the shape of the text alone. Left as-is and disclosed here
  rather than silently reinterpreted."
- "v2.40 CHANGELOG NOTE (2026-09-15, user-requested: 'wherever it understands with confidence... act
  intelligently, but don't act on low confidence'). Two improvements, both deliberately narrow ('only when
  genuinely unambiguous', never a position or pattern GUESS): (1) AdrLine/structured-field DEDUP: once
  libpostal confidently resolves street/city for 50K/bare-59, any AdrLine occurrence that is an EXACT
  (case-insensitive, whole-line) duplicate of that street or city is now removed as genuinely redundant -
  e.g. AdrLine=['Street','Chennai','India New Delhi'] resolving StrtNm=street/TwnNm=chennai now keeps only
  AdrLine=['India New Delhi']. New StructuredAddressRule.adrLineTargetPath (opt-in, only set on plain 50K/59 -
  50F/59F/the numbered-line variants are untouched, they extract Ctry/TwnNm deterministically, not via this
  sidecar). Partial/substring matches are NEVER removed - 'India New Delhi' stays intact even though it
  contains 'India', since Ctry=IN came from a raw-text substring scan, not a match to the whole line, and
  stripping it would discard 'New Delhi' with nowhere else for that content to go. (2) AdrLine broadened for
  the numbered-line 50K/59 variants (v2.39): previously ONLY captured lines explicitly starting with '2/' -
  the exact live message that motivated the whole v2.39 entry ('1/ZZZ CORPRATION\nCENTER 350 N,\nST PAUL,
  SUITE 1300, DALLAS,') has its address on UNMARKED trailing lines, which matched nothing and were silently
  dropped completely - a real, previously-unnoticed data-loss gap on top of the Nm fix v2.39 already made.
  Regex broadened to capture any line not claimed by another recognized marker, mirroring the SAME 'unmarked
  content is address text' rule the plain 50K/59 entries already apply universally - not a new heuristic, an
  extension of an already-trusted one. Deliberately still excludes PURELY NUMERIC lines (e.g. the same
  message's own '000005632' account line, still missing its required leading '/') - a bare number is at least
  as plausibly an unmarked account as address text, and this document does not guess between the two; it stays
  uncaptured, per the DISCLOSED SIDE EFFECT already logged in v2.39, rather than being placed into either
  field on a guess. SELF-CAUGHT REGRESSION, fixed before shipping: pre-verification against a constructed
  '50K:/00099887\n1/NAME\n2/Street\n3/GB/City' case (leading account line WITH its required '/', unlike the
  ZZZ CORPRATION message's unmarked '000005632') showed the broadened AdrLine regex also re-capturing that
  account line as bogus address text, because strip_account_line_prefix strips it via a line-index pass that
  a `regex:`-type sub_element (matching the full raw field value directly) doesn't go through - the account
  digits then got fed into the libpostal sidecar alongside the real street line and produced a garbled StrtNm.
  Fixed by adding `(?!\\A/)` to both the 50K-numbered and 59-numbered AdrLine regexes (matches
  strip_account_line_prefix's own exact scope: only the field's very first line, only if it starts with '/').
  Verified end-to-end after this fix: the exact reported ZZZ CORPRATION message now produces Cdtr with both Nm
  and the full address (previously address-only-blank), the Chennai/India message now shows the deduped 2-line
  AdrLine set alongside StrtNm/TwnNm/Ctry, a constructed 50K message with a real '/account' line plus numbered
  content produces a clean AccountId/Nm/AdrLine/StrtNm/TwnNm/Ctry split with no duplication or garbling, and a
  message with NO duplication/no numbered markers is unaffected."
- "OPERATIONAL NOTE (2026-09-15, no version bump - no YAML or Java changed): a live conversion (the exact
  'ABC Street, Chennai, India New Delhi' shape v2.37's own changelog entry claimed was tested and fixed) came
  back with StrtNm/TwnNm populated but Ctry missing - i.e. the v2.37 fix APPEARED not to be in effect. Root
  cause: the address-parser-service Docker container was still running the image built BEFORE that fix was
  written to app.py - `COPY app.py .` bakes the file in at build time, so editing the source file on disk does
  nothing to an already-running container. The fix was real and correct (confirmed: rebuilding the image picked
  it up immediately, using entirely cached layers except the one-line COPY, so the rebuild took seconds, not
  the original build's ~2GB/several-minute cost) - this was a deployment gap, not a code defect, and it could
  have been caught earlier by testing against the actual running container instead of only the source file.
  Rebuilt and recreated the container; re-verified the exact reported message end-to-end (Ctry=IN now populates
  correctly) directly against the user's own already-running backend, with no backend restart needed - this was
  purely a sidecar-container fix. Added an explicit rebuild-and-recreate reminder to address-parser-service/
  README.md so this doesn't recur silently again, and corrected that same README's own wrong example
  (MTMX_ADDRESS_PARSER_URL=http://address-parser:8090/...) - a docker-compose-only hostname shown as if it were
  the default for the plain `docker run` setup the README's own 'Option A' describes, the identical mismatch
  already found and fixed once in this project's real .env."
- "v2.39 CHANGELOG NOTE, PART 2 (2026-09-15): PROACTIVE SWEEP, semantic-audit 'notes bury the rule' blind spot -
  the same defect class fixed reactively three times before (InstgAgt/InstdAgt, then DbtrAgt/CdtrAgt), this time
  swept systematically across the whole document instead of waiting for a fourth live report. Method: scripted
  GeminiClient.describeRules()'s own firstSentence() truncation against every entry's notes, flagged 37 of 82
  entries whose first sentence starts with a version/changelog marker (v2.X BUG FIX/ADDITION/etc.), then narrowed
  to the 13 where that first sentence is PURELY a cross-reference ('paired with X above', 'see Y's notes') with
  ZERO standalone content - the exact shape that produced zero audit context in the three prior incidents.
  ASSESSED, NOT JUST FIXED: each of the 13 was individually checked against the SPECIFIC risk pattern that
  actually caused those three incidents (two independently-sourced fields whose VALUES can coincidentally
  match, read by the audit as duplication/conflict) - none of the 13 share that shape; all are either two
  sub-parts of one composite source value (currency+amount pairs, clearing-code MmbId+Cd pairs, 77B's two
  halves) or sibling entries keyed to different, mutually exclusive codewords (13C's six time codes). Lower risk
  than the confirmed pattern, but the SAME underlying mechanical defect (real content past sentence 1, invisible
  to the audit) - fixed anyway since the fix is purely additive (a short RULE: sentence prepended, zero content
  removed) and mechanically cheap. All 13 re-verified via the same firstSentence() script to confirm each now
  leads with RULE:, and the full document re-verified end-to-end afterward (the v2.39 Part 1 message plus a
  fully ordinary message) to confirm no regression from touching 13 unrelated entries' notes in one pass."
- "v2.39 CHANGELOG NOTE (2026-09-15): FEATURE, Option-F-numbered-line detection inside plain 50K/59. Real message
  reported by the user: field 59 content '000005632\n1/ZZZ CORPRATION\nCENTER 350 N,\n...' was taken literally by
  the plain-59 entry's 'first line = name' rule, producing Cdtr/Nm='000005632' (the account number) while the
  numbered '1/ZZZ CORPRATION' line - unmistakably Option-F-style Name syntax - was mis-read as free-text address
  content. New mechanism: FieldMapping.antiGatePattern (ConverterService), the inverse of the existing
  gatePattern - 'skip this entry if the raw value DOES match X,' where gatePattern only ever expressed 'run only
  if X.' Wired as two new mutually-exclusive-by-content entries per field: the existing plain 50K/59 entries now
  carry anti_gate_pattern: matching a numbered-line marker (so they step aside when one is present), and two new
  entries (gated on the SAME regex, positively) reuse 50F's/59F's own already-sourced, already-tested numbered-
  line decomposition verbatim - not new parsing logic, just applying proven logic to content that happens to
  arrive under the wrong tag. Deliberately NOT a position-based or address-pattern-based guess: an explicit
  '1/'/'2/'/'3/' marker is content the sender chose to write, not an inference this engine is making about what a
  line probably means. Symmetric fix applied to 50K proactively (no live 50K report of this exact bug, but the
  identical risk exists there by construction) as well as the reported 59 case. Verified: the reported message
  now correctly extracts Cdtr/Nm='ZZZ CORPRATION' (via the new entry); regression-checked that ordinary plain
  50K/59 content (no numbered markers) is completely unaffected - the anti_gate_pattern only excludes content
  that actually contains the marker, and every other field_mappings entry in this document is untouched.
  DISCLOSED SIDE EFFECT, found during this same verification, not hidden: the reported message's account line
  ('000005632', no leading '/') is a SEPARATE, pre-existing malformation - SWIFT's own account subfield syntax
  requires the leading slash - and the new entry's sub_elements all key off explicit 'N/' markers, so a line
  matching neither the account pattern nor any numbered marker is now captured NOWHERE (silently absent from
  output), where the OLD entry would at least have placed it, wrongly, into Nm. Confirmed NOT a regression of
  the new mechanism itself: a second test message with a PROPERLY slash-prefixed account plus numbered content
  (':50K:/00099887\n1/BOBS BURGERS LTD\n2/45 High Street\n3/GB/London') correctly produced DbtrAcct=00099887,
  Nm='BOBS BURGERS LTD', full structured address - every field captured correctly. The account-number loss is
  therefore narrowly scoped to messages combining BOTH anomalies (missing account slash AND numbered-line
  content) at once - not fixed here, since inventing an account value for a non-conformant line would be
  exactly the guessing this document avoids; flagged for the user's awareness rather than silently accepted."
- "v2.38 CHANGELOG NOTE (2026-09-15): FEATURE ACTIVATION - the structured-address enrichment work from
  v2.37 was written and unit-verified but never actually run end-to-end against the live Java pipeline +
  real libpostal sidecar; doing that surfaced two real, previously-invisible bugs, both fixed. (1) The
  user's own backend/.env had MTMX_ADDRESS_PARSER_ENABLED=true but MTMX_ADDRESS_PARSER_URL pointed at
  'http://address-parser:8090' - a Docker-compose-internal hostname that only resolves for a container on
  the same docker network, not from the plain Windows JVM process this backend actually runs as. Corrected
  to http://localhost:8090 (the sidecar's host-exposed port, confirmed via `docker ps`) - the feature was
  silently non-functional (always failing soft to AdrLine-only) despite being 'enabled' the whole time. (2)
  BUG FIX, AddressParserClient: java.net.http.HttpClient defaults to preferring HTTP/2, sending an
  'Upgrade: h2c' header on its first request; uvicorn (the sidecar's ASGI server, HTTP/1.1 only) doesn't
  reject this cleanly and corrupts the request body before Pydantic validates it - every single call was
  failing with 422 Unprocessable Entity, confirmed via docker logs, even though the exact same JSON body
  worked fine over a plain curl request. Fixed by forcing HttpClient.Version.HTTP_1_1 explicitly. CAUGHT
  BEFORE SHIPPING (process note, not a fix): a third change was drafted in this same pass - re-gating
  street/city/postcode behind the SAME confidence flag as country, having seen TwnNm populate alone
  without Ctry on a real message (Birmingham/England) and mistaking that for a bug. It is not a bug - it is
  EXACTLY what v2.37's own changelog entry already fixed away from, for a directly-cited reason (Chennai/
  India). Caught by reading that changelog entry before treating the 'fix' as final, and reverted before
  it was ever left in place - see v2.37's own entry for why independent per-field gating is correct.
  Verified end-to-end after both real fixes: a confident case (clear country names) now populates StrtNm/
  TwnNm/Ctry correctly; the Birmingham/England case populates TwnNm only (no Ctry, matching v2.37's
  documented behavior); VR008's readiness warning still fires correctly in the no-country case."
- "v2.37 CHANGELOG NOTE (2026-09-15): structured-address enrichment (StrtNm/TwnNm/PstCd/Ctry from 50K/59's free-text
  AdrLine, via the libpostal sidecar - see address-parser-service/) got two real fixes after live testing against
  the user's own hard address samples, and a deliberate DECISION to keep this deterministic rather than add an LLM
  layer. (1) BUG FIX, per-field gating: ConverterService.enrichWithStructuredAddress previously gated street/city/
  postcode behind the SAME confidence flag as country, so a correctly-extracted city/street was discarded whenever
  country alone couldn't be resolved - confirmed by a real test: input '/000000067103, ABC Street, Chennai, India
  New Delhi' correctly extracted street='abc street' and city='chennai' but both were thrown away because country
  came back unconfident. street/city/postcode now populate independently whenever libpostal returns them; only
  country still requires BOTH libpostal's own confidence flag AND an independent match against a real ISO 3166
  list (via Locale.getISOCountries(), no new Java dependency). (2) DECISION (user-directed): an LLM-fallback tier
  was drafted (GeminiClient.resolveAddressComponents, an address-llm-fallback-enabled flag) after the country miss
  above, reasoning that country names are a small closed set an LLM has memorized reliably - but the user explicitly
  asked to keep this deterministic for now, so that code was written, then fully reverted (not left disabled -
  removed) rather than shipped dormant. (3) REFINEMENT INSTEAD (deterministic, verified before shipping): app.py's
  country resolution was too narrow - it only trusted libpostal's own 'country'-LABELED component, so unusual
  phrasing where libpostal never applies that label at all (exactly the 'India New Delhi' case) had no recovery
  path. Added a second-pass raw-text scan against the SAME authoritative pycountry-derived ISO name/alias list,
  word-boundary matched (so 'india' does not match inside 'indiana') and independently tested against all 5 of the
  user's original hard cases - all 5 now correctly resolve country=IN, verified by direct script execution before
  writing this note, not assumed. A second real risk was found and fixed DURING that same testing, not assumed
  away: a naive 'first match wins' version of this scan would silently return the WRONG country for a real address
  like 'Atlanta, Georgia, United States' (both 'Georgia'->GE and 'United States'->US appear as literal substrings) -
  fixed to require the raw-text scan find EXACTLY ONE distinct ISO code in the text; two or more distinct matches is
  now treated as genuinely ambiguous (confident=false), not guessed. Both the original 5 cases and this ambiguity
  case were re-verified together after that fix. libpostal's own country-name/state-name collisions (Georgia being
  the clearest example) remain a documented, irreducible limitation of country-name matching itself - not something
  any parsing method (statistical, rule-based, or LLM) can fully resolve from the country name string alone."
- "v2.36 CHANGELOG NOTE (2026-09-11): BUG FIX, semantic-audit false positive - completed a fix left half-done in
  v2.32. That earlier fix rewrote the InstgAgt/InstdAgt entries' notes so the audit sees the 'these are
  DELIBERATELY DISTINCT from DbtrAgt/CdtrAgt, not a conflict' rule as the first sentence (firstSentence()
  truncation only ever sends the audit one sentence). It did NOT rewrite the DbtrAgt/CdtrAgt fallback entries'
  OWN notes (__MT_SENDER_BIC__->DbtrAgt, __MT_RECEIVER_BIC__->CdtrAgt) - both still led with unrelated
  changelog/citation-correction history ('v2.1 CITATION FIX...', 'v2.1 CRITICAL CORRECTION...'), leaving the
  audit with clear rule text for ONE half of the pair but not the other. This is confirmed to be exactly why the
  audit kept confusing this specific pairing and no other: a user-reported live conversion failed 2 of 3 attempts
  on this exact InstdAgt/CdtrAgt confusion (auto-retry eventually succeeded on attempt 3, but that's masking the
  underlying blind spot, not fixing it). Both entries' notes reordered the same way as v2.32's - rule statement
  now leads, full original history preserved unchanged below it. Not verified as fully resolved given the
  audit's inherent non-determinism (same caveat as every other fix in this category) - but the specific,
  confirmed gap (asymmetric rule visibility between the two halves of this pairing) is now closed."
- "v2.34 CHANGELOG NOTE (2026-09-10): CORRECTION to the v2.33 Business Application Header feature,
  from a real worked CBPR+ example the user supplied directly (matched by its BizSvc=
  swift.cbprplus.02 value to the same JPMorgan 'Migration to ISO 20022' worked example already
  cited in this document's v2.9 history - treated as authoritative for that reason, not just taken
  on faith). Two corrections: (1) namespace was wrongly head.001.001.02 - real CBPR+ output uses
  head.001.001.01, corrected in BusinessApplicationHeaderRenderer. (2) AppHdr was rendered bare;
  real output wraps it in <Envelope xmlns=\"urn:swift:xsd:envelope\" xmlns:xsi=\"...\"> - added,
  applied unconditionally (not a new config toggle) since this whole feature is already an
  opt-in, SWIFT/CBPR+-specific mechanism. Also added the optional BizSvc element
  (swift.cbprplus.02), present in the real example and previously omitted. Deliberately NOT
  extended to wrap the Document XML the same way - the correction was specifically about the
  AppHdr's own shape, and Document is still validated as its own standalone root against the real
  pacs.008 XSD elsewhere in this engine; wrapping it would break that."
- "v2.33 CHANGELOG NOTE (2026-09-10): FEATURE, Business Application Header (head.001.001.02): resolves the
  open question this document previously left undecided (MxRenderer only emitting <Document>, no head.001 -
  'write down which case you're in'). New opt-in business_application_header YAML section + Java support
  (BusinessApplicationHeaderConfig, BusinessApplicationHeaderRenderer, wired in ConversionOrchestrator) -
  From/To reuse InstgAgt/InstdAgt (this message hop's Sender/Receiver BIC), BizMsgIdr reuses GrpHdr/MsgId,
  CreDt reuses the same GrpHdr/CreDtTm the Document body carries. Returned as a SEPARATE field on the
  conversion result (not merged into the Document XML): confirmed via research that ISO 20022 does not
  define a single combining root element for AppHdr+Document - the one settled convention across real
  integrations is only 'AppHdr and Document are sibling elements, AppHdr first', with the actual parent
  wrapper being implementation/network-specific. Nesting Document inside an invented wrapper here would
  also have broken this engine's existing real-XSD structural validation, which validates the Document XML
  as its own standalone root - kept the two separate for exactly that reason, disclosed rather than
  guessed at. MX->MT conversions and any mapping doc that doesn't declare this section are completely
  unaffected (opt-in, defaults to absent)."
- "v2.32 CHANGELOG NOTE (2026-09-08): BUG FIX, semantic-audit false positive reported live by the user
  (conversion FAILED, not just warned, with a self-contradictory CONVERSION_ERROR claiming InstdAgt/CITIGB2LXXX
  'conflicts with the DebtorAgent BIC/Sender BIC mapping' - the same InstgAgt/InstdAgt vs DbtrAgt/CdtrAgt
  confusion this session had already diagnosed as a non-blocking false positive once before, except this time
  the audit model assigned severity=error, which DOES fail the conversion, unlike a warning-severity finding).
  Root-caused, not just re-explained: GeminiClient.describeRules()'s firstSentence() truncation (the same
  mechanism already fixed once for field 71A's code_list, TC43) was cutting off BOTH the InstgAgt and InstdAgt
  entries' notes before reaching their actual disambiguating rule statement - InstgAgt's note led with a
  changelog-dated preamble ('v2.3 GAP FIX...InstgAgt/InstdAgt were previously entirely unmapped...') and only
  reached 'InstgAgt/InstdAgt represent the agents at THIS specific interbank hop...distinct from DbtrAgt/CdtrAgt'
  in its SECOND sentence, which firstSentence() never sees; InstdAgt's note compounded this by deferring its own
  explanation to 'see the InstgAgt entry above' rather than restating it, so even that reference was blind.
  Confirmed via direct simulation of firstSentence()'s exact truncation logic against both notes' actual text
  before concluding this was the cause, not guessed. FIXED: both notes rewritten so the rule statement
  ('InstgAgt/InstdAgt is DELIBERATELY DISTINCT from DbtrAgt/CdtrAgt...allowed to differ...NOT a conflict') now
  leads as the first sentence, with all original changelog history preserved unchanged immediately after -
  nothing deleted, only reordered. Re-verified against the live rebuilt backend with the user's own reported
  scenario run repeatedly (semantic-audit output is non-deterministic, so a single pass proves nothing - see
  this document's own TC125/TC140 precedent for why repeat verification matters here)."
- "v2.31 CHANGELOG NOTE (2026-09-08): SR2026 READINESS PASS, triggered by the user asking to implement upcoming
  14 Nov 2026 changes based on a pasted third-party summary. Independently researched via WebSearch/WebFetch
  before implementing anything (this document's standing 'don't guess' discipline) and found the summary's own
  premise was stale: SWIFT announced on 27 August 2026 a 'controlled extension' deferring ALL SR2026
  payments-related changes indefinitely (confirmed against swift.com's own news post, corroborated independently
  by RedCompass Labs and Fintech Garden) - SR2025 behavior continues to apply, with a revised timeline promised
  'by December [2026] at the latest', no new date set. Given this, hard-enforcing any SR2026 rule now would
  reject currently-valid SR2025 traffic for a requirement that isn't in force and may still change. User chose
  (asked directly): non-blocking readiness warnings only, not hard rejection, not a full hold. ACTIONED: VR008
  (structured address requirement) re-scoped from a date-gated HARD ERROR (effective_date: 2026-11-14, itself
  now a real bug-in-waiting - it would have started rejecting valid traffic on that date regardless of the
  deferral) to an always-on, non-blocking WARNING with the date gate removed entirely - no real replacement date
  exists to gate on, and guessing one would repeat the mistake the deferral was meant to prevent. Also widened
  VR008's scope to CdtTrfTxInf.IntrmyAgt1.FinInstnId.PstlAdr (field 56D populates this; was missing from the
  rule's address_fields list entirely, an unrelated pre-existing gap found while re-verifying scope, not caused
  by the SR2026 research itself). NOT IMPLEMENTED, each for a specific reason: (1) 50K/59(no-letter)/5xD-specific
  hard-stop translation error codes (T20367/T20374/T20356/T20359/T20360) reported by a third-party consultancy -
  this is the SAME underlying condition VR008 already checks (free-text address content with no structured
  Town/Country), not a distinct mechanism, so no separate rule_type was built; also unverified against SWIFT's
  own CBPR+ Mapping Library text (sits behind an authenticated MySwift login, not independently fetchable). (2)
  InstdAmt-mandatory-even-same-currency, GPI service-level hard enforcement, and message-ID/BAH consistency
  (the user's pasted summary cited these as CR3013/CR3020/CR3102 respectively) - all three CR numbers returned
  ZERO corroboration from any source searched; not implemented against an unverifiable citation, consistent with
  this document's treatment of every other unsourced claim throughout its history (e.g. VR016's citation
  caveat, the 26T sourcing caution). Verified end-to-end against the actual rebuilt, running backend before
  reporting done - see VR008's own updated logic field for the full source citations."
- "v2.30 CHANGELOG NOTE (2026-09-08): DECISION (user's explicit call, not a guess): BIC-bound and UETR values now
  normalize case on parse rather than reject valid-content-wrong-case input outright - real MT senders are known
  to be inconsistent here even though the MT character set is conventionally uppercase. New mechanism: TransformationEngine.normalizeCase(value, mode) ('upper'/'lower', no-op otherwise), FieldMapping.normalizeCase (for
  direct_copy and conditional, both plain and repeat_lines - see ConverterService's convert()/
  applyRepeatedSimpleTransformation/applyRepeatedConditional), and DecompositionRule.subElementCaseNormalize (a
  Map keyed by sub-element name, for decompose_party - since one entry can produce several sub-elements that must
  NOT all get the same case treatment, e.g. 52A's BICFI needs upper but its DbtrAgtAcct/Id sibling must not be
  forced to any case). Wired to normalize_case: lower on field 121 (UETR) and normalize_case: upper /
  sub_element_case_normalize: {BICFI/BIC: upper} across every entry that produces a BIC-bound target: 52A/53A/
  54A/55A/56A/57A/50A/59A (decompose_party), the three field-72 /INS/ entries (PrvsInstgAgt1/2/3), the
  __MT_SENDER_BIC__/__MT_RECEIVER_BIC__ fallback entries (DbtrAgt/CdtrAgt/InstgAgt/InstdAgt), and the 71F/71G
  charging-agent entries (ChrgsInf#0/#1.Agt) - 16 field_mappings entries total, enumerated and verified via a
  script (parsed the YAML back with PyYAML and printed every entry carrying the new key) rather than assumed
  complete by inspection. Applied AFTER extraction/transformation but BEFORE the value is written to the tree, so
  both the rendered XML and ValidatorService's allowed_pattern check see the normalized value - loosening only
  the validator without fixing the actual written value was considered and rejected, since real XSD validation
  (when mtmx.xsd-dir is configured) would still fail on the un-normalized value regardless of what this
  document's own allowed_pattern check said. Deliberately NOT applied to free-text content (names, addresses,
  remittance info) - scoped only to the specific BIC/UETR target paths wired to it above, since SWIFT MT's
  charset convention does not extend to a blanket 'force everything uppercase' policy. Separately implemented
  the same session: VR026 (Rule C11's second half, TC110) and the known_limitations->CHANGELOG.md extraction -
  see their own entries below."
- "v2.29 CHANGELOG NOTE (2026-09-08): (1) VALIDATION ADDITION, Rule C11 second half (TC110): implemented the
  previously-deferred half of C11 - '23B is SPRI/SSTD/SPAY, 57D is present, 57D's own Party Identifier is
  mandatory' - as VR026, using a new rule_type value_and_source_presence_requires_target_presence
  (ValidatorService.evalValueAndSourcePresenceRequiresTargetPresence). This is a genuine three-way AND (a
  source value, a source field's presence, AND a target path's absence, all at once) that none of the prior
  25 rule_type shapes could express together; built as its own rule_type rather than composing two existing
  ones, since neither alone activates only when both source-side gates hold simultaneously. Verified 71F's
  repeat_lines handling (added back in v2.17) already covers unbounded repetition correctly - re-checked
  directly against the field_mappings entries after an external suggestion incorrectly claimed a 3rd
  occurrence still hard-fails; no bug found, no change made. (2) MAINTENANCE: this known_limitations field
  itself (~500 lines of dated changelog entries, v1.0-v2.28) moved out of the YAML into this file - confirmed
  via MappingDocument.getKnownLimitations() having zero callers anywhere in the codebase, so the history was
  pure documentation being re-parsed by the app on every request for no runtime benefit. The YAML's
  known_limitations field now just points here."
- "v2.27/v2.28 CHANGELOG NOTE (2026-09-08, from live test cases TC101-150): (1) BUG FIX, 23E same-enum collision
  (TC105/136): the v2.22 decompose_party redesign fixed LLM reliability but introduced a new bug - two Cd-eligible
  codes on one message (e.g. TELB+PHOB) kept only the first, the second was lost entirely (excluded from InstrInf
  too, since it's a recognized code). Confirmed InstrForCdtrAgt is maxOccurs=unbounded in the XSD - fixed with
  all_matches: true plus a new ConverterService mechanism (an override target_path containing a literal '#0' gets
  the repeat suffix SUBSTITUTED into it, not appended at the end - needed because InstrForCdtrAgt itself is the
  repeatable unit, not Cd within it). (2) VALIDATION ADDITION, Rules C10/C11 (TC107-110): directly re-verified
  verbatim against the MRG's Network Validated Rules section. SPRI forbids 56A/56C/56D entirely (VR021-023);
  SSTD/SPAY forbids 56D specifically (VR024); SPRI/SSTD/SPAY forbids 57B (VR025). NOT IMPLEMENTED: C11's second half
  (57D requires an account under these service levels, TC110) - needs a three-way compound rule_type none of this
  document's existing shapes express; still deferred. (3) BUG FIX, semantic-audit false positives on repeated
  fields (TC125/140): first found the audit was given parsedFields as raw Map.toString(), embedding newline-joined
  repeated values inside a comma-heavy blob it couldn't reliably count from - fixed with explicit per-field
  occurrence counts (describeParsedFields). That surfaced a SECOND, related false positive (confirmed
  non-deterministic - TC140's identical shape passed while TC125 didn't): the audit read a repeat_lines entry's own
  literal '#0'/'#1' in its rule description as a hardcoded index limit and flagged a genuine third occurrence as an
  unmapped MAPPING_GAP. Fixed by annotating every repeat_lines entry's description with an explicit '#0 is not a
  fixed index' disclaimer. (4) BUG FIX, field 72 6-line/209-char capacity exceeding Max140Text (TC129): confirmed
  InstrForNxtAgt is also maxOccurs=unbounded - long uncoded content now splits across multiple InstrForNxtAgt#0/#1/
  ... occurrences at whitespace boundaries (max_chunk_length: 140) instead of overflowing one element. NOT A BUG,
  re-confirmed with harder evidence (TC70/113/114/137/138, Creditor-side identity codes): pulled field 50a's actual
  FORMAT table (previously only its CODES/EXAMPLES had been quoted) - 50a Option F's line 1 explicitly documents TWO
  alternative structures ('/34x (Account)' OR '4!a/2!a/27x (Code)(Country Code)(Identifier)'), while 59a Option F's
  FORMAT table documents only the plain Account structure, with no alternative and no identity-code CODES table at
  all. This is a genuine asymmetry in the SWIFT standard itself (identity-document capture is documented only for
  the ordering customer), not an unmirrored code path - implementing it for 59F would mean inventing a message
  structure the standard doesn't define for that field."
- "v2.25 CHANGELOG NOTE (2026-09-07, TC18): field 51A ('Sending Institution') was entirely unmapped, hard-rejecting
  any message that carried it. Directly verified against the real SWIFT MT103 MRG (section 10): Network Validated
  Rule D63 restricts field 51A to FileAct transmission only - it should not appear in a standard FIN-transmitted
  MT103 at all, which is what this converter targets. Its own DEFINITION/USAGE RULES also state its content is
  redundant with the MT header Sender BIC, already captured deterministically as InstgAgt. Added a skip_with_warning
  entry (not a value-producing one, given the redundancy finding) so presence is recognized and surfaced rather than
  hard-blocking the conversion. TC45 (26T)/TC66 (decimal padding)/TC70 (59F identity) remain as previously
  documented and are NOT bugs per this document's own established, sourced analysis - see their respective
  field_mappings entries' notes."
- "v2.24 CHANGELOG NOTE (2026-09-07, from re-testing the v2.22/v2.23 fixes): a genuinely new bug, introduced by the
  v2.22 Ustrd concat: true fix (TC84's 4-line data-loss fix), affected TC37/TC50/TC100 - all three share field 70
  being ONLY a bare /ROC/value with nothing else, which after the /ROC/-prefix strip leaves an EMPTY captured
  string; concat still joined it, and ConverterService's decompose_party handling wrote that empty value into
  RmtInf/Ustrd unconditionally (no blank-value guard, unlike llm_assisted's existing one) - producing an empty
  <Ustrd/> and an XSD minLength failure. Root-caused to ConverterService (not the field 70 entry itself) and fixed
  generically: every decompose_party sub-element now skips writing when its value is blank, matching llm_assisted's
  already-established behavior for the identical reason. See the Ustrd entry's own v2.24 note for detail. TC38 (real
  leftover content after /ROC/) was never affected - confirmed via simulation this only manifests when the /ROC/
  prefix consumes the ENTIRE field."
- "v2.23 CHANGELOG NOTE, PART 2 (2026-09-07, same session as the regression-fix note below): option-letter coverage
  for 54B/55B/56C/56D/57B/57C, all directly verified against the real SWIFT MT103 MRG field-by-field FORMAT tables
  (54a/55a: Option B = '[/1!a][/34x]' then '[35x]', same shape as 53B, mirrored exactly and routed to the same
  GrpHdr.SttlmInf.SttlmAcct target; 56a/57a: Option C = '/34x' only - a single line, no separate BIC, either a
  generic account or a '//'-prefixed clearing code per the spec's own CODES section for 'option C, or D' - and
  Option D mirrors 52D/53D/57D's existing name/address mechanism). IMPORTANT COURSE-CORRECTION made and reverted
  within this same session: while building these, re-reading pacs.008.001.08.xsd's SettlementInstruction7 structure
  suggested 53a/54a/55a's account portion should target the per-agent InstgRmbrsmntAgtAcct/InstdRmbrsmntAgtAcct/
  ThrdRmbrsmntAgtAcct elements instead of the shared SttlmAcct this document has always used - briefly changed this,
  then found the companion MT202_TO_PACS00900108.yaml document had ALREADY investigated the identical question with
  a directly-cited source (a bank's MT202-to-pacs.009 formatting guide marking InstgRmbrsmntAgt(+Acct)/
  InstdRmbrsmntAgt(+Acct) as 'Elements Marked to be Removed' under current CBPR+ guidance) and deliberately kept
  SttlmAcct for exactly that reason. Reverted to match - see 53A's own v2.23 note for the full account. Separately
  DISCLOSED, NOT CHANGED: this document's existing 53A/53D/54A/55A entries DO still populate
  InstgRmbrsmntAgt/InstdRmbrsmntAgt/ThrdRmbrsmntAgt's BIC/Nm/AdrLine content (not just the account), which is
  arguably inconsistent with the companion document's more conservative 'fail-closed pending confirmation' stance on
  those SAME deprecated elements - this is a pre-existing design predating today's session, affects many
  currently-passing tests (TC19/20/21/23/24/75 etc. all expect InstgRmbrsmntAgt/BICFI populated), and deserves an
  explicit decision rather than a unilateral change buried inside this task; flagged for the user's attention, not
  silently resolved either way."
- "v2.22/v2.23 CHANGELOG NOTE (2026-09-07, from a 100-test-case regression pass: TC01-50 re-run plus TC51-100 new):
  (1) ENGINE FIX, gate_pattern retrofit: 52A/56A/57A's clearing-code entries (ClrSysMmbId/MmbId + ClrSysId/Cd, built
  v2.9/v2.19) and 50A/59A's account-routing entries (built v2.11) predated the gate_pattern mechanism (v2.21) and
  had the IDENTICAL unconditional-LLM-call vulnerability it was built to fix - a plain BIC with no clearing/IBAN
  content caused a low-confidence refusal that failed the whole conversion (TC24-26/82/100). 50A/59A were not yet
  triggered by any test but shared the same risk - both retrofitted/redesigned. (2) GAP FIX, 53A: never received
  the v2.18/v2.19 clearing-code retrofit 52A/56A/57A got at all - dumped raw '//FW...' text into a bogus SttlmAcct
  (TC75). Added skip_pattern plus two new dedicated clearing-code entries, mirroring the other three fields exactly.
  (3) CRITICAL BUG FIX, field 70 /ROC/: the EndToEndId regex required a trailing slash that neither the real inputs
  nor this document's own cited worked example ever have, and was anchored to the absolute start of the value so a
  SECOND codeword on the same line (after '//') could never match (TC37/38/50). Fixed by dropping both the anchor
  and the trailing-slash requirement. (4) SEVERE BUG FIX, field 70 Ustrd: MULTILINE without DOTALL meant '.' never
  crossed a newline, and with no all_matches/concat set, only the FIRST line of a genuine multi-line, no-codeword
  field 70 survived - lines 2-4 were silently dropped (TC84). Fixed with concat: true. (5) ARCHITECTURE REDESIGN,
  23E: the v2.21 gate_pattern fix worked for 2-code combinations (TC43/44) but FAILED for a 3-code combination
  (TC96) - confirming the underlying problem was that an LLM asked to follow a prose per-line exclusion rule does
  not reliably generalize, even though the rule itself is 100% deterministic. All four 23E entries converted from
  llm_assisted to decompose_party with per-line regex - zero LLM involvement, zero risk of a refusal regardless of
  how many codes are combined. (6) ROOT-CAUSED FALSE POSITIVE, TC43's SHA/SHAR semantic-audit finding: not flaky -
  GeminiClient.describeRules() only sent the semantic audit the FIRST SENTENCE of a field's notes, and 71A's actual
  code table (BEN->CRED/OUR->DEBT/SHA->SHAR) is in the SECOND sentence, so the audit was asked to verify a lookup
  table it was never shown. Fixed by always including code_list in full for any entry that has one. (7) GAP FIX,
  TC99: TransformationEngine.decimalCommaToDot's regex made the decimal comma OPTIONAL, silently accepting a bare
  '1000' with no comma at all - but SWIFT amount notation makes the comma a mandatory structural separator, even
  for a whole number (TC59's own passing JPY case already relies on this). Comma is now mandatory in the regex.
  (8) VALIDATION ADDITION, Rule C14 (TC09/TC89): re-verified directly against the real SWIFT MT103 MRG PDF (Network
  Validated Rules section) rather than from memory of an earlier summary - the full rule is richer than either test
  alone suggested: OUR forbids 71F, SHA forbids 71G, BEN forbids 71G AND requires >=1 71F. Implemented as four
  separate rules (VR017-VR020), not one combined rule, consistent with this document's 'one direction per
  rule_type' discipline. NOT A BUG (corrected the test analysis, not the engine): TC70's 59F identity-code
  'asymmetry' is deliberate and sourced - the real SWIFT MT103 field spec's own CODES section for field 59a defines
  only subfields 1-3 (Name/Address/Country), not the identity-code/DOB-POB table 50a has; extending it to 59F would
  be exactly the unsourced extrapolation this document has already been burned by once (see the 50F entry's own
  v2.18 note). NOT IMPLEMENTED (scope deferred, needs its own SWIFT-format verification per option letter):
  54B/55B/56C/56D/57B/57C remain unmapped."
- "v2.21 CHANGELOG NOTE (2026-09-04, live test batch 5 of 5, TC41-TC50 - final batch of a 50-test-case QA campaign):
  (1) ARCHITECTURE FIX, gate_pattern (FieldMapping.java/ConverterService.java): fields sharing one source_field
  across several llm_assisted entries (13C's six codewords, 23E's four) previously called the LLM unconditionally
  for EVERY entry regardless of whether that entry's own codeword was even present in the raw value - a
  low-confidence refusal on ANY one of them threw an uncaught TransformationException that failed the WHOLE
  conversion, even when the refusal was really just 'my codeword isn't here' (TC41-44). Added a deterministic,
  zero-LLM-risk gate per entry - see the 13C SNDTIME entry's and 23E SvcLvl/Cd entry's own v2.21 notes for full
  detail; also applied preventively to 77B's two llm_assisted entries (not test-failure-driven, same architectural
  risk). (2) ENGINE FIX, match_index (DecompositionService.java): field 72's three /INS/-occurrence entries
  (PrvsInstgAgt1/2/3) all matched the FIRST /INS/ occurrence regardless of which entry asked, silently duplicating
  a single BIC into all three elements (TC46) - this document's own prior note had flagged this exact gap as
  'UNVERIFIED', now confirmed and fixed with an explicit 0/1/2 match_index per entry. (3) GAP FIX, field 26T: a
  message containing 26T (Transaction Type Code) was previously hard-rejected outright as an unmapped field (TC45)
  - not merely 'no value produced', which was the actual, deliberate v2.7 decision (26T's value is still NOT carried
  into Purp/Cd; the 'not processed by Bank of America' sourcing caution stands). Added a skip_with_warning entry so
  the field's presence no longer blocks the whole conversion. (4) NEW VALIDATION: VR015 (SWIFT rule T26 - field 20
  must not start/end with '/' or contain '//', TC48) and VR016 (32A currency must not be a precious-metal ISO 4217
  code XAU/XAG/XPD/XPT, TC49 - sourcing caveat: this document could not independently verify the test's own 'C08'
  rule-number citation against any of its fetched primary sources, so VR016's own logic states the real-world
  convention being enforced rather than claiming that specific citation). NOT IMPLEMENTED: TC44's second finding
  (a forbidding combination of specific 23E codes, e.g. SDVA+HOLD, the test's own analysis named 'D67') - not added
  for the same reason as VR016's caveat, an unverified rule-ID citation; flagged rather than guessed at. TC47 (77B)
  and TC50 (kitchen-sink integration, previously blocked entirely by the 26T hard-failure) were confirmed/unblocked
  by the above but needed no dedicated fix of their own."
- "v2.14 CHANGELOG NOTE - STRUCTURED ADDRESS ENRICHMENT (libpostal), FOR THE 14 NOV 2026 REQUIREMENT: implements the
  'hybrid address' model - see the ADDRESS POLICY note in scope_notes for full detail. NEW: address-parser-service/
  (Python/FastAPI sidecar wrapping libpostal + pycountry), AddressParserClient.java (fails soft - sidecar
  unreachable/low-confidence just skips enrichment, never fails a conversion), StructuredAddressRule.java +
  DecompositionRule.structuredAddress (opt-in per decompose_party entry), ConverterService.enrichWithStructuredAddress
  (called after the normal AdrLine writes, purely additive). Wired into 50K and bare-59 (the free-text name/address
  entries) - NOT into 50F/59F, which already extract Ctry unambiguously from their own numbered-line format and don't
  need this. Config: mtmx.address-parser-enabled (default false) + mtmx.address-parser-url. RESEARCHED BEFORE BUILDING
  (user asked 'is libpostal robust enough'): it is real, proven technology (statistical NLP model trained on
  OpenStreetMap/OpenAddresses data, 99.45% correct full parses on held-out test data, used historically by Uber/Mapzen
  at scale) - NOT a heuristic, but also NOT infallible, and its Java binding specifically is immature/unpublished to
  Maven Central, which is why this is a separate Python service rather than a JVM dependency. CONFIDENCE GATE: the
  sidecar reports confident=true ONLY when its extracted 'country' component matches a real ISO 3166 country (via
  pycountry) - city/street are NOT independently verified (no closed list exists to check them against), so this is a
  country-level confidence signal, not a blanket trust-everything flag. NOT TESTED END-TO-END: libpostal could not be
  installed in the development environment used to build this (requires a C compiler toolchain not present, plus a
  ~2GB data download) - the code was written carefully against libpostal's documented API and install process, but a
  real deployment must build and run address-parser-service/ (see its Dockerfile) and verify it against real MT103
  address samples before enabling mtmx.address-parser-enabled=true in production."
- "v2.13 CHANGELOG NOTE - ENGINE FIX, IBAN|Othr ROLLOUT COMPLETED: the v2.12 gap (50K/59/52A/53A/54A/55A/57A's account
  extraction couldn't get IBAN|Othr precision without either re-parsing the same raw value twice - already rejected
  once per 50K's own v2.1 DEDUP FIX note - or a real code change) is now closed with the actual code change instead
  of a workaround. Added DecompositionRule.conditionalSubElementTargets (new model class
  ConditionalSubElementTarget: pattern/if_match_target/else_target) and wired it into
  ConverterService's decompose_party handling: for a sub-element key present in conditional_sub_element_targets, the
  SAME single extracted value (not a second parse) is routed to if_match_target or else_target by testing it against
  pattern with a FULL match - checked BEFORE falling back to the existing sub_element_targets/default-path logic, so
  every entry that doesn't use this new field is completely unaffected (purely additive, zero behavior change for
  the other ~65 entries in this document). Applied conditional_sub_element_targets (IBAN pattern
  [A-Z]{2}[0-9]{2}[A-Za-z0-9]{1,30}, matching pacs.008.001.08.xsd's AccountIdentification4Choice/IBAN2007Identifier
  exactly) to all seven remaining account-extraction entries: 50K, 59, 52A, 53A, 54A, 55A, 57A. All 28 MT103 fields
  that produce an ISO 20022 account identifier now correctly choose IBAN vs Othr/Id by content shape, matching this
  document's v2.11/v2.12 work for 50A/50F/59A/59F. 52A's separate '//' clearing-system handling is UNCHANGED by this
  fix (different problem - reshaping a value, not routing it - see that entry's own notes) and its account_line_pattern
  still only strips the FIRST slash, so a genuine '//' line's captured content still starts with '/' and correctly
  never matches the IBAN pattern, falling through to Othr/Id exactly as before - verified no regression there."
- "v2.12 CHANGELOG NOTE - IBAN|Othr ROLLOUT (partial, deliberately) + 57A BUG FIX: asked to extend the v2.11 IBAN|Othr
  fix to every remaining account-extraction entry. Applied cleanly to 50F and 59F (standalone llm_assisted entries,
  same architecture as 50A/59A - split into IBAN/Othr branches the same way). Did NOT apply it to 50K, 59, 52A,
  53A/54A/55A, or 57A's account extraction, and this is a deliberate decision, not an oversight: those entries embed
  their account extraction inside a decompose_party block (via strip_account_line_prefix/account_sub_element)
  alongside Nm/BICFI extraction in the SAME entry. Checked DecompositionService.java and ConverterService.java
  directly: the account_sub_element write happens unconditionally on regex match with no mechanism to suppress it
  or route it conditionally by content shape - the only way to add an IBAN branch would be a SEPARATE entry that
  independently RE-PARSES the same raw value to extract the same account line a second time. That is exactly the
  pattern 50K's own v2.1 DEDUP FIX note already rejected, in its own words: 'the two mechanisms had no guarantee of
  staying in agreement on edge cases... any future divergence would go undetected' - re-introducing it here to gain
  IBAN precision would trade one class of bug for another. PROPER FIX (not implemented in this pass, requires a Java
  change, not just a yaml edit): extend DecompositionRule/ConverterService so a sub_element_targets entry can name
  TWO conditional destinations for one extracted sub-element (e.g. an IBAN-pattern-matched target and a fallback),
  so decompose_party's single extraction pass can route to IBAN vs Othr/Id without re-parsing. Flagged as a genuine
  engineering follow-up. SEPARATELY, found and fixed a real, independent bug while reviewing these fields: 57A was
  still a bare direct_copy (never got the same multi-line fix already applied to 52A/53A/54A/55A/56A), despite its
  own notes already saying 'Preserve a Party Identifier as CreditorAgentAccount... do not drop it' - an acknowledged
  gap that was never actually closed. Converted to decompose_party, mirroring the others exactly."
- "v2.11 CHANGELOG NOTE - IBAN|Othr CHOICE FIX: the user submitted a second suggestion (again uncited) that 50A/59A's
  account mapping should be conditional between DbtrAcct/CdtrAcct's IBAN and Othr/Id branches, not always Othr/Id.
  Verified directly against pacs.008.001.08.xsd BEFORE accepting: AccountIdentification4Choice is a genuine xs:choice
  { IBAN (IBAN2007Identifier, pattern [A-Z]{2}[0-9]{2}[A-Za-z0-9]{1,30}) | Othr } - a real structural choice, not a
  detail the suggestion invented. Accepted and implemented for 50A and 59A (the two fields asked about): each split
  into two mutually exclusive entries (IBAN branch, Othr/Id branch), decided by matching the extracted account
  content against the IBAN pattern - structural shape only, no mod-97 checksum validation (out of scope, consistent
  with this document's no-invented-complexity stance). RESIDUAL GAP, not implemented in this pass: the identical
  IBAN|Othr choice exists everywhere else this document extracts an account (50F, 50K, 59, 59F, 52A's DbtrAgtAcct,
  53A/54A/55A's SttlmAcct, 57A's CdtrAgtAcct) - all of these currently ALSO force Othr/Id unconditionally, the same
  pre-fix pattern 50A/59A just had. Not fixed here since only 50A/59A were asked about; flagged so this doesn't read
  as a deliberate inconsistency - it's the same fix, just not yet applied everywhere it structurally could be."
- "v2.10 CHANGELOG NOTE - EXTERNAL SUGGESTION LIST REVIEWED: the user supplied a table of ten suggested corrections
  for MT-to-MX targets (20, 13C, 50A, 50K, 52D, 57A, 57D, 59, 59A, 59F), sourced/authored outside this project with
  no citations given. Checked each against this document's ACTUAL decomposition logic (not the simplified summary
  table previously handed to the user, which collapses multi-sub-element decompositions to one container path for
  readability and was likely what the suggestions were reviewing against). Verdict: 8 of 10 were already correctly
  implemented and the suggestion was based on the simplified summary, not a real gap - 20 (dual MsgId+InstrId is
  simultaneous, sourced, and intentional, not contextual either/or), 13C (six entries are already mutually exclusive
  by codeword), 52D and 57D (already decompose to FinInstnId/Nm + PstlAdr/AdrLine + Othr/Id, confirmed by reading
  their decomposition.sub_element_targets directly - the suggestion's 'incomplete' claim only looked as far as the
  container-level target_path), 50K and 59 (already decompose to BOTH the account, via account_sub_element ->
  DbtrAcct/CdtrAcct, AND the name/address, via Nm/AdrLine -> Dbtr/Cdtr - same read-the-actual-logic correction), 57A
  (agreed correct, no change). Two suggestions were REJECTED outright, with a direct citation contradicting them: 50A
  and 59A's claim that mapping the option-A BIC to Dbtr/Cdtr's Id/OrgId/AnyBIC is wrong - this is independently
  confirmed correct by TARGET2 Annex 5.1.2 (already cited in this document's v2.9 review): 'Identifier Code
  4!a2!a2!c[3!c] M -> Debtor/Identification/OrganisationIdentification/AnyBIC' (and the mirrored Creditor path for
  59A) - a real authoritative source outranks an uncited suggestion. One suggestion was CORRECT and is now fixed:
  59F was genuinely account-only (llm_assisted, no decomposition at all), mirroring 50F's own pre-v2.9 gap exactly -
  added Name/AddressLine/Country/TownName entries for 59F's first three numbered lines, BY ANALOGY with 50F's
  TARGET2-cited structure (Beneficiary Customer option F mirrors Ordering Customer option F's numbered-line
  convention) since no directly-cited 59F-specific table exists - flagged as lower confidence than 50F's own entries
  for exactly that reason."
- "v2.9 ADDENDUM (same turn as the review below): the 52A '//' entry's first version, added during this same review,
  routed the entire post-'//' content verbatim to MmbId with ClrSysId/Cd left unimplemented, reasoning that TARGET2
  Annex 5.1.3's own 'ClearingSystemIdentification mapping' table did not extract as text. Re-checked before finishing
  the review: that table is embedded as an IMAGE in the source PDF (not a text-extraction failure, a genuinely
  non-text table), extracted via PyMuPDF and read directly rather than via OCR (no tesseract binary available in this
  environment). It contains a full Country/System/MT-code/ISO-code table plus a worked example
  (':52A://BL12345678' -> ClrSysId/Cd='DEBLZ', MmbId='12345678') proving the post-'//' content is NOT one opaque
  string - it's a 2-character MT clearing-system code immediately followed by the member id. Corrected both the
  MmbId entry (now strips the 2-character code first) and added the ClrSysId/Cd entry (code-list lookup, transcribed
  from the table) - see both entries' own notes for the full table and the one deliberately-unresolved case
  (Switzerland's 'SW' code is shared by two different systems, genuinely ambiguous from the MT side alone)."
- "v2.9 CHANGELOG NOTE - EXTERNAL SOURCE REVIEW: this document was reviewed against two additional sources at the
  user's request: (1) TARGET2 'General Functional Specification of the MX/ISO 20022 migration' (Eurosystem, 2013/2014)
  - ANNEX 5.1 ONLY (MT103-to-pacs.008 core mapping tables), explicitly scoped to that section since the rest of that
  document is TARGET2-network-specific and this document's own scope_notes already deliberately excludes TARGET2 as
  a source of truth (see the TARGET PROFILE DECISION note); (2) JPMorgan 'Migration to ISO 20022' guide (Jan 2023,
  full document), which includes a concrete worked MT103-to-pacs.008 example with real XML output - genuinely useful
  since it is CBPR+-flavored (BizSvc=swift.cbprplus.02) and matches this document's actual target profile, unlike the
  TARGET2 source. Four sourced corrections/additions were made as a result: (a) 23E's SDVA code was mis-routed to
  CtgyPurp/Cd - TARGET2 Annex 5.1.1 shows SDVA belongs on SvcLvl/Cd, separate from INTC/CORT on CtgyPurp/Cd - split
  into two entries. (b) 53A/54A/55A used a bare direct_copy that would corrupt BICFI if a leading Party
  Identifier/account line was present - the same bug class already fixed for 56A/57A in v1.1 but never applied here;
  converted to decompose_party, and additionally now route the extracted account line to GrpHdr/SttlmInf/SttlmAcct
  per TARGET2 Annex 5.1.3 (previously dropped entirely). (c) Field 72 previously mapped ONLY the /INS/ codeword;
  JPMorgan's worked example shows /ACC/ content -> InstrForCdtrAgt/InstrInf and remaining codeword content ->
  InstrForNxtAgt/InstrInf - both added. (d) 52A had the same direct_copy bug as (b), now fixed the same way; also
  added a new entry for a '//'-prefixed clearing-system line -> ClrSysMmbId/MmbId (TARGET2 Annex 5.1.3), and 50F
  gained real Name/AddressLine/Country/TownName decomposition for its first three numbered lines (TARGET2 Annex 5.1.2,
  corroborated structurally by JPMorgan's worked 50F example) - numbered lines 4-8 (birth date, private-id scheme
  codes) were deliberately NOT implemented since that part of the source table did not extract cleanly from the PDF
  and no corroborating worked example exists; flagged as a residual gap rather than guessed at. One item was reviewed
  and deliberately NOT changed: 71F/71G's charge-Agent fallback (Sender/Receiver BIC) is now known to diverge from
  both sources (TARGET2 says 'deduce from chain' with an 'As per payment chain' text fallback; JPMorgan's own worked
  example wrote 'NOTPROVIDED' even though Sender/Receiver BICs were determinable) - left as-is pending a deliberate
  decision rather than silently overridden, since reasonable positions differ and the existing citation (SWIFT's own
  field 71F definition) is not wrong, just possibly not how real production systems behave."
- "v2.8 CHANGELOG NOTE: resolved the M/O-status placeholder (see scope_notes' M/O STATUS RESOLUTION for the full
  reasoning). Set mandatory: true on 20 (both entries already partially true/false, now consistent), 23B, all three 32A
  entries, and 71A - these are unconditionally mandatory in every real MT103 per two independent public field-spec sources,
  and each entry's own edge_cases already said 'reject' on absence while its mandatory: flag contradicted that. Did NOT set
  mandatory: true on the 50A/50F/50K or 59/59A/59F entries individually - would incorrectly reject a valid message using
  only one option letter, since mandatory: is a per-entry flag with no 'at least one of' semantics. Instead, fixed the
  ENGINE WIRING of VR005 (source_alternative_group_required), which already existed and already covered exactly this
  6-field/2-group requirement: it was only ever evaluated after a full conversion attempt, so a message failing it burned
  up to maxConverterRetries+2 pointless converter re-runs (retrying a source message that cannot change) before finally
  throwing. Added ValidatorService.checkMandatorySourceFields() plus a new MandatorySourceFieldMissingException, wired into
  ConversionOrchestrator.convert() immediately after parsing - a message missing 20/23B/32A/71A or every option of
  50a/59a now fails fast, before the first conversion attempt, with a dedicated exception (mapped to a 422 by
  GlobalExceptionHandler, same structured response shape as every other engine exception). The original post-conversion
  VR005 check stays in place too, as a second layer."
- "v2.7 CHANGELOG NOTE: added 13C (six entries, mirroring the already-proven MT202_TO_PACS00900108.yaml pattern -
  confirmed pacs.008.001.08.xsd has the identical SettlementDateTimeIndication1/SettlementTimeRequest2 structure),
  23E (three entries: Cd for the Instruction3Code-enumerated CHQB/HOLD/PHOB/TELB, CtgyPurp/Cd for SDVA/INTC/CORT,
  InstrInf as the free-text fallback for everything else), and 77B (three entries: DbtCdtRptgInd derived from the
  ORDERRES/BENEFRES codeword, Dtls/Ctry for the trailing 2-letter country code, Dtls/Inf as an unconditional raw-text
  fallback). 23E and 77B were reopened after directly reading the primary source already cited for this document's
  53A/54A/55A entries (the Bank of America MT103-to-pacs.008 formatting guide) rather than relying on a
  secondhand/search-engine summary of it - that guide gives real worked forward-direction examples for both fields
  (:23E:CHQB, :77B:/BENEFRES/CA), stronger evidence than this document previously required to exclude them (see the
  superseded 23E/77B entries below, kept for history). 26T was NOT added: the same primary source states verbatim,
  'The field is not processed by Bank of America' - a real bank's production implementation explicitly skipping the
  field is evidence FOR continued exclusion, not against it, even though the schema does have a structural Purp
  target for it (see the superseded 26T entry below). 51A remains excluded - the source guide does not mention it at
  all, so 'no evidence found either way' still stands unchanged."
- "v2.6 CHANGELOG NOTE: 71F/71G were reverted to 'unsupported' (hard reject) by an intervening edit, undoing this session's earlier 'skip_with_warning' decision - but on being asked to explain the underlying gap rather than just pick between reject/skip, found a real fix instead of either. SWIFT's own field definitions are specific about the charging agent: field 71F is 'charges deducted BY THE SENDER', field 71G is 'charges to be deducted BY THE RECEIVER' - so the Agt Charges7 requires is not actually undeterminable, it's the MT header Sender (for 71F) / Receiver (for 71G), both already available as __MT_SENDER_BIC__/__MT_RECEIVER_BIC__. Rebuilt as three entries each (currency via extract_pattern+direct_copy+allowed_pattern, amount via extract_pattern+decimal_comma_to_dot, agent via the existing conditional/if_any_present_field/skip_if_none_present mechanism keyed on the SAME field's own presence) - mirrors the 32A/33B composite-extraction pattern. Uses '#0'/'#1' (ChrgsInf is maxOccurs=unbounded) so a message with BOTH 71F and 71G produces two distinct occurrences rather than one overwriting the other. Removed the now-dead 'unsupported' transformation type's only two callers - the engine capability itself (TransformationEngine.unsupported/ConverterService) is left in place for any future genuinely-unfixable gap, just unused in this document for now."
- "v2.5 CHANGELOG NOTE (decisions recorded, no code/mapping change): two items had been raised repeatedly across several review passes (v2.1 through v2.3) without a final answer, re-litigating the same ground each time. User explicitly decided both this round (2026-08-31), recorded here so a future review doesn't re-open them without new information: (1) EndToEndId KEEPS the existing UHB p.209-cited NOTPROVIDED default (from /ROC/ in field 70 when present, else NOTPROVIDED) - the field-20-reuse alternative proposed in several reviews is explicitly NOT adopted; do not silently switch this again without a fresh explicit decision, since it directly overrides a cited source. (2) The Envelope/AppHdr (head.001.001.01) wrapper stays OUT OF SCOPE for now, consistent with the original v1.0 scope_notes exclusion - this converter continues to emit a bare pacs.008 Document payload, not a full SWIFT-transmittable envelope. If this is needed later, the open question is which network's BizSvc identifier applies (this document targets CBPR+ per the v2.1 profile decision, not TARGET2)."
- "v2.4 CHANGELOG NOTE (ENGINE FIX): fixed VR008 firing unconditionally - its own description explicitly scopes it to 'if converted after 14 November 2026', but the v2.2 implementation had no date-gating at all, so it incorrectly hard-failed conversions run today (2026-08-31), months before its stated effective date. Added evalStructuredAddress() date-gating: params.effective_date (ISO yyyy-MM-dd) - the rule doesn't apply at all (not even a warning) until the current date reaches it. VR008's own params now sets effective_date: '2026-11-14', matching its description. A malformed/unparseable effective_date fails safe (rule treated as not yet active, not as a crash or an unconditional enforcement)."
- "v2.3 CHANGELOG NOTE (real-message comparison, 2026-08-31): four changes, one pushback-with-evidence, three items left open pending user decision. FIXED: (1) MtParserService.normalizeToBicCore() was truncating a 12-char SWIFT LT address to bare BIC8, discarding the branch code entirely ('APACGB61AXXX' -> wrongly 'APACGB61' instead of correctly 'APACGB61XXX') - fixed to drop only the single LT-identifier character at position 9, keeping BIC8+branch, per standard SWIFT LT-address format (BIC8 + 1-char terminal id + 3-char branch). (2) Added InstgAgt/InstdAgt (CdtTrfTxInf level), sourced from the MT header Sender/Receiver via the existing __MT_SENDER_BIC__/__MT_RECEIVER_BIC__ synthetic fields - closes a gap flagged two rounds earlier that the user had deferred; distinct from DbtrAgt/CdtrAgt (this-hop agents vs whole-chain agents). (3) GrpHdr/MsgId switched from a generated UUID to reusing field 20 (Sender's Reference) - a common real-world convention, not in conflict with any existing UHB citation (unlike EndToEndId, see below) - tradeoff (field-20 uniqueness is only sender-local, not global like a UUID) stated explicitly in that entry's notes. PUSHBACK, not applied: a reviewer's 'corrected' example omitted GrpHdr/SttlmInf/SttlmMtd entirely, calling it un-derivable and un-sourced - but SttlmInf has no minOccurs=0 in GroupHeader93 (re-verified directly against the supplied XSD, line 553) and is therefore genuinely MANDATORY; omitting it would fail XSD validation outright, not fix anything. The existing GENERATED:SttlmMtd entry's own extensive documented caveat (INDA-when-absent is an assumption, not a sourced fact) already covers the legitimate part of this concern. LEFT OPEN (conflicts with existing sourced material or a prior explicit user decision - not silently changed either way): EndToEndId reuse of field 20 was proposed, but this document's EndToEndId entry already has a specific UHB p.209 citation for a 'NOTPROVIDED' literal default when no /ROC/ reference exists in field 70 - a source conflict, not resolved here. PmtTpInf/LclInstrm/Prtry (23B) removal was proposed again - the user already explicitly confirmed 'leave as-is' when this exact question was raised previously; not re-litigated without the user revisiting that decision. The Envelope/AppHdr (head.001.001.01) wrapper remains unbuilt - asked about previously and the user's answer was dismissed without a response that round."
- "v2.2 CHANGELOG NOTE (validation overhaul, 2026-08-31): three engine-level additions, requested to move validation closer to real SWIFT-standard checking rather than just XSD presence. (1) PROACTIVE SCHEMA COMPLETENESS CHECK: XsdOrderingIndex now also indexes each complexType's mandatory children (sequence minOccurs, xs:choice treated as 'at least one branch', not 'all branches mandatory' - avoids false positives like CashAccount38/Id/IBAN when only Othr is used) and mandatory attributes (simpleContent/extension/attribute use=required). ValidatorService walks this top-down BEFORE the reactive SAX XSD pass, only descending into containers already populated - catches the exact class of bug this engine kept hitting one field at a time over many rounds (missing GrpHdr, Dbtr, Ccy attributes, ...) with a clear field-path message instead of SAX's sometimes-cryptic wording. Shared the XSD-loading/caching logic between MxRenderer and ValidatorService via a new XsdIndexRegistry rather than duplicating it. Also fixed a pre-existing retry-classification gap while touching this: SAX XSD errors and the new completeness-check errors are DETERMINISTIC (retrying the converter can never fix a missing schema element), but neither message ever matched the MAPPING_GAP substring check, so both were silently burning max-converter-retries attempts before eventually failing anyway - now classified MAPPING_GAP immediately. (2) REAL validation_rules ENGINE: added rule_type + params to ValidationRule (generic map, not one Java field per rule shape) and six evaluators in ValidatorService (conditional_currency_mismatch_requires, presence_requires_value, mutual_exclusion, source_alternative_group_required, currency_precision_check using standard ISO 4217 minor-unit exceptions, structured_address_required) - VR001/VR003/VR004/VR005/VR007/VR008 are now actually machine-checked instead of descriptive-only text 'recorded for reviewer judgement'; VR002 and VR006 stay descriptive (see their own entries for why - VR002 because ChrgsInf has no real support to check against, VR006 because it's already naturally enforced by the target_defaults removal + the new completeness check together, not a distinct rule shape). (3) LLM AUDIT (Layer 2) ROBUSTNESS: semanticAudit() is now wrapped so a transient LLM API failure degrades to a warning (deterministic checks already passed by the time Layer 2 runs) instead of failing an otherwise-valid conversion outright - new ValidationReport.llmAuditStatus (ran/error/skipped_not_reached) lets a caller tell 'clean because both layers passed' apart from 'clean because Layer 2 never actually ran'. Added a sanity cross-check on the audit's own is_valid vs findings fields, a lightweight hallucination guard flagging findings that reference a field not found anywhere in this mapping doc, symmetric retry-on-malformed-JSON for the Groq path (previously only Gemini had it), and trimmed the audit prompt to each field's first sentence of notes instead of the full multi-paragraph changelog history now embedded in most entries - was inflating token cost/latency and diluting the model's attention for no benefit to the actual audit."
- "v2.1 CHANGELOG NOTE (independent verification pass, 2026-08-31): five fixes from an external review of v2.0. (1) CRITICAL
  CONTRADICTION FIX: the __MT_RECEIVER_BIC__ (CdtrAgt fallback) entry previously claimed 'User confirmed... this deployment
  targets TARGET2 specifically' and cited scope_notes for that declaration - but scope_notes explicitly states the opposite
  target-profile decision (CBPR+, not TARGET2). This was a direct, uncorrected self-contradiction left over from the entry's
  v1.9 drafting. The false TARGET2 citation is removed; the fallback behavior itself is retained but re-grounded in the
  correct, verified, universal Swift MT103 field 57a usage rule ('When field 57a is not present, it means that the Receiver
  is also the account with institution') - a source-side rule independent of target network. The parallel 52a/DbtrAgt
  fallback (__MT_SENDER_BIC__) is likewise re-grounded in the equivalent verified 52a usage rule ('Specifies the Financial
  Institution of the Ordering Customer, when different from the Sender'). (2) DUPLICATE MAPPING FIX: removed the standalone
  account-only llm_assisted entries for 50K and bare-59, which independently re-parsed the same raw source value as the
  v1.16 name/address decompose_party entries' account_sub_element and wrote to the identical target path - a genuine
  duplicate write via two different mechanisms (LLM vs regex), not merely 'redundant-but-harmless' as v2.0 characterized it.
  The decompose_party entries alone now own both the account and name/address extraction for 50K and bare 59, with the
  malformed-empty-account-line edge case carried over into them so no coverage was lost. (3) BUG FIX: 52D and 57D declared
  account_sub_element: 'Id' but had no corresponding sub_element_targets entry, so an extracted Party Identifier line had no
  destination - contradicting both entries' own notes that it 'must not be' dropped/discarded. Added Id -> FinInstnId/Othr/Id
  targets (the same safe generic-identifier container already used elsewhere in this document for account portions), rather
  than guessing a ClrSysMmbId scheme code. (4) INCONSISTENCY FIX: added 54A to the GENERATED:SttlmMtd check_fields list -
  it was missing while its option-A counterparts 53A and 55A were both present, and 54A is mapped elsewhere in this document
  as an ordinary field, so a message with only 54A present was previously misclassified as INDA. (5) SCOPE ADDITION: added an
  OPERATIONAL SCOPE paragraph to scope_notes noting that Swift's CBPR+ MT/MX coexistence period for cross-border payments
  ended November 2025 and live cross-border traffic is now MX-native, so this converter's actual use case (archival,
  domestic/non-Swift MT103, reconciliation, or test tooling) should be confirmed before production use. No other field
  mappings, transformations, or fail-closed decisions from v2.0 were changed."
- "v1.18 CHANGELOG NOTE (ENGINE FIX + mapping-doc fix): fixed 'cvc-complex-type.2.3: Element ChrgsInf cannot have character
  [children], because the type's content type is element-only.' Root cause: ChrgsInf is Charges7, a structured complex type
  requiring BOTH Amt (currency+amount, itself needing a Ccy attribute) and Agt (agent BICFI) - not a plain value. The 71F/71G
  entries used direct_copy, writing the raw MT value as TEXT CONTENT straight into ChrgsInf, which Charges7's element-only
  content model forbids entirely, on top of missing Agt (this document's own pre-existing notes already said 'Agent BIC undeterminable
  - source material doesn't say where to get it (gap)' and 'reject - no sourced default' in edge_cases, but the actual entry
  never implemented that rejection - it just wrote invalid text instead). Added a new 'unsupported' transformation type (TransformationEngine/ConverterService)
  that raises a clear, specific error only when the field actually has a value - for entries a mapping doc's own notes already
  document as genuinely unmappable in their current form, rather than leaving them to silently write structurally-invalid
  output. Proactively scanned every other target_path in this document for the same 'leaf value into a structured complex
  type' shape (only decompose_party entries, already fixed in v1.9-v1.16, and llm_assisted/direct_copy entries checked against
  the XSD's complexType definitions) - no other instances found."
- "v1.16 CHANGELOG NOTE: two changes from a business-correctness review (2026-08-31). (1) BUG FIX: found and fixed a pre-existing,
  systemic bug (present before this session, not introduced by it) in 7 decompose_party entries - 50A-BIC, 59A-BIC, the three
  72 /INS/ entries, and both 70 entries - which set target_path to the exact intended destination while ConverterService always
  appends the sub_elements key on top unless overridden, producing invalid double-suffixed paths (e.g. 'CdtTrfTxInf.PmtId.EndToEndId.EndToEndId').
  This meant EndToEndId was NEVER populated by its /ROC/ entry even when the prefix was present - every conversion silently
  fell through to the v1.9 NOTPROVIDED target_default instead - and Dbtr/Cdtr's BIC-portion entries (50A/59A) never worked
  either. Fixed by adding sub_element_targets overrides to all 7, routing explicitly to target_path. (2) GAP FIX: added real
  Dbtr/Cdtr Nm/PstlAdr population for 50K and bare-59 (the plain-unstructured-line shape) - first line -> Nm, remaining line(s)
  -> repeated AdrLine elements (new lines_from: decomposition rule + '#N' repeated-element convention in ConverterService/MxRenderer/XsdOrderingIndex,
  generically reusable for any other repeated ISO 20022 element, e.g. ChrgsInf, RgltryRptg). Per explicit user instruction:
  does NOT decompose address text into TwnNm/Ctry/PstCd - not discretely present in the MT103, and CBPR+'s move away from
  unstructured postal addresses (Nov 2026) makes guessing that structure inappropriate. 50F/59F (numbered-line format) remain
  unresolved - see that known_limitations entry."
- "v1.15 CHANGELOG NOTE (ENGINE FIX, not a mapping-doc change): fixed 'cvc-pattern-valid: Value BANKBEBBAXXX is not facet-valid...for
  type BICFIDec2014Identifier' surfaced via the __MT_SENDER_BIC__/__MT_RECEIVER_BIC__ fallback entries (v1.9/v1.10). Root
  cause: MtParserService's manual Block1/Block2 truncation-to-8-chars logic only ran when Prowide's getSender()/getReceiver()
  returned null/blank - but empirically those methods returned the raw 12-character logical-terminal address (BIC8 + 1-char
  terminal + 3-char branch) directly at least once, which is NOT a valid BICFIDec2014Identifier (8 or 11 chars only) and was
  passed through unmodified since the null/blank check never triggered the truncation. Fixed with a shared normalizeToBicCore()
  helper applied to the result regardless of which source (Prowide convenience method or manual block parsing) produced it
  - truncates a 12+ char value to its 8-char BIC core, leaves 8/11-char values untouched, and leaves any other length alone
  for XSD validation to reject rather than guessing at a fix. Confirms both getSender() and getReceiver() are real, callable
  methods on this Prowide version (this error could only occur after they successfully returned a value and were fed into
  the pipeline)."
- "v1.14 CHANGELOG NOTE (ENGINE FIX, not a mapping-doc change): fixed 'cvc-minLength-valid: Value '' with length 0 is not
  facet-valid with respect to minLength 1 for type Max34Text'. Root cause was in ConverterService, not this document: the
  six account-portion llm_assisted entries (50A/50F/50K/59A/59/59F) each document 'no leading / line -> output nothing, this
  is normal, not an error', but ConverterService's llm_assisted case unconditionally wrote whatever the LLM returned into
  the tree - including a blank/empty result meant to signal 'no value' - creating an empty Othr/Id element that then failed
  XSD minLength validation. Fixed to skip writing to the tree entirely when the llm_assisted result is null/blank, matching
  how decompose_party already treats an unmatched optional sub-element. This was a latent bug in every llm_assisted entry
  across any mapping doc, not specific to these six - simply hadn't been triggered by a 'legitimately no account line present'
  test case until now."
- "v1.13 CHANGELOG NOTE: fixed 'cvc-complex-type.2.4.b: The content of element Id is not complete. One of [IBAN, Othr] is
  expected.' Root cause: CashAccount38/Id is AccountIdentification4Choice - a CHOICE between IBAN and Othr (GenericAccountIdentification1,
  itself with its own Id/SchmeNm/Issr), never a plain text value directly under Id. All six account-portion entries (50A,
  50F, 50K account entries -> DbtrAcct.Id; 59A, 59, 59F account entries -> CdtrAcct.Id) targeted '...Acct.Id' directly, which
  is structurally invalid regardless of what the extracted value looks like. Retargeted all six to '...Acct.Id.Othr.Id' -
  the generic identifier container, which accepts any identifier string (Max34Text) without requiring IBAN-format detection.
  Deliberately NOT routed to IBAN even when a value happens to look like one: doing so correctly would need value-pattern
  detection (a 'conditional-on-value' capability this engine doesn't have, distinct from the presence-based 'conditional'
  transformation added earlier), and guessing IBAN vs Othr from shape alone risks miscategorizing a value the schema would
  still accept under Othr - Othr/Id is a safe, universally-valid choice branch for any identifier, so no guess is needed here.
  Same class of oversight as the DbtrAgt/CdtrAgt Othr/Id routing already used correctly for the 52D/57D entries (v1.9) - this
  fix brings the six account entries in line with that existing precedent."
- "v1.12 CHANGELOG NOTE: fixed 'cvc-complex-type.2.4.a: Invalid content...DbtrAcct...one of [...Dbtr] is expected'. Root cause:
  Dbtr/Cdtr are mandatory pacs.008 elements, but this document only ever populates them via the 50A/59A BIC entries - for
  50F/50K/bare-50/bare-59/59F input (no BIC), Dbtr/Cdtr were never created AT ALL, while DbtrAcct/CdtrAcct (populated separately
  by those same field options) still were - producing DbtrAcct with no preceding Dbtr sibling, an XSD sequence violation.
  Fixed with new target_defaults entries (empty CdtTrfTxInf.Dbtr/Cdtr, applied only when nothing under that path was populated
  by any other entry) - see target_defaults section. Required a safety fix to ConverterService's target_defaults application:
  it previously checked only the EXACT target_path key, which would have let a container-placeholder default clobber a Dbtr/Cdtr
  that 50A/59A DID populate (MxRenderer's setTextContent on the container element would wipe out children already appended)
  - now checks the whole subtree before applying a default."
- "v1.11 CHANGELOG NOTE: fixed 'cvc-complex-type.4: Attribute Ccy must appear on element IntrBkSttlmAmt'. Root cause was two-fold:
  (1) MxRenderer had NO mechanism to emit XML attributes at all - only child elements/text - even though every ISO 20022 currency-and-amount
  type (ActiveCurrencyAndAmount for IntrBkSttlmAmt, ActiveOrHistoricCurrencyAndAmount for InstdAmt) has a MANDATORY Ccy attribute,
  not a child element; (2) the 32A/33B amount entries used a single llm_assisted call targeting the amount element directly,
  which can only ever produce ONE value at ONE target_path - structurally incapable of also populating an attribute. Fixed
  generically: MxRenderer now supports a 'Foo.Bar.@AttrName' target_path convention (sets an XML attribute instead of a child
  element), and the 32A/33B amount entries were each split into two deterministic entries (currency via extract_pattern+direct_copy+allowed_pattern,
  amount via extract_pattern+decimal_comma_to_dot) instead of one combined llm_assisted call - removing an LLM dependency
  for what was already documented as purely deterministic fixed-width extraction, consistent with this engine's own stated
  preference for exact operations over LLM calls. SEPARATELY, also fixed field 36 (XchgRate): its notes incorrectly claimed
  no comma-to-dot transformation type existed and used direct_copy, silently passing a comma-decimal value through unconverted
  into a plain xs:decimal target - decimal_comma_to_dot already existed and is used elsewhere in this document; this would
  have failed XSD validation the first time a message carried field 36, not yet triggered only because no test message had
  exercised it."
- "v1.9 CHANGELOG NOTE: added DbtrAgt (52A/52D, with an explicit Sender-BIC-from-MT-header fallback per user instruction 2026-08-28)
  and CdtrAgt option D (57D) alongside the existing 57A. Required a new engine capability: 'conditional' transformation entries
  can now reference another field's raw value per branch (if_any_present_field/if_none_present_field) and can explicitly skip
  a branch (skip_if_any_present/skip_if_none_present) when a different entry already covers it - see TransformationEngine.conditional().
  Also required MtParserService to read the MT header's Block 1 (Sender BIC) for the first time - previously only Block 4
  was read at all, same class of gap already flagged for Block 3/UETR (field 121). CdtrAgt deliberately has NO fallback when
  57A/57D are both absent, per explicit user instruction not to invent one from the header - that case now fails conversion
  with a clear, documented error rather than guessing or emitting invalid XSD. See the updated 52A/52D/57A/57D entries and
  the CdtrAgtRequiredCheck entry for the exact rules. SEPARATELY, also wired in the new top-level 'target_defaults' mechanism
  (target_path -> value, applied only if still absent after all field_mappings entries ran) for CdtTrfTxInf/PmtId/EndToEndId
  - UHB p.209's 'NOTPROVIDED' default was already cited in this document's notes but never actually applied, so any message
  where field 70 lacked a /ROC/ prefix silently produced an invalid (missing) mandatory EndToEndId. ALSO FOUND while writing
  the 52D/57D entries above: DecompositionRule.stripAccountLinePrefix/accountLinePattern/accountSubElement were extensively
  documented (see that class's own Javadoc) but DecompositionService never actually read them - dead code, never exercised
  by any pre-v1.9 entry (all of which work around the same shape with a single whole-value regex instead, e.g. the 50A/59A
  BIC entries). Implemented for real in DecompositionService rather than working around it a third time, since it is the mechanism
  the schema's own documentation describes as the correct one for this exact 'optional /account line, then name/address lines'
  shape."
- "v1.7 CHANGELOG NOTE: v1.4's fix was documentation-only (added prose to decompose_party's pattern_description/edge_cases
  explaining that a non-match against an optional account-line sub-element should not raise). It did not work - the identical
  error class recurred against the same test value ('JOHN DOE 123 MAIN STREET', field 50K) with near-identical wording, confirming
  the engine does not read that prose and mechanically applies decompose_party's fallback_if_unparseable, which this schema
  itself documents as hard-locked to raise_error with 'no other fallback implemented.' No prose fix could have worked - the
  transformation type itself has no mechanism for optional sub-elements. STRUCTURAL FIX: moved the six account-only entries
  (50A-account, 50F, 50K, 59A-account, 59, 59F) from decompose_party to llm_assisted, which has no fallback_if_unparseable
  field to trigger. Each entry's decision procedure explicitly instructs 'output nothing if no account line is present, this
  is not an error.' The 59A-BIC and 50A-BIC entries (which extract a BIC, not an account) were left on decompose_party since
  their regex already contains an optional group designed to match with or without an account line present - but this is flagged
  as unverified against the actual engine, not confirmed safe, in case the same failure mode applies to them too."
- "v1.6 CHANGELOG NOTE: v1.5's fix added a new top-level field 'character_set_scope', which failed schema validation - the
  consuming class (com.wiredesk.mtmx.mapping.model.MappingDocument) is strict (fails on unrecognized properties) and only
  accepts the 12 fields already in this schema. Moved the same clarification into scope_notes instead, which is free text
  and already a valid field. No new top-level fields exist in this document as of v1.6 - only the original 12."
- "v1.5 CHANGELOG NOTE: a real conversion run (sample MT103 with 20/23B/32A/50K/59/71A) correctly produced '<IntrBkSttlmAmt
  Ccy=\"USD\">1234.56</IntrBkSttlmAmt>' from source '32A:240115USD1234,56' (comma-to-dot conversion worked as designed), but
  validation then rejected that OUTPUT for containing '\", <, =, >' - characters outside SWIFT-X. This is a validator scope
  bug, not a mapping error: SWIFT-X governs the MT103 source side only; it was never a valid constraint for serialized XML,
  which structurally requires those characters. See the CHARACTER_SET SCOPE note in scope_notes (moved there in v1.6 after
  the original fix failed schema validation). No field_mappings entries were changed - the 32A transformation that produced
  the flagged output was correct."
- "v1.4 CHANGELOG NOTE: a production run against a real 50K value ('JOHN DOE 123 MAIN STREET', no account line) incorrectly
  raised fallback_if_unparseable. Root cause: the account-line entries (50A-account, 50F, 50K, 59A-account, 59, 59F) all use
  an optional sub-element (AccountId, matched only when a leading '/' line is present) but fallback_if_unparseable was apparently
  triggering on any regex non-match, not just genuine malformation. Since the account line is optional in every one of these
  MT103 field options, plain name/address content with no leading slash is the COMMON case, not an edge case - tightened all
  six entries' pattern_description and edge_cases to explicitly state that a non-match means 'no account present' and must
  not raise. This is a correction to documentation clarity for an existing rule, not a new source citation or a change in
  what's actually mapped."
- "SUPERSEDED (v2.8 - see the M/O STATUS RESOLUTION note in scope_notes, and the v2.8 CHANGELOG NOTE below) - M/O status: no
  MT103 FIN spec attached, so no field's mandatory flag can be certified - all set to false with a note, including 20/32A/71A
  which are almost certainly mandatory in reality. [Kept for history only - 20/23B/32A/71A now have mandatory: true; 50a/59a
  are enforced as alternative groups via VR005 instead, not via individual per-entry flags - see scope_notes for why.]"
- "SUPERSEDED (v2.7 - see the v2.7 CHANGELOG NOTE above) - 23E Instruction Code: unresolved. NOT MAPPED in the
  MT103-to-pacs.004 and MT104-to-pacs.003 tables. The MT101-to-pacs.008 table (different message type) does NOT flag
  it NOT MAPPED and places it near Category Purpose/Instruction For Creditor Agent, but that's not a valid MT103
  citation. No field_mappings entry. [Kept for history only - three field_mappings entries now exist for 23E.]"
- "SUPERSEDED (v2.26 - see the direct_copy field_mappings entry now placed after the 77B entries) - 26T Transaction
  Type Code: STILL excluded as of v2.7, but the reasoning was upgraded from 'no evidence' to a confirmed direct
  citation - see the v2.7 CHANGELOG NOTE above. Original v1.x reasoning (kept for history): no evidence for
  MT103-to-pacs.008 in any attached document. NOT MAPPED in MT104-to-pacs.003 (different pair); absent from the
  MT101-to-pacs.008 table entirely. No field_mappings entry. [v2.21: 'No field_mappings entry' was itself the live
  bug found by TC45 - a message containing 26T was hard-rejected outright as an unmapped field. Fixed with
  skip_with_warning, value still not mapped, citing the Bank of America guide's 'not processed' caution. v2.26: on
  re-review, that caution was one bank's own implementation choice, not evidence the field is unmappable or that
  Purp is the wrong target - 26T's own DEFINITION in the real SWIFT MT103 MRG ('nature of, purpose of, and/or
  reason for the transaction... salaries, pensions, dividends') is definitionally ISO 20022's Purpose concept. Now a
  real value-producing entry, targeting Purp/Prtry (not Purp/Cd, since 26T's EUROSTAT codes are not ISO 20022's own
  external code list) - see that entry's own v2.26 note for the full citation.]"
- "SUPERSEDED (v2.24 - see the skip_with_warning field_mappings entry now placed before the 52A entries) - 51A
  Sending Institution: no evidence found either way in any attached document. Re-checked in v2.7 against the
  Bank of America MT103-to-pacs.008 guide specifically - still not mentioned there either. Still excluded.
  [v2.24: real sourcing found at last, directly in the SWIFT MT103 MRG itself (not a guide's silence): 51A is
  Network-Validated-Rule-restricted to FileAct transmission (D63) and is definitionally redundant with the MT
  header Sender BIC this document already captures as InstgAgt. 'No field_mappings entry' was the actual live bug
  (TC18) - a message carrying 51A was hard-rejected outright, not merely left unmapped. A skip_with_warning entry
  now exists so presence is recognized and surfaced without blocking the conversion; no separate value target was
  added, per the redundancy finding.]"
- "SUPERSEDED (v2.7 - see the v2.7 CHANGELOG NOTE above) - 77B Regulatory Reporting: no UHB citation, but real sample
  evidence exists - comparing pacs.008 samples TRAK (has Cdtr/CtryOfRes=US) vs TROK (doesn't), only TRAK's translated
  MT contains ':77B:/BENEFRES/US'. Genuine same-pair evidence, but reverse-direction, and only the Creditor-side
  (/BENEFRES/) half was observed - the Debtor-side (/ORDERRES/) equivalent was inferred by symmetry only, and the
  full RgltryRptg structure beyond the codeword was unevidenced. [Kept for history only - three field_mappings
  entries now exist for 77B, grounded in a stronger forward-direction source.]"
- "Decimal reformatting (MT comma -> XML dot): CORRECTED in v1.11 - this note (and the 36/XchgRate entry that acted on it)
  previously claimed no matching transformation type existed in this schema's enum. That was wrong: decimal_comma_to_dot has
  existed in TransformationEngine all along. All amount-bearing entries (32A, 33B, 36) now use it via extract_pattern + decimal_comma_to_dot
  rather than direct_copy or llm_assisted."
- "pacs.008 STP variant, AppHdr/head.001 fields: out of scope - see scope_notes."
- "ClrSysRef and TxId (PmtId): confirmed no MT103 source. Evidence: engine diagnostic logs in inflow_translation_sample_messages_2025Q3
  explicitly show both being dropped as unmapped input. No field_mappings entry (nothing to map FROM in this direction)."
- "EndToEndId default 'NOTPROVIDED': UHB p.209 states this literal default when the Debtor supplies none, confirmed in real
  samples. RESOLVED in v1.9 - wired into the new target_defaults mechanism (see that section and the v1.9 changelog note)
  rather than left as an open judgment call; this was a mandatory pacs.008 element that was previously silently missing whenever
  field 70 lacked a /ROC/ prefix."
- Swift publishes the authoritative current MT103<->pacs.008 translation rules in MyStandards / the Translation Portal. 
  Public Swift pages confirm the translation scope but do not expose every field-level rule; those portal-only rules are
  not fabricated here.
- 23B -> PmtTpInf/LclInstrm/Prtry is retained as corroborated mapping, but is not marked direct Swift-portal-certified 
  because the current portal rule was not publicly retrievable in this review.
- 50F/59F numbered customer data requires a dedicated deterministic structured-address parser. Account extraction alone 
  is not a complete business mapping.
- 71F/71G require a structured ChargesInformation builder because the pacs.008.001.08 XSD requires both Amount and 
  Agent. Raw text-to-ChrgsInf is prohibited.
- 52B/52C, 53B/53D, 54B/54D, 55B/55D, 56C/56D, and 57B/57C are not silently guessed. The current mapping must fail 
  closed for unsupported variants until the exact CBPR+ translation rule and clearing-system mapping are implemented.
- Field 77B (ORDERRES/BENEFRES) and 26T have current CBPR+ implementation evidence, but exact target-side 
  codeword/occurrence handling should be added only after the engine supports the required repeated structured elements 
  and the current Swift translation rule is verified.
- SettlementMethod cannot be universally inferred from MT103 presence/absence alone. The current conditional rule is an 
  implementation default and must be verified against the Swift Translation Portal and corridor settlement configuration
  before production.
- "v2.1 VERIFIED CITATIONS: the official Swift MT103 Standards Category 1 field definitions state, for field 52a (Ordering
  Institution), that it 'Specifies the Financial Institution of the Ordering Customer, when different from the Sender' -
  i.e. absence of 52a means the Sender is the ordering institution; and for field 57a (Account With Institution), that
  'When field 57a is not present, it means that the Receiver is also the account with institution.' Both are universal
  Swift FIN usage rules on the MT103 source side, independent of the MX target profile (CBPR+, TARGET2, or otherwise), and
  are the corrected basis for the __MT_SENDER_BIC__ and __MT_RECEIVER_BIC__ fallback entries above - superseding the v2.0
  entries' unsourced 'user-provided rule' framing and the __MT_RECEIVER_BIC__ entry's incorrect TARGET2 citation."
```
