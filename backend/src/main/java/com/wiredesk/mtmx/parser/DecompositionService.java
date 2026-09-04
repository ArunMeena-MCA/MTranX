package com.wiredesk.mtmx.parser;

import com.wiredesk.mtmx.exception.SemanticDecompositionGapException;
import com.wiredesk.mtmx.llm.GeminiClient;
import com.wiredesk.mtmx.mapping.model.DecompositionRule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a free-text field into structured sub-elements using the mapping
 * doc's decomposition rule. Tries deterministic regex/line-index rules
 * first (fast, exact, zero hallucination risk); falls back to a tightly
 * scoped LLM call ONLY if the rule explicitly allows it. Raises
 * SemanticDecompositionGapException if no rule is provided at all, or if
 * neither the deterministic pass nor the guided LLM pass can produce a
 * confident result - the caller must NOT proceed with a guessed split.
 */
@Service
public class DecompositionService {

    private final GeminiClient llmClient;

    public DecompositionService(GeminiClient llmClient) {
        this.llmClient = llmClient;
    }

    public Map<String, String> decompose(String sourceField, String rawValue, DecompositionRule rule) {
        if (rule == null) {
            throw new SemanticDecompositionGapException(sourceField, rawValue,
                    "No decomposition rule defined in mapping doc for this field. Add a `decomposition` block "
                            + "with `sub_elements` and `fallback_if_unparseable`.");
        }

        Map<String, Object> subElementsRules = rule.getSubElements() == null ? Map.of() : rule.getSubElements();
        List<String> lines = new ArrayList<>(List.of(rawValue.split("\\r?\\n")).stream()
                .filter(l -> !l.isBlank())
                .toList());

        Map<String, String> result = new LinkedHashMap<>();

        // Strip an optional leading "/account" (or similar) line BEFORE
        // resolving line:N indices below, so "line:0" always means "the
        // first name/address line" regardless of whether an account line
        // preceded it - see DecompositionRule's own Javadoc for why this
        // has to be a separate pass rather than a per-sub-element flag.
        if (rule.isStripAccountLinePrefix() && !lines.isEmpty()) {
            Matcher accountMatch = Pattern.compile(rule.getAccountLinePattern()).matcher(lines.get(0));
            if (accountMatch.matches()) {
                result.put(rule.getAccountSubElement(),
                        accountMatch.groupCount() > 0 ? accountMatch.group(1) : accountMatch.group());
                lines.remove(0);
            }
        }

        boolean allMatched = true;

        for (Map.Entry<String, Object> entry : subElementsRules.entrySet()) {
            String name = entry.getKey();
            String ruleSpec = ruleSpec(entry.getValue());
            boolean optional = isOptional(entry.getValue());

            if (ruleSpec.startsWith("line:")) {
                int idx = Integer.parseInt(ruleSpec.substring("line:".length()));
                if (idx < lines.size()) {
                    result.put(name, lines.get(idx));
                } else if (!optional) {
                    allMatched = false;
                }
            } else if (ruleSpec.startsWith("lines_from:")) {
                // Every remaining line from idx onward, each as its OWN
                // result entry ("name#0", "name#1", ...) rather than joined
                // into one string - for schema elements that allow repeated
                // occurrences (e.g. PostalAddress24/AdrLine, maxOccurs=7).
                // ConverterService strips the "#N" suffix when resolving
                // sub_element_targets and MxRenderer strips it again when
                // choosing the actual XML tag name, so each becomes a
                // separate sibling element with the same tag.
                int idx = Integer.parseInt(ruleSpec.substring("lines_from:".length()));
                if (idx < lines.size()) {
                    for (int i = idx; i < lines.size(); i++) {
                        result.put(name + "#" + (i - idx), lines.get(i));
                    }
                } else if (!optional) {
                    allMatched = false;
                }
            } else if (ruleSpec.startsWith("regex:")) {
                Pattern pattern = Pattern.compile(ruleSpec.substring("regex:".length()), Pattern.MULTILINE);
                Matcher m = pattern.matcher(rawValue);
                if (isAllMatches(entry.getValue())) {
                    // BUG FIX (2026-09-04, from live test case TC14): a numbered MT line (e.g.
                    // field 50F/59F's "2/" Address Line) is explicitly allowed to repeat per the
                    // SWIFT field spec ("Numbers 1, 2, and 3 may be repeated. The same number
                    // must not occur more than 2 times") - but find() alone only ever returns the
                    // FIRST match and stops, silently discarding every later occurrence of the
                    // same numbered line. Opt-in via all_matches: true (same map-based convention
                    // as `optional`) rather than the new default, since NOT every repeated regex
                    // match should become multiple XML elements - e.g. a repeated "1/" (Name) or
                    // "3/" (Country+Town continuation) line is meant to be concatenated into ONE
                    // longer value, not split into two <Nm> elements (Dbtr/Nm is not repeatable
                    // in the XSD; splitting it would produce invalid XML). Only genuinely
                    // repeatable target elements (AdrLine, PostalAddress24/AdrLine maxOccurs=7)
                    // should set this flag. Reuses decompose_party's existing "#N" suffix
                    // convention (same one lines_from: already produces for 50K/bare-59's AdrLine)
                    // - ConverterService's sub_element_targets resolution already strips/re-appends
                    // it generically, so no other code needed to change for this to render
                    // correctly as multiple repeated elements.
                    int i = 0;
                    boolean any = false;
                    while (m.find()) {
                        result.put(name + "#" + i, m.groupCount() > 0 ? m.group(1) : m.group());
                        i++;
                        any = true;
                    }
                    if (!any && !optional) {
                        allMatched = false;
                    }
                } else if (matchIndex(entry.getValue()) != null) {
                    // BUG FIX (2026-09-04, from live test case TC46): field 72's three
                    // "/INS/" occurrence entries (PrvsInstgAgt1/2/3) previously all ran the
                    // SAME "/INS/(\S+)" pattern with a plain find() - which always returns
                    // the FIRST match regardless of which entry is asking - so a message
                    // with only ONE /INS/ occurrence had it duplicated into all three
                    // PrvsInstgAgt elements instead of populating only the first. This
                    // entry's own notes even flagged the gap directly: "UNVERIFIED: ...
                    // confirm TransformationEngine does occurrence-indexed matching" - it
                    // did not. match_index: N (0-based) walks find() N+1 times and keeps
                    // only the Nth occurrence, so the second/third entries correctly
                    // require a second/third /INS/ to actually be present rather than
                    // re-matching the first one.
                    int targetIdx = matchIndex(entry.getValue());
                    String matched = null;
                    for (int i = 0; i <= targetIdx && m.find(); i++) {
                        if (i == targetIdx) {
                            matched = m.groupCount() > 0 ? m.group(1) : m.group();
                        }
                    }
                    if (matched != null) {
                        result.put(name, matched);
                    } else if (!optional) {
                        allMatched = false;
                    }
                } else if (isConcat(entry.getValue())) {
                    // BUG FIX (2026-09-04, from live test case TC34): the counterpart to
                    // all_matches above, for the case that entry's own comment predicted would
                    // need handling - a repeated "1/" (Name) or "3/" (Country/Town continuation)
                    // line where the target element (Dbtr/Nm) is NOT repeatable in the XSD, so
                    // every occurrence must fold into ONE value instead of becoming multiple
                    // elements. Space-joined, matching this document's own established precedent
                    // for the identical "concatenate free text, don't reinterpret it" situation
                    // (see field 72's InstrForNxtAgt entry, whose remaining-codeword text is
                    // likewise "space-joined, NOT further interpreted" per its own JPMorgan
                    // citation) - a reasonable, disclosed default, not a directly-cited worked
                    // example for THIS specific field.
                    List<String> matches = new ArrayList<>();
                    while (m.find()) {
                        matches.add(m.groupCount() > 0 ? m.group(1) : m.group());
                    }
                    if (!matches.isEmpty()) {
                        result.put(name, String.join(" ", matches));
                    } else if (!optional) {
                        allMatched = false;
                    }
                } else if (m.find()) {
                    result.put(name, m.groupCount() > 0 ? m.group(1) : m.group());
                } else if (!optional) {
                    allMatched = false;
                }
            } else if (ruleSpec.startsWith("regex_date_yyyymmdd:")) {
                // Same idea as "regex:" (single-match, no all_matches support - a
                // date of birth is never legitimately repeated), but the captured
                // group is reformatted from YYYYMMDD to ISODate's required
                // YYYY-MM-DD form afterward. Built for MT numbered-line "Date of
                // Birth" sub-fields (e.g. field 50a's "4/") - confirmed against
                // the real SWIFT MT103 field spec: "The number followed by a
                // slash must be followed by the date of birth in the YYYYMMDD
                // format" - which doesn't match ISODate's dashed lexical form as
                // extracted verbatim. A separate rule prefix rather than a flag
                // on "regex:", since reformatting would silently corrupt every
                // OTHER regex sub_element in this document if ever misapplied.
                Pattern pattern = Pattern.compile(ruleSpec.substring("regex_date_yyyymmdd:".length()), Pattern.MULTILINE);
                Matcher m = pattern.matcher(rawValue);
                if (m.find()) {
                    String raw = m.groupCount() > 0 ? m.group(1) : m.group();
                    result.put(name, formatYyyymmddToIso(sourceField, raw));
                } else if (!optional) {
                    allMatched = false;
                }
            } else {
                allMatched = false; // unrecognised rule syntax - needs LLM or is a doc-authoring error
            }
        }

        if (allMatched && !subElementsRules.isEmpty()) {
            return result;
        }

        // Guided LLM fallback - only reached because a rule exists.
        Map<String, String> llmResult = llmClient.decompose(sourceField, rawValue, rule);
        if (llmResult != null && !llmResult.isEmpty()) {
            return llmResult;
        }

        throw new SemanticDecompositionGapException(sourceField, rawValue, "Could not confidently decompose the field.");
    }

