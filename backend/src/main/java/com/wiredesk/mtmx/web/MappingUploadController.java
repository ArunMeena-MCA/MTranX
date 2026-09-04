package com.wiredesk.mtmx.web;

import com.wiredesk.mtmx.mapping.MappingUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lets an operator register a new (or overwrite an existing) MT&lt;-&gt;MX
 * conversion at runtime, without a server restart or per-conversion Java
 * code - see {@link MappingUploadService} for the actual logic.
 *
 * <p><b>No authentication on this endpoint.</b> This app has no auth layer
 * anywhere (CORS is wide open - see WebConfig), so anyone who can reach
 * this API can overwrite any existing mapping/XSD file. Acceptable for a
 * local/internal dev tool as currently deployed; add an auth check here
 * before ever exposing this beyond localhost/a trusted network.
 */
@RestController
@RequestMapping("/api/mappings")
public class MappingUploadController {

    private final MappingUploadService uploadService;

    public MappingUploadController(MappingUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping("/check")
    public MappingCheckResult check(@RequestParam("source_format") String sourceFormat,
                                     @RequestParam("target_format") String targetFormat) {
        return uploadService.check(sourceFormat, targetFormat);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public MappingUploadResult upload(@RequestParam("direction") String direction,
                                       @RequestParam("source_format") String sourceFormat,
                                       @RequestParam("target_format") String targetFormat,
                                       @RequestParam("mapping_file") MultipartFile mappingFile,
                                       @RequestParam(value = "xsd_file", required = false) MultipartFile xsdFile,
                                       @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {
        return uploadService.upload(direction, sourceFormat, targetFormat, mappingFile, xsdFile, confirm);
    }
}
