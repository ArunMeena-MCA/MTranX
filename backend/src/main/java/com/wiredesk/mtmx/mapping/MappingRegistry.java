package com.wiredesk.mtmx.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.wiredesk.mtmx.config.AppProperties;
import com.wiredesk.mtmx.exception.MappingDocInvalidException;
import com.wiredesk.mtmx.exception.MappingDocNotFoundException;
import com.wiredesk.mtmx.mapping.model.MappingDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads mapping documents from the configured mappings folder.
 * Naming convention: {@code <SOURCE_FORMAT>_TO_<TARGET_FORMAT>.yaml}
 * e.g. MT103_TO_PACS008.yaml, PACS008_TO_MT103.yaml, MT202_TO_PACS009.yaml.
 */
@Component
public class MappingRegistry {

    private final AppProperties props;
    private final ObjectMapper rawMapper;   // reads YAML into a literal-keyed Map (for the auditor)
    private final ObjectMapper pojoMapper;  // converts that Map into MappingDocument (snake_case -> camelCase)

    public MappingRegistry(AppProperties props) {
        this.props = props;
        this.rawMapper = new ObjectMapper(new YAMLFactory());
        this.pojoMapper = new ObjectMapper(new YAMLFactory());
        this.pojoMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public List<String> listAvailable() {
        File dir = new File(props.getMappingsDir());
        if (!dir.exists()) {
            return List.of();
        }
        String[] files = dir.list((d, name) -> name.endsWith(".yaml"));
        if (files == null) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(Arrays.asList(files));
        Collections.sort(sorted);
        return sorted;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadRawByFilename(String filename) {
        File file = new File(props.getMappingsDir(), filename);
        try {
            Map<String, Object> raw = rawMapper.readValue(file, Map.class);
            if (raw == null) {
                throw new MappingDocInvalidException("Mapping doc " + file.getPath() + " did not parse to a YAML mapping/object.");
            }
            return raw;
        } catch (IOException e) {
            throw new MappingDocInvalidException("YAML parse error in " + file.getPath() + ": " + e.getMessage());
        }
    }

    public Map<String, Object> loadRaw(String sourceFormat, String targetFormat) {
        String fname = MappingFilenames.mappingFilenameFor(sourceFormat, targetFormat);
        File file = new File(props.getMappingsDir(), fname);
        if (!file.exists()) {
            String available = String.join(", ", listAvailable());
            if (available.isEmpty()) {
                available = "(none found)";
            }
            throw new MappingDocNotFoundException(
                    "No mapping document found at " + file.getPath() + ".\n"
                            + "Expected filename: " + fname + "\n"
                            + "Available mapping docs in " + props.getMappingsDir() + ": " + available
            );
        }
        return loadRawByFilename(fname);
    }

    public MappingDocument loadValidated(String sourceFormat, String targetFormat) {
        Map<String, Object> raw = loadRaw(sourceFormat, targetFormat);
        try {
            return pojoMapper.convertValue(raw, MappingDocument.class);
        } catch (IllegalArgumentException e) {
            throw new MappingDocInvalidException(
                    "Mapping doc for " + sourceFormat + " -> " + targetFormat + " failed schema validation: " + e.getMessage()
            );
        }
    }
}
