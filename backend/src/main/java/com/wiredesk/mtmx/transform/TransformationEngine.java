package com.wiredesk.mtmx.transform;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic implementations of the simple transformation types. Date
 * parsing, code-list lookups, and truncation are exact operations with a
 * single correct answer - handing them to an LLM would add hallucination
 * risk for zero benefit. The LLM (GeminiClient) is reserved for
 * fields the mapping doc explicitly marks llm_assisted or decompose_party.
 */
@Component
public class TransformationEngine {

    public String directCopy(String value) {
        return value;
    }

    /**
     * For a field the mapping doc's own notes already document as
     * genuinely unmappable in its current form - e.g. a target element
     * that's a structured complex type (multiple mandatory children) when
     * the source only ever carries a single flat value, with no sourced
     * way to supply the rest. Raises unconditionally WHEN THIS ENTRY FIRES
     * (i.e. only when the source field has a value - absence is still
     * handled normally by the optional/mandatory check before this runs),
     * rather than silently writing a value that can only produce invalid
     * output, or silently doing nothing.
     */
    public String unsupported(FieldMapping fm) {
        throw new TransformationException(
                "Field " + fm.getSourceField() + " has no valid mapping to " + fm.getTargetPath()
                        + " in this version of the mapping doc - see this entry's notes for why. Refusing to "
                        + "produce output that would be structurally invalid.");
    }

    public String constant(FieldMapping fm) {
        if (fm.getConstantValue() == null) {
            throw new TransformationException(
                    "constant transformation for " + fm.getSourceField() + " has no constant_value defined.");
        }
        return fm.getConstantValue();
    }

    /**
     * Produces a fresh value with no source-field input at all - for
     * envelope fields the target schema mandates but that don't
     * correspond to any single MT tag (e.g. pacs.008 GrpHdr/MsgId,
     * GrpHdr/CreDtTm). Unlike 'constant', every call returns a new value.
     */
    public String generate(FieldMapping fm) {
        String generator = fm.getGenerator();
        if (generator == null) {
            throw new TransformationException(
                    "generated transformation for target " + fm.getTargetPath() + " has no generator defined.");
        }
        return switch (generator) {
            // Hyphens stripped: a standard UUID string is 36 characters,
            // which doesn't fit Max35Text (e.g. pacs.008 GrpHdr/MsgId) -
            // 32 hex characters carries the same 128 bits of uniqueness
            // and fits comfortably under that limit.
            case "uuid" -> UUID.randomUUID().toString().replace("-", "");
            // Truncated to millisecond precision: Instant.now().toString()
            // can emit up to 9 fractional-second digits (nanoseconds) on
            // this JDK, which is technically valid xs:dateTime lexical
            // form but not meaningful for a payment timestamp and can trip
            // up stricter downstream validators expecting the conventional
            // millisecond precision real ISO 20022 traffic uses.
            case "timestamp" -> Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
            default -> throw new TransformationException(
                    "Unknown generator '" + generator + "' for target " + fm.getTargetPath()
                            + ". Supported: uuid, timestamp.");
        };
    }

    /**
     * Derives a value from whether OTHER source fields are present in the
     * message - not from this entry's own source_field. Generic across any
     * conversion pair: the check_fields/if_any_present/if_none_present
     * rule lives entirely in the mapping doc, so no Java code needs to
     * know about any specific pair's envelope-derivation business rule.
     *
     * <p>Returns null to mean "produce nothing for this message" (the
     * matched branch is marked skip_if_*_present - a different entry
     * covers it), which the caller must treat as skip, not as absence of
     * a mandatory value.
     */
    public String conditional(FieldMapping fm, Map<String, String> allFields) {
        var rule = fm.getConditional();
        if (rule == null || rule.getCheckFields().isEmpty()) {
            throw new TransformationException(
                    "conditional transformation for target " + fm.getTargetPath()
                            + " has no conditional.check_fields defined.");
        }
        boolean anyPresent = rule.getCheckFields().stream().anyMatch(allFields::containsKey);

        if (anyPresent) {
            if (rule.isSkipIfAnyPresent()) {
                return null;
            }
            if (rule.getIfAnyPresent() != null) {
                return rule.getIfAnyPresent();
            }
            if (rule.getIfAnyPresentField() != null) {
                return allFields.get(rule.getIfAnyPresentField());
            }
        } else {
            if (rule.isSkipIfNonePresent()) {
                return null;
            }
            if (rule.getIfNonePresent() != null) {
                return rule.getIfNonePresent();
            }
            if (rule.getIfNonePresentField() != null) {
                return allFields.get(rule.getIfNonePresentField());
            }
        }
        throw new TransformationException(
                "conditional transformation for target " + fm.getTargetPath() + ": no outcome configured for the "
                        + (anyPresent ? "if_any_present" : "if_none_present") + " branch (check_fields="
                        + rule.getCheckFields() + "). Refusing to guess a value for this branch.");
    }

