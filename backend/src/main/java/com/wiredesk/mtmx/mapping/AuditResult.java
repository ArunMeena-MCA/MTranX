package com.wiredesk.mtmx.mapping;

import java.util.ArrayList;
import java.util.List;

public class AuditResult {
    private final boolean complete;
    private final List<String> missing;
    private final List<String> warnings;

    public AuditResult(boolean complete, List<String> missing, List<String> warnings) {
        this.complete = complete;
        this.missing = missing == null ? new ArrayList<>() : missing;
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public boolean isComplete() {
        return complete;
    }

    public List<String> getMissing() {
        return missing;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
