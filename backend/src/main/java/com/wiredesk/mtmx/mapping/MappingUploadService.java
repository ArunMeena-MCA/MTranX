package com.wiredesk.mtmx.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.wiredesk.mtmx.config.AppProperties;
import com.wiredesk.mtmx.convert.XsdIndexRegistry;
import com.wiredesk.mtmx.exception.MappingDocIncompleteException;
import com.wiredesk.mtmx.exception.MappingDocInvalidException;
import com.wiredesk.mtmx.exception.MappingUploadConflictException;
import com.wiredesk.mtmx.web.MappingCheckResult;
import com.wiredesk.mtmx.web.MappingUploadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets a user register a brand-new (or replace an existing) MT&lt;-&gt;MX
 * conversion at runtime by uploading a mapping YAML and (conditionally) an
 * XSD - no server restart, no per-conversion Java code, since the rest of
 * the engine (MappingRegistry, MxParserService, ConverterService,
 * ValidatorService) already reads mapping docs fresh from disk on every
 * request. See {@link MappingFilenames} for the two filename conventions
 * this depends on getting right.
 *
 * <p>Nothing is written to disk until every validation step below passes -
 * a rejected upload leaves the mappings/xsd directories untouched.
 */
@Service
public class MappingUploadService {

    public static final String MT_TO_MX = "MT_TO_MX";
    public static final String MX_TO_MT = "MX_TO_MT";

    private final AppProperties props;
    private final CompletenessAuditor auditor;
    private final XsdIndexRegistry xsdIndexRegistry;
    private final ObjectMapper rawMapper;

    public MappingUploadService(AppProperties props, CompletenessAuditor auditor, XsdIndexRegistry xsdIndexRegistry) {
        this.props = props;
        this.auditor = auditor;
        this.xsdIndexRegistry = xsdIndexRegistry;
        this.rawMapper = new ObjectMapper(new YAMLFactory());
    }

    /** Cheap, no-file-upload preview of whether a pair already has a mapping doc on disk. */
    @SuppressWarnings("unchecked")
    public MappingCheckResult check(String sourceFormat, String targetFormat) {
        if (isBlank(sourceFormat) || isBlank(targetFormat)) {
            return new MappingCheckResult(false, null, null);
        }
        String filename = MappingFilenames.mappingFilenameFor(sourceFormat, targetFormat);
        File file = new File(props.getMappingsDir(), filename);
        if (!file.exists()) {
            return new MappingCheckResult(false, null, filename);
        }
        String conversionId = filename;
        try {
            Map<String, Object> raw = rawMapper.readValue(file, Map.class);
            if (raw != null && raw.get("conversion_id") != null) {
                conversionId = String.valueOf(raw.get("conversion_id"));
            }
        } catch (IOException ignored) {
            // Exists but doesn't currently parse - still report it as existing under a
            // fallback id (the filename), since the "does something already live here"
            // question is about the file path, not about that file's current validity.
        }
        return new MappingCheckResult(true, conversionId, filename);
    }

