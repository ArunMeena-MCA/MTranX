package com.wiredesk.mtmx.automation;

import com.wiredesk.mtmx.exception.MtmxException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes each automated-dashboard conversion to backend/sample/&lt;reference_no&gt;.md - a plain,
 * human-readable record of the source MT103 and the converted MX, per the user's own specified
 * format. This is a demo-stage archive, not a database: one file per reference number, overwritten
 * on re-conversion. backend/sample/ is gitignored (see .gitignore) since these files carry real
 * payment data pulled from Oracle.
 */
@Service
public class SampleArchiveService {

    private static final Path SAMPLE_DIR = Paths.get("sample");

    /**
     * @param mxOutput the converted output to archive - the caller decides which form: the real,
     *                 single-root {@code <Envelope>} (ConversionResult.envelopeOutput) when a
     *                 head.001 header applies, or the bare Document (renderedOutput) otherwise.
     *                 This service just writes whatever well-formed XML it's given; it no longer
     *                 does any header/body combining itself (see BusinessApplicationHeaderRenderer.
     *                 renderEnvelope() for that - v2.35, replacing this class's earlier
     *                 string-concatenation approach, which produced two "&lt;?xml?&gt;"-declared
     *                 documents in one string - not valid single-document XML).
     */
    public void write(String referenceNo, String targetFormat, String mtMessage, String mxOutput) {
        try {
            Files.createDirectories(SAMPLE_DIR);
            String content = "# " + referenceNo + "\n\n"
                    + "## MT103 (source)\n\n"
                    + "```\n" + mtMessage.strip() + "\n```\n\n"
                    + "## " + targetFormat + " (converted)\n\n"
                    + "```xml\n" + mxOutput.strip() + "\n```\n";
            Files.writeString(SAMPLE_DIR.resolve(referenceNo + ".md"), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MtmxException("Failed to write sample archive for reference " + referenceNo + ": " + e.getMessage(), e);
        }
    }
}
