package com.wiredesk.mtmx.convert;

import com.wiredesk.mtmx.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches one XsdOrderingIndex per target format, shared between
 * every consumer that needs schema-derived structure (MxRenderer for
 * element ordering, ValidatorService for the proactive mandatory-element
 * completeness check) - a single place to change if XSD lookup/caching
 * behavior ever needs to, rather than duplicating the same
 * mtmx.xsd-dir/&lt;format&gt;.xsd lookup and cache in each consumer.
 */
@Component
public class XsdIndexRegistry {

    private final AppProperties props;
    private final Map<String, Optional<XsdOrderingIndex>> cache = new ConcurrentHashMap<>();

    public XsdIndexRegistry(AppProperties props) {
        this.props = props;
    }

    /**
     * Drops the cached entry for a format so the next {@link #get} call
     * re-reads it from disk - needed after a runtime XSD upload/overwrite
     * replaces a file whose format string was already queried and cached
     * (including cached as absent), which {@code computeIfAbsent} alone
     * would otherwise keep serving indefinitely.
     */
    public void evict(String targetFormat) {
        cache.remove(targetFormat);
    }

    public Optional<XsdOrderingIndex> get(String targetFormat) {
        return cache.computeIfAbsent(targetFormat, fmt -> {
            if (props.getXsdDir() == null || props.getXsdDir().isBlank()) {
                return Optional.empty();
            }
            File xsdFile = new File(props.getXsdDir(), fmt + ".xsd");
            if (!xsdFile.exists()) {
                return Optional.empty();
            }
            try {
                return Optional.of(XsdOrderingIndex.parse(xsdFile));
            } catch (Exception e) {
                // Same "degrade, don't block" stance used elsewhere in this
                // index: fall back to heuristic ordering / skip the
                // proactive completeness check rather than failing the
                // conversion over a schema this index couldn't parse -
                // the reactive SAX-based XSD validation pass is still the
                // authoritative gate regardless.
                return Optional.empty();
            }
        });
    }
}
