package com.wiredesk.mtmx.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FieldMapping {
    private String sourceField;
    private String sourceName;
    private String targetPath;
    private boolean mandatory;

    /**
     * One of: direct_copy, constant, date_format, code_list_lookup,
     * truncate, uppercase, decompose_party, llm_assisted, generated.
     * Kept as a plain String (validated by CompletenessAuditor /
     * ConverterService) rather than an enum, so an unrecognised value
     * in the YAML surfaces as a clear "unknown transformation" error
     * instead of a YAML-binding failure with a less useful message.
     */
    private String transformation;

    private Integer maxLength;
    private Integer minLength;
    private String allowedPattern;
    private Map<String, String> codeList = new LinkedHashMap<>();
    private String constantValue;

    /**
     * Only used when transformation=generated. One of: uuid, timestamp.
     * For values that must be synthesized fresh per message (e.g. a
     * message ID or creation timestamp) rather than copied from any
     * source field or held as one fixed value across all conversions -
     * that's what 'constant' is for.
     */
    private String generator;

    /**
     * Only used when transformation=conditional. Derives a value from
     * whether OTHER source fields are present, not from this entry's own
     * source_field's value. See ConditionalRule for when this applies.
     */
    private ConditionalRule conditional;
    private String sourceDateFormat;
    private String targetDateFormat;
    private String extractPattern;
    private DecompositionRule decomposition;

    /**
     * When true, a repeated occurrence of this source field's MT tag
     * (newline-joined into one raw value by ParsedMessage.addField - e.g.
     * field 71F appearing 2+ times in one message) is treated as N
     * independent occurrences instead of one malformed multi-line value.
     * Only meaningful for the simple value-in/value-out transformation
     * types (direct_copy, decimal_comma_to_dot, code_list_lookup,
     * truncate, uppercase, date_format) and for "conditional" - see
     * ConverterService.applyRepeatedSimpleTransformation/
     * applyRepeatedConditional. target_path's literal "#0" occurrence
     * marker is resolved to "#0", "#1", ... per line; line 0 always
     * resolves back to "#0", so a single, non-repeated occurrence of the
     * field produces byte-identical output to repeat_lines being unset.
     */
    private boolean repeatLines = false;

    /**
     * When set, this ENTIRE entry (any transformation type) is skipped
     * unconditionally - treated as "no value produced", not an error, not
     * even a trace row - unless the raw source value matches this regex
     * somewhere. Checked immediately after this entry's raw value is
     * fetched, before repeat_lines/extract_pattern/the transformation
     * switch, so it applies uniformly regardless of transformation type.
     * BUG FIX (2026-09-04, from live test cases TC41-44): fields like 13C
     * and 23E share one source_field across several entries, each keyed to
     * a different codeword (SNDTIME/RNCTIME/CLSTIME/... or SDVA/INTC/
     * CORT/...) - every entry's own edge_cases already documents "codeword
     * absent -> not an error, this entry produces no value," but nothing
     * previously enforced that deterministically: an llm_assisted entry's
     * own LLM call ran unconditionally and a low-confidence refusal (the
     * model cannot reliably distinguish "genuinely uncertain" from "my own
     * codeword just isn't in this text") threw an uncaught
     * TransformationException that failed the WHOLE conversion, and even a
     * deterministic entry's extract_pattern (TransformationEngine.
     * extractSubstring) throws unconditionally on no match, with no
     * "optional" concept of its own. This gate makes the documented
     * "codeword absent -> skip" case a deterministic, zero-ambiguity Java
     * check up front, before either failure mode can occur, matching this
     * document's own stated preference for exact operations over LLM calls
     * wherever a single correct answer exists (see the 32A
     * currency-extraction entry's identical rationale). Originally scoped
     * to llm_assisted only (named llm_gate_pattern) but generalized to
     * cover the deterministic 13C time-conversion entries too - see
     * ConverterService's settlement_datetime_from_time_offset case.
     */
    private String gatePattern;

    /**
     * Only used when transformation=settlement_datetime_from_time_offset.
     * The ALREADY-CONVERTED tree path (not a raw source field) to read a
     * date (ISODate, YYYY-MM-DD) from, to combine with this entry's own
     * time-with-offset value into a full ISODateTime. Depends on
     * field_mappings ordering: the referenced entry must run earlier in
     * the document than this one, since convert() processes entries in
     * list order and this reads from `tree`, not from the source message.
     * See the 13C SNDTIME/RNCTIME entries' v2.21 notes for why this exists
     * and why it's scoped this narrowly (only two entries in the whole
     * document need it).
     */
    private String dateFromTargetPath;

    private List<EdgeCase> conditionalRules = new ArrayList<>();
    private List<EdgeCase> edgeCases = new ArrayList<>();
    private String notes;

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getTransformation() {
        return transformation;
    }

    public void setTransformation(String transformation) {
        this.transformation = transformation;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public String getAllowedPattern() {
        return allowedPattern;
    }

    public void setAllowedPattern(String allowedPattern) {
        this.allowedPattern = allowedPattern;
    }

    public Map<String, String> getCodeList() {
        return codeList;
    }

    public void setCodeList(Map<String, String> codeList) {
        this.codeList = codeList;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public ConditionalRule getConditional() {
        return conditional;
    }

    public void setConditional(ConditionalRule conditional) {
        this.conditional = conditional;
    }

    public String getSourceDateFormat() {
        return sourceDateFormat;
    }

    public void setSourceDateFormat(String sourceDateFormat) {
        this.sourceDateFormat = sourceDateFormat;
    }

    public String getTargetDateFormat() {
        return targetDateFormat;
    }

    public void setTargetDateFormat(String targetDateFormat) {
        this.targetDateFormat = targetDateFormat;
    }

    public String getExtractPattern() {
        return extractPattern;
    }

    public void setExtractPattern(String extractPattern) {
        this.extractPattern = extractPattern;
    }

    public DecompositionRule getDecomposition() {
        return decomposition;
    }

    public void setDecomposition(DecompositionRule decomposition) {
        this.decomposition = decomposition;
    }

    public boolean isRepeatLines() {
        return repeatLines;
    }

    public void setRepeatLines(boolean repeatLines) {
        this.repeatLines = repeatLines;
    }

    public String getGatePattern() {
        return gatePattern;
    }

    public void setGatePattern(String gatePattern) {
        this.gatePattern = gatePattern;
    }

    public String getDateFromTargetPath() {
        return dateFromTargetPath;
    }

    public void setDateFromTargetPath(String dateFromTargetPath) {
        this.dateFromTargetPath = dateFromTargetPath;
    }

    public List<EdgeCase> getConditionalRules() {
        return conditionalRules;
    }

    public void setConditionalRules(List<EdgeCase> conditionalRules) {
        this.conditionalRules = conditionalRules;
    }

    public List<EdgeCase> getEdgeCases() {
        return edgeCases;
    }

    public void setEdgeCases(List<EdgeCase> edgeCases) {
        this.edgeCases = edgeCases;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