    @SuppressWarnings("unchecked")
    public MappingUploadResult upload(String direction, String sourceFormat, String targetFormat,
                                       MultipartFile mappingFile, MultipartFile xsdFile, boolean confirm) {
        String dir = normaliseDirection(direction);
        if (isBlank(sourceFormat) || isBlank(targetFormat)) {
            throw new MappingDocInvalidException("source_format and target_format are both required.");
        }
        if (mappingFile == null || mappingFile.isEmpty()) {
            throw new MappingDocInvalidException("A mapping YAML file is required.");
        }
        sourceFormat = sourceFormat.trim();
        targetFormat = targetFormat.trim();
        boolean targetIsMx = MT_TO_MX.equals(dir);

        List<String> warnings = new ArrayList<>();
        String mappingFilename = MappingFilenames.mappingFilenameFor(sourceFormat, targetFormat);

        boolean filenameMatches = filenameLooksConsistent(mappingFile.getOriginalFilename(), mappingFilename);
        if (!filenameMatches) {
            warnings.add("The uploaded file is named '" + mappingFile.getOriginalFilename() + "', which doesn't "
                    + "look like it matches " + sourceFormat + " -> " + targetFormat + " (expected something like "
                    + mappingFilename + "). Double check you selected the right file.");
        }

        Map<String, Object> raw;
        try (InputStream in = mappingFile.getInputStream()) {
            raw = rawMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new MappingDocInvalidException("Uploaded mapping file is not valid YAML: " + e.getMessage());
        }
        if (raw == null) {
            throw new MappingDocInvalidException("Uploaded mapping file did not parse to a YAML mapping/object.");
        }

        String conversionId = String.valueOf(raw.getOrDefault("conversion_id", mappingFilename));

        AuditResult audit = auditor.audit(raw);
        if (!audit.isComplete()) {
            throw new MappingDocIncompleteException(conversionId, audit.getMissing());
        }
        warnings.addAll(audit.getWarnings());

        String yamlSource = String.valueOf(raw.getOrDefault("source_format", ""));
        String yamlTarget = String.valueOf(raw.getOrDefault("target_format", ""));
        if (!MappingFilenames.normalise(yamlSource).equals(MappingFilenames.normalise(sourceFormat))
                || !MappingFilenames.normalise(yamlTarget).equals(MappingFilenames.normalise(targetFormat))) {
            warnings.add("The uploaded YAML's own source_format/target_format ('" + yamlSource + "' / '"
                    + yamlTarget + "') don't match what you typed ('" + sourceFormat + "' / '" + targetFormat
                    + "'). The file is saved under the name computed from what you typed, since lookups go by "
                    + "filename, not by these internal fields - a mismatch here will cause confusing failures "
                    + "later. Recommend fixing one side or the other.");
        }

        boolean xsdProvided = xsdFile != null && !xsdFile.isEmpty();
        if (targetIsMx && !xsdProvided) {
            throw new MappingDocInvalidException(
                    "An XSD file is required for MT -> MX uploads (the target, " + targetFormat + ", is an MX/pacs "
                            + "format that needs its schema for element ordering and validation).");
        }
        byte[] xsdBytes = null;
        String xsdFormatKey = null;
        String xsdFilename = null;
        if (xsdProvided) {
            try {
                xsdBytes = xsdFile.getBytes();
            } catch (IOException e) {
                throw new MappingDocInvalidException("Could not read the uploaded XSD file: " + e.getMessage());
            }
            assertWellFormedXml(xsdBytes);
            // MT_TO_MX: XSD is for the MX target, looked up by ValidatorService/XsdIndexRegistry
            // by target_format. MX_TO_MT: MT targets have no schema concept, so this is stored
            // under the MX source format's name for potential future use only - nothing in the
            // engine reads it today.
            xsdFormatKey = targetIsMx ? targetFormat : sourceFormat;
            xsdFilename = MappingFilenames.xsdFilenameFor(xsdFormatKey);
        }

        File mappingTarget = new File(props.getMappingsDir(), mappingFilename);
        boolean mappingExists = mappingTarget.exists();

        File xsdTarget = null;
        boolean xsdExists = false;
        if (xsdProvided) {
            if (isBlank(props.getXsdDir())) {
                throw new MappingDocInvalidException(
                        "mtmx.xsd-dir is not configured on this server - cannot save an XSD file.");
            }
            xsdTarget = new File(props.getXsdDir(), xsdFilename);
            xsdExists = xsdTarget.exists();
        }

        boolean wouldOverwrite = mappingExists || xsdExists;
        if ((wouldOverwrite || !filenameMatches) && !confirm) {
            throw new MappingUploadConflictException(buildConflictMessage(
                    sourceFormat, targetFormat, mappingFilename, xsdFilename, mappingExists, xsdExists, filenameMatches));
        }

        try {
            writeAtomically(mappingTarget, mappingFile.getBytes());
            if (xsdProvided) {
                writeAtomically(xsdTarget, xsdBytes);
                if (targetIsMx) {
                    xsdIndexRegistry.evict(xsdFormatKey);
                }
            }
        } catch (IOException e) {
            throw new MappingDocInvalidException("Failed to save uploaded files: " + e.getMessage());
        }

        return new MappingUploadResult(conversionId, sourceFormat, targetFormat, mappingFilename, xsdFilename,
                wouldOverwrite, warnings);
    }

    private String buildConflictMessage(String sourceFormat, String targetFormat, String mappingFilename,
                                         String xsdFilename, boolean mappingExists, boolean xsdExists,
                                         boolean filenameMatches) {
        StringBuilder msg = new StringBuilder();
        if (mappingExists || xsdExists) {
            msg.append("A mapping already exists for ").append(sourceFormat).append(" -> ").append(targetFormat)
                    .append(" (").append(mappingFilename).append(")");
            if (xsdExists) {
                msg.append(" along with its XSD (").append(xsdFilename).append(")");
            }
            msg.append(" - uploading will overwrite it.");
        }
        if (!filenameMatches) {
            if (msg.length() > 0) {
                msg.append(" Also: ");
            } else {
                msg.append("Notice: ");
            }
            msg.append("the uploaded file's name doesn't look like it matches the typed source/target format.");
        }
        msg.append(" Resubmit with confirm=true to proceed anyway.");
        return msg.toString();
    }

    private String normaliseDirection(String direction) {
        if (direction == null) {
            throw new MappingDocInvalidException("direction is required (MT_TO_MX or MX_TO_MT).");
        }
        String d = direction.trim().toUpperCase().replace('-', '_');
        if (!MT_TO_MX.equals(d) && !MX_TO_MT.equals(d)) {
            throw new MappingDocInvalidException("direction must be MT_TO_MX or MX_TO_MT, got: " + direction);
        }
        return d;
    }

    /** Loose match: same alphanumerics once the extension and any path prefix are stripped. */
    private boolean filenameLooksConsistent(String uploadedName, String canonicalFilename) {
        if (uploadedName == null || uploadedName.isBlank()) {
            return true; // nothing to compare against (e.g. some browsers/clients may omit it)
        }
        return MappingFilenames.normalise(stripYamlExtension(uploadedName))
                .equals(MappingFilenames.normalise(stripYamlExtension(canonicalFilename)));
    }

    private String stripYamlExtension(String filename) {
        String base = filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String lower = base.toLowerCase();
        if (lower.endsWith(".yaml")) {
            return base.substring(0, base.length() - 5);
        }
        if (lower.endsWith(".yml")) {
            return base.substring(0, base.length() - 4);
        }
        return base;
    }

    /** Proves well-formed XML only, not full XML-Schema validity - see class-level notes. */
    private void assertWellFormedXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bytes));
            if (doc.getDocumentElement() == null) {
                throw new MappingDocInvalidException("Uploaded XSD has no root element.");
            }
        } catch (MappingDocInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new MappingDocInvalidException("Uploaded XSD is not well-formed XML: " + e.getMessage());
        }
    }

    /** Write to a temp file in the same directory, then atomically rename - never leaves a torn file. */
    private void writeAtomically(File target, byte[] content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        File tmp = File.createTempFile(target.getName(), ".tmp", parent);
        try {
            Files.write(tmp.toPath(), content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
