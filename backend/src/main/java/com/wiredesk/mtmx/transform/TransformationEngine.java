package com.wiredesk.mtmx.transform;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
     */
    public String decimalCommaToDot(String value, FieldMapping fm) {
        if (!value.matches("[+-]?\\d+(,\\d*)?")) {
            throw new TransformationException("Value '" + value + "' is not a valid decimal for field "
                    + fm.getSourceField());
        }
        if (value.endsWith(",")) {
            return value.substring(0, value.length() - 1);
        }
        return value.replace(',', '.');
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
