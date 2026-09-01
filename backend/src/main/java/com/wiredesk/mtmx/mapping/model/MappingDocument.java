package com.wiredesk.mtmx.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MappingDocument {
    private String conversionId;
    private String sourceFormat;
    private String targetFormat;
    private String version;
    private String lastUpdated;
    private String scopeNotes;

    private List<FieldMapping> fieldMappings = new ArrayList<>();
    private String unmappedFieldsPolicy = "error";
    private Map<String, String> defaultValues = new LinkedHashMap<>();

    /**
     * target_path -> default value, applied ONCE per conversion after every
     * field_mappings entry has run, only for target paths still absent
     * from the output tree at that point. Distinct from default_values
     * (which is keyed by SOURCE field and only applies when that specific
     * entry's own source field is absent): this is for a mandatory target
     * element that no single field_mappings entry reliably populates
     * across all input variants (e.g. pacs.008 EndToEndId, which several
     * different entries might or might not produce depending on the
     * input), so it needs a true "still missing after everything ran"
     * fallback rather than a per-entry one.
     */
    private Map<String, String> targetDefaults = new LinkedHashMap<>();
    private List<ValidationRule> validationRules = new ArrayList<>();
    private String characterSet = "SWIFT-X";
    private List<String> knownLimitations = new ArrayList<>();

    /**
     * Fields added for the MT202/MT202COV -> pacs.009 mapping doc, which
     * introduced a documentation-oriented schema variant alongside
     * conversion_id/source_format/target_format (still required - see
     * CompletenessAuditor). Accepted here purely so MappingDocument's
     * strict (fail-on-unknown-property) deserialization doesn't reject
     * the whole document over metadata fields the engine doesn't
     * currently act on functionally: targetRoot in particular is already
     * redundant with what XsdOrderingIndex auto-discovers from the XSD's
     * own "Document" complexType, so it's accepted as documentation, not
     * consulted at runtime.
     */
    private String targetMessage;
    private String targetRoot;
    private List<String> sourceMessages = new ArrayList<>();
    private Map<String, Object> sourcesConsulted = new LinkedHashMap<>();
    private String importantLimitation;

    public String getConversionId() {
        return conversionId;
    }

    public void setConversionId(String conversionId) {
        this.conversionId = conversionId;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getScopeNotes() {
        return scopeNotes;
    }

    public void setScopeNotes(String scopeNotes) {
        this.scopeNotes = scopeNotes;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public String getUnmappedFieldsPolicy() {
        return unmappedFieldsPolicy;
    }

    public void setUnmappedFieldsPolicy(String unmappedFieldsPolicy) {
        this.unmappedFieldsPolicy = unmappedFieldsPolicy;
    }

    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(Map<String, String> defaultValues) {
        this.defaultValues = defaultValues;
    }

    public Map<String, String> getTargetDefaults() {
        return targetDefaults;
    }

    public void setTargetDefaults(Map<String, String> targetDefaults) {
        this.targetDefaults = targetDefaults;
    }

    public List<ValidationRule> getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(List<ValidationRule> validationRules) {
        this.validationRules = validationRules;
    }

    public String getCharacterSet() {
        return characterSet;
    }

    public void setCharacterSet(String characterSet) {
        this.characterSet = characterSet;
    }

    public List<String> getKnownLimitations() {
        return knownLimitations;
    }

    public void setKnownLimitations(List<String> knownLimitations) {
        this.knownLimitations = knownLimitations;
    }

    public String getTargetMessage() {
        return targetMessage;
    }

    public void setTargetMessage(String targetMessage) {
        this.targetMessage = targetMessage;
    }

    public String getTargetRoot() {
        return targetRoot;
    }

    public void setTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
    }

    public List<String> getSourceMessages() {
        return sourceMessages;
    }

    public void setSourceMessages(List<String> sourceMessages) {
        this.sourceMessages = sourceMessages;
    }

    public Map<String, Object> getSourcesConsulted() {
        return sourcesConsulted;
    }

    public void setSourcesConsulted(Map<String, Object> sourcesConsulted) {
        this.sourcesConsulted = sourcesConsulted;
    }

    public String getImportantLimitation() {
        return importantLimitation;
    }

    public void setImportantLimitation(String importantLimitation) {
        this.importantLimitation = importantLimitation;
    }
}