    public String codeListLookup(String value, FieldMapping fm) {
        Map<String, String> table = fm.getCodeList() == null ? Map.of() : fm.getCodeList();
        if (!table.containsKey(value)) {
            throw new TransformationException(
                    "Value '" + value + "' for field " + fm.getSourceField() + " has no entry in the mapping "
                            + "doc's code_list table. Known values: " + table.keySet()
                            + ". Refusing to guess an equivalent code.");
        }
        return table.get(value);
    }

    public String truncate(String value, FieldMapping fm) {
        Integer maxLen = fm.getMaxLength();
        if (maxLen == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    public String uppercase(String value) {
        return value.toUpperCase();
    }

    /**
     * Opt-in, narrowly-scoped case fixup for values that are known by the target
     * schema to be case-SPECIFIC (BIC -&gt; uppercase per BICFIDec2014Identifier's
     * pattern; UETR -&gt; lowercase per its UUIDv4 pattern), applied AFTER the value
     * has already been extracted/transformed but BEFORE it is written to the tree -
     * unlike TransformationEngine.uppercase (a whole transformation type on its own),
     * this is a small post-processing step any transformation can opt into via
     * FieldMapping.normalizeCase / DecompositionRule.subElementCaseNormalize.
     * Deliberately NOT applied to free-text content (names, addresses, remittance
     * info) - only to the specific BIC/UETR target paths that are wired to it in the
     * mapping doc, since SWIFT MT senders are known to be case-inconsistent for
     * these two identifier types even though the real MT character set is
     * conventionally uppercase-only. Unknown/null mode is a no-op (fail safe, not
     * fail closed - a mapping-doc typo here should not corrupt an otherwise-valid
     * value).
     */
    public String normalizeCase(String value, String mode) {
        if (value == null || mode == null) {
            return value;
        }
        return switch (mode) {
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            default -> value;
        };
    }

    public String extractSubstring(String value, FieldMapping fm) {
        Pattern pattern = Pattern.compile(fm.getExtractPattern());
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            throw new TransformationException("Value '" + value + "' does not match extract_pattern for field "
                    + fm.getSourceField());
        }
        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
    }

    private static final Pattern TIME_OFFSET = Pattern.compile("^(\\d{2})(\\d{2})([+-])(\\d{2})(\\d{2})$");

    /**
     * MT field 13C's time-indication value, after extract_pattern has
     * already pulled out just the "HHMM[+-]HHMM" portion following this
     * entry's own codeword (e.g. "0915+0100"), reformatted to the
     * ISOTime lexical form pacs.008's SttlmTmReq/CLSTm|TillTm|FrTm|RjctTm
     * elements require ("09:15:00+01:00"). Deterministic fixed-width
     * reformatting, not a judgement call - matches this document's
     * stated preference for exact operations over LLM calls (see the
     * 32A currency-extraction entry's identical rationale). Confirmed
     * against the real SWIFT MT103 field spec's own format definition
     * for 13C ("/8c/4!n1!x4!n" - a 4-digit time, a sign, a 4-digit
     * offset), so this is not a guessed shape.
     */
    public String timeOffsetFormat(String value, FieldMapping fm) {
        Matcher m = TIME_OFFSET.matcher(value);
        if (!m.matches()) {
            throw new TransformationException("Value '" + value + "' for field " + fm.getSourceField()
                    + " is not a valid HHMM[+-]HHMM time-with-offset.");
        }
        return m.group(1) + ":" + m.group(2) + ":00" + m.group(3) + m.group(4) + ":" + m.group(5);
    }

    /**
     * Companion to timeOffsetFormat above, for the two 13C entries whose
     * pacs.008 target (SttlmTmIndctn/DbtDtTm|CdtDtTm) is a full
     * ISODateTime, not a bare ISOTime - see FieldMapping.dateFromTargetPath's
     * Javadoc for why a date has to come from elsewhere in the converted
     * tree. Combines dateFromTargetPath's ALREADY-CONVERTED ISODate value
     * (YYYY-MM-DD) with this entry's own reformatted time-with-offset via a
     * literal "T", per ISO 8601's standard date-time separator.
     */
    public String settlementDateTimeFromTimeOffset(String value, FieldMapping fm, String datePart) {
        if (datePart == null) {
            throw new TransformationException("Field " + fm.getSourceField() + " requires "
                    + fm.getDateFromTargetPath() + " to already be populated (date_from_target_path), but it wasn't "
                    + "- check field_mappings ordering.");
        }
        return datePart + "T" + timeOffsetFormat(value, fm);
    }

    /**
     * SWIFT amount convention: a trailing comma with nothing after it
     * (e.g. "116,") means a whole-number amount, not malformed input -
     * very common in real MT traffic. Rendering that literally as "116."
     * is not valid XSD decimal lexical form, and appending an invented
     * "0" would add precision the source never stated - so a bare
     * trailing comma is simply dropped, leaving the integer value as-is.
     *
     * BUG FIX (2026-09-07, from live test case TC99): the comma itself was previously
     * OPTIONAL in the validation regex ("(,\d*)?"), so a value with no comma at all (e.g.
     * "1000") passed silently - but the SWIFT MT amount subfield format ("15d", decimal
     * number notation) makes the comma a MANDATORY structural separator, always present
     * even for a whole number (as this method's own doc comment above already establishes
     * for the "116," case) - TC59's own passing JPY case relies on exactly that rule. A
     * bare "1000" with no comma at all is not valid SWIFT amount notation and must be
     * rejected, not silently accepted as if it were "1000,". The comma is now mandatory in
     * the pattern; only the digits AFTER it remain optional.
     */
    public String decimalCommaToDot(String value, FieldMapping fm) {
        if (!value.matches("[+-]?\\d+,\\d*")) {
            throw new TransformationException("Value '" + value + "' is not a valid decimal for field "
                    + fm.getSourceField() + " - SWIFT amount notation requires a decimal comma, even for a whole "
                    + "number (e.g. '1000,'), not a bare integer.");
        }
        if (value.endsWith(",")) {
            return value.substring(0, value.length() - 1);
        }
        return value.replace(',', '.');
    }

    /**
     * Same ISO 4217 minor-unit exceptions ValidatorService.CURRENCY_PRECISION_CHECK already
     * uses to REJECT too many fractional digits - reused here to zero-PAD too few, so
     * "1000.5" (GBP, 2 decimals) renders as "1000.50" rather than a mathematically-identical
     * but inconsistently-formatted xs:decimal. Kept as a separate copy in this class (not a
     * shared constant) - ValidatorService and TransformationEngine are deliberately
     * independent, self-contained concerns in this document's existing architecture, and this
     * is a small, stable, well-established table.
     */
    private static final Map<String, Integer> CURRENCY_MINOR_UNITS = Map.ofEntries(
            Map.entry("JPY", 0), Map.entry("KRW", 0), Map.entry("VND", 0), Map.entry("CLP", 0),
            Map.entry("ISK", 0), Map.entry("XOF", 0), Map.entry("XAF", 0), Map.entry("XPF", 0),
            Map.entry("GNF", 0), Map.entry("RWF", 0), Map.entry("UGX", 0), Map.entry("PYG", 0),
            Map.entry("VUV", 0), Map.entry("DJF", 0), Map.entry("KMF", 0), Map.entry("BIF", 0),
            Map.entry("BHD", 3), Map.entry("KWD", 3), Map.entry("OMR", 3), Map.entry("JOD", 3),
            Map.entry("TND", 3), Map.entry("LYD", 3), Map.entry("IQD", 3)
    );

    /**
     * BUG FIX (2026-09-07, from live test case TC66): "1000.5" and "1000.50" are the same
     * xs:decimal VALUE, so this was never a correctness bug - but real-world consumers
     * downstream of this converter generally expect an amount rendered at its currency's
     * actual minor-unit count, not a source-dependent number of digits. Zero-pads the
     * fractional part out to the target currency's precision (default 2, matching
     * ValidatorService's own default for an unlisted currency) - never truncates a value that
     * already has that many digits or more; a currency with TOO MANY digits is
     * ValidatorService's VR007 currency_precision_check's job to reject, not this method's to
     * silently fix.
     */
    public String padDecimalToCurrencyPrecision(String value, String currency) {
        if (currency == null) {
            return value;
        }
        int minorUnits = CURRENCY_MINOR_UNITS.getOrDefault(currency, 2);
        int dot = value.indexOf('.');
        String intPart = dot < 0 ? value : value.substring(0, dot);
        String fracPart = dot < 0 ? "" : value.substring(dot + 1);
        if (fracPart.length() >= minorUnits) {
            return value;
        }
        // minorUnits == 0 always returns early above (fracPart.length() >= 0 is always true),
        // so reaching here means minorUnits >= 1 and a "." is always needed.
        StringBuilder padded = new StringBuilder(fracPart);
        while (padded.length() < minorUnits) {
            padded.append('0');
        }
        return intPart + "." + padded;
    }

    /**
     * BUG FIX (2026-09-08, from live test case TC129): field 72 permits up to 6 lines of 35
     * characters (210 chars total) with NO codeword at all - but the pacs.008 target this
     * document routes uncoded content to (InstrForNxtAgt/InstrInf) is Max140Text, so a
     * legitimately-long, entirely valid field 72 previously failed XSD validation outright
     * rather than converting. Confirmed directly against pacs.008.001.08.xsd:
     * InstrForNxtAgt itself is maxOccurs="unbounded" (same repeatable-container shape already
     * fixed for InstrForCdtrAgt/23E, see ConverterService's "#0" mid-path substitution
     * mechanism) - so long content is split across MULTIPLE InstrForNxtAgt occurrences instead
     * of being force-fit into one. Splits on whitespace boundaries (never mid-word) so each
     * chunk is at most maxLen characters; a single "word" longer than maxLen is placed alone in
     * its own chunk rather than silently dropped or truncated (this document's existing
     * "reject or preserve in full, never mangle" philosophy - see the field 70 Ustrd 140-char
     * edge_case's identical stance).
     */
    public List<String> chunkText(String value, int maxLen) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split(" ")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLen) {
                current.append(' ').append(word);
            } else {
                chunks.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * Only YYMMDD (SWIFT convention, 2-digit year windowed: yy&lt;80 -> 20yy,
     * else 19yy) and YYYYMMDD source formats, rendered to YYYY-MM-DD, are
     * implemented out of the box. Extend this method deliberately if you
     * need another pair rather than approximating one.
     */
    public String dateFormat(String value, FieldMapping fm) {
        String srcFmt = fm.getSourceDateFormat();
        String tgtFmt = fm.getTargetDateFormat();
        LocalDate date;

        if ("YYMMDD".equals(srcFmt)) {
            if (!value.matches("\\d{6}")) {
                throw new TransformationException(
                        "Value '" + value + "' is not 6 digits (YYMMDD) for field " + fm.getSourceField());
            }
            int yy = Integer.parseInt(value.substring(0, 2));
            int year = yy < 80 ? 2000 + yy : 1900 + yy;
            int month = Integer.parseInt(value.substring(2, 4));
            int day = Integer.parseInt(value.substring(4, 6));
            try {
                date = LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                throw new TransformationException(
                        "Value '" + value + "' is not a valid calendar date for field " + fm.getSourceField());
            }
        } else if ("YYYYMMDD".equals(srcFmt)) {
            try {
                date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (DateTimeParseException e) {
                throw new TransformationException(
                        "Value '" + value + "' does not match YYYYMMDD for field " + fm.getSourceField());
            }
        } else {
            throw new TransformationException(
                    "Unsupported source_date_format '" + srcFmt + "' for field " + fm.getSourceField()
                            + ". Extend TransformationEngine.dateFormat to add it.");
        }

        if ("YYYY-MM-DD".equals(tgtFmt)) {
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        throw new TransformationException(
                "Unsupported target_date_format '" + tgtFmt + "' for field " + fm.getSourceField()
                        + ". Extend TransformationEngine.dateFormat to add it.");
    }
}
