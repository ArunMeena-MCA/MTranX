package com.wiredesk.mtmx.transform;

import com.wiredesk.mtmx.exception.TransformationException;
import com.wiredesk.mtmx.mapping.model.FieldMapping;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NOTE: not executed in the sandbox this was written in (no Maven Central
 * access to resolve test dependencies there) - please run `mvn test` and
 * report anything that doesn't compile or pass.
 */
class TransformationEngineTest {

    private final TransformationEngine engine = new TransformationEngine();

    @Test
    void yymmddWindowsCorrectly() {
        FieldMapping fm = new FieldMapping();
        fm.setSourceField("32A");
        fm.setSourceDateFormat("YYMMDD");
        fm.setTargetDateFormat("YYYY-MM-DD");

        assertEquals("2024-01-15", engine.dateFormat("240115", fm));
        assertEquals("1999-12-31", engine.dateFormat("991231", fm));
    }

    @Test
    void rejectsMalformedDateRatherThanGuessing() {
        FieldMapping fm = new FieldMapping();
        fm.setSourceField("32A");
        fm.setSourceDateFormat("YYMMDD");
        fm.setTargetDateFormat("YYYY-MM-DD");

        assertThrows(TransformationException.class, () -> engine.dateFormat("24011X", fm));
        assertThrows(TransformationException.class, () -> engine.dateFormat("240230", fm)); // Feb 30 doesn't exist
    }

    @Test
    void codeListLookupRejectsUnknownCodeInsteadOfGuessing() {
        FieldMapping fm = new FieldMapping();
        fm.setSourceField("71A");
        fm.setCodeList(Map.of("BEN", "CRED", "OUR", "DEBT", "SHA", "SHAR"));

        assertEquals("SHAR", engine.codeListLookup("SHA", fm));
        assertThrows(TransformationException.class, () -> engine.codeListLookup("ZZZ", fm));
    }

    @Test
    void truncateRespectsMaxLength() {
        FieldMapping fm = new FieldMapping();
        fm.setMaxLength(5);
        assertEquals("HELLO", engine.truncate("HELLOWORLD", fm));
    }
}
