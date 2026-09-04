package com.wiredesk.mtmx.exception;

import java.util.List;

/**
 * The incoming source message is missing a field (or every option of an
 * alternative group) that the mapping doc's own source_alternative_group_required
 * validation_rules declare mandatory per the real SWIFT standard (e.g. MT103
 * fields 20/23B/32A/71A, or "at least one of 50A/50F/50K"). Checked against
 * the PARSED SOURCE message immediately after parsing, before the converter
 * is ever invoked - unlike the same rule re-checked post-conversion inside
 * ValidatorService (kept as a second, defense-in-depth layer), this is a
 * genuine fail-fast precondition: a missing mandatory source field can never
 * be fixed by retrying the converter against the same input, so there is no
 * reason to burn conversion attempts before reporting it.
 */
public class MandatorySourceFieldMissingException extends MtmxException {
    private final List<String> problems;

    public MandatorySourceFieldMissingException(List<String> problems) {
        super(buildMessage(problems));
        this.problems = problems;
    }

    private static String buildMessage(List<String> problems) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source message is missing one or more fields the mapping doc declares mandatory ")
          .append("per the SWIFT standard - refusing to attempt conversion.\n");
        for (String p : problems) {
            sb.append("  - ").append(p).append("\n");
        }
        return sb.toString();
    }

    public List<String> getProblems() {
        return problems;
    }
}
