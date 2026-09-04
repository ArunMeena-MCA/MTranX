package com.wiredesk.mtmx.mapping;

/**
 * The two filename conventions this engine relies on, in one place.
 *
 * <p>Mapping YAML filenames are NORMALISED: {@code normalise(source) +
 * "_TO_" + normalise(target) + ".yaml"} (e.g. MT103_TO_PACS00800108.yaml).
 * {@link #normalise} strips everything except A-Z0-9, which also happens
 * to neutralise path-traversal characters ('/', '.', '..') - a side
 * effect of the whitelist, not a dedicated security check, so this must
 * never be bypassed for a filename derived from user input.
 *
 * <p>XSD filenames are NOT normalised - {@code XsdIndexRegistry.get()}
 * and {@code ValidatorService} both look up {@code <verbatim
 * target_format>.xsd} (e.g. "pacs.008.001.08.xsd", dots preserved).
 * Applying {@link #normalise} to an XSD filename would break lookup for
 * every conversion that depends on it - see {@link #xsdFilenameFor}.
 */
public final class MappingFilenames {

    private MappingFilenames() {
    }

    public static String normalise(String fmt) {
        return fmt.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public static String mappingFilenameFor(String sourceFormat, String targetFormat) {
        return normalise(sourceFormat) + "_TO_" + normalise(targetFormat) + ".yaml";
    }

    /** Verbatim (trimmed only) - deliberately NOT run through {@link #normalise}. */
    public static String xsdFilenameFor(String format) {
        return format.trim() + ".xsd";
    }
}
