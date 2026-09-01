package com.wiredesk.mtmx.mapping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NOTE: I could not run `mvn test` in the sandbox this was written in (no
 * network access to Maven Central to resolve spring-boot-starter-test /
 * junit-jupiter there). This class is written to compile and pass against
 * standard JUnit 5 + the CompletenessAuditor logic as designed - please run
 * `mvn test` yourself and report anything that doesn't compile or pass.
 */
class CompletenessAuditorTest {

    private final CompletenessAuditor auditor = new CompletenessAuditor();

    @Test
    void flagsMissingTopLevelKeys() {
        Map<String, Object> doc = Map.of("source_format", "MT103");
        AuditResult result = auditor.audit(doc);
        assertFalse(result.isComplete());
        assertTrue(result.getMissing().stream().anyMatch(m -> m.contains("conversion_id")));
        assertTrue(result.getMissing().stream().anyMatch(m -> m.contains("field_mappings")));
    }

    @Test
    void flagsCodeListLookupWithoutTable() {
        Map<String, Object> fieldMapping = Map.of(
                "source_field", "71A",
                "target_path", "ChrgBr",
                "mandatory", true,
                "transformation", "code_list_lookup"
        );
        Map<String, Object> doc = Map.of(
                "conversion_id", "X",
                "source_format", "MT103",
                "target_format", "pacs.008",
                "version", "1",
                "last_updated", "2026-01-01",
                "field_mappings", List.of(fieldMapping)
        );
        AuditResult result = auditor.audit(doc);
        assertFalse(result.isComplete());
        assertTrue(result.getMissing().stream().anyMatch(m -> m.contains("code_list")));
    }

    @Test
    void completeDocPassesWithOnlyWarnings() {
        Map<String, Object> fieldMapping = Map.of(
                "source_field", "20",
                "target_path", "MsgId",
                "mandatory", true,
                "transformation", "direct_copy",
                "edge_cases", List.of(Map.of("condition", "x", "handling", "y"))
        );
        Map<String, Object> doc = new java.util.HashMap<>(Map.of(
                "conversion_id", "X",
                "source_format", "CUSTOMFMT",
                "target_format", "pacs.008",
                "version", "1",
                "last_updated", "2026-01-01",
                "field_mappings", List.of(fieldMapping)
        ));
        AuditResult result = auditor.audit(doc);
        assertTrue(result.isComplete(), () -> "unexpected missing: " + result.getMissing());
        // validation_rules wasn't supplied -> should be a warning, not a hard failure.
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("validation_rules")));
    }
}