    private String ruleSpec(Object definition) {
        if (definition instanceof String text) {
            return text;
        }
        if (definition instanceof Map<?, ?> details && details.get("rule") instanceof String text) {
            return text;
        }
        return "";
    }

    private boolean isOptional(Object definition) {
        return definition instanceof Map<?, ?> details && Boolean.TRUE.equals(details.get("optional"));
    }

    /** See the "regex:" branch's own comment above for why this is opt-in, not the new default. */
    private boolean isAllMatches(Object definition) {
        return definition instanceof Map<?, ?> details && Boolean.TRUE.equals(details.get("all_matches"));
    }

    /** See the "concat" branch's own comment above. Mutually exclusive with all_matches in practice (never both set). */
    private boolean isConcat(Object definition) {
        return definition instanceof Map<?, ?> details && Boolean.TRUE.equals(details.get("concat"));
    }

    /** See the "match_index" branch's own comment above. Returns null (not -1) when unset, distinct from a real 0. */
    private Integer matchIndex(Object definition) {
        if (definition instanceof Map<?, ?> details && details.get("match_index") instanceof Integer idx) {
            return idx;
        }
        return null;
    }

    /** See the "regex_date_yyyymmdd:" branch's own comment above. Rejects rather than guessing at a malformed date. */
    private String formatYyyymmddToIso(String sourceField, String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new SemanticDecompositionGapException(sourceField, value,
                    "Value '" + value + "' does not match YYYYMMDD or is not a valid calendar date.");
        }
    }
}
