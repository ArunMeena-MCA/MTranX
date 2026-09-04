package com.wiredesk.mtmx.mapping.model;

/**
 * Lets ONE decompose_party-extracted sub-element (e.g. an account number
 * extracted once, alongside a Name/BICFI, in the same pass) route to one of
 * TWO destinations depending on the extracted value's own shape - e.g. an
 * IBAN-shaped account goes to .../Id/IBAN, anything else goes to
 * .../Id/Othr/Id, per pacs.008's genuine AccountIdentification4Choice
 * xs:choice (IBAN | Othr).
 *
 * <p>This exists specifically so that adding IBAN/Othr precision to a field
 * whose account is extracted INSIDE a decompose_party block (alongside
 * Name/BICFI) does not require a second entry that independently re-parses
 * the same raw value a second time - that duplicate-parsing pattern was
 * already tried once for a similar field and deliberately removed (see
 * MT103_TO_PACS00800108.yaml's 50K "v2.1 DEDUP FIX" note) because two
 * independent parsers of the same input have no guarantee of staying in
 * agreement on edge cases. This mechanism instead reuses the SAME single
 * extracted value from the SAME single parse pass, just routes it based on
 * its own content.
 */
public class ConditionalSubElementTarget {
    private String pattern;
    private String ifMatchTarget;
    private String elseTarget;

    /**
     * Optional third outcome, checked BEFORE pattern: if the extracted
     * value full-matches this, it is written to NEITHER ifMatchTarget nor
     * elseTarget - dropped entirely for this sub-element (some OTHER
     * entry, e.g. a dedicated clearing-system-code entry, is already
     * responsible for that content). Added for the case where the same
     * account_line_pattern strip that correctly positions a following
     * BICFI/Name line ALSO, unavoidably, captures a "//"-prefixed
     * clearing-system line as if it were a generic account identifier -
     * see MT103_TO_PACS00800108.yaml's 52A entry's v2.18 note for the
     * concrete bug this fixes (a UK sort code like "//SC123456" was
     * being written BOTH to the correct ClrSysMmbId AND, wrongly, to
     * DbtrAgtAcct/Othr/Id as a duplicate, semantically wrong account).
     */
    private String skipPattern;

    public String getSkipPattern() {
        return skipPattern;
    }

    public void setSkipPattern(String skipPattern) {
        this.skipPattern = skipPattern;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getIfMatchTarget() {
        return ifMatchTarget;
    }

    public void setIfMatchTarget(String ifMatchTarget) {
        this.ifMatchTarget = ifMatchTarget;
    }

    public String getElseTarget() {
        return elseTarget;
    }

    public void setElseTarget(String elseTarget) {
        this.elseTarget = elseTarget;
    }
}
