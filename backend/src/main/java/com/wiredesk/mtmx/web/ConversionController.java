package com.wiredesk.mtmx.web;

import com.wiredesk.mtmx.exception.MappingDocInvalidException;
import com.wiredesk.mtmx.mapping.MappingRegistry;
import com.wiredesk.mtmx.orchestrate.ConversionOrchestrator;
import com.wiredesk.mtmx.orchestrate.ConversionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Same endpoint shapes as the previous FastAPI backend, so the existing
 * React frontend needs zero changes:
 *   GET  /api/health
 *   GET  /api/mappings
 *   POST /api/convert
 */
@RestController
@RequestMapping("/api")
public class ConversionController {

    private final ConversionOrchestrator orchestrator;
    private final MappingRegistry registry;

    public ConversionController(ConversionOrchestrator orchestrator, MappingRegistry registry) {
        this.orchestrator = orchestrator;
        this.registry = registry;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/mappings")
    public List<MappingSummaryDto> listMappings() {
        List<MappingSummaryDto> summaries = new ArrayList<>();
        for (String filename : registry.listAvailable()) {
            try {
                Map<String, Object> raw = registry.loadRawByFilename(filename);
                summaries.add(new MappingSummaryDto(
                        String.valueOf(raw.getOrDefault("conversion_id", filename)),
                        String.valueOf(raw.getOrDefault("source_format", "")),
                        String.valueOf(raw.getOrDefault("target_format", ""))
                ));
            } catch (MappingDocInvalidException ignored) {
                // Skip files that don't parse cleanly rather than failing the whole listing -
                // they'll still surface loudly the moment someone tries to convert against them.
            }
        }
        return summaries;
    }

    @PostMapping("/convert")
    public ConversionResult convert(@Valid @RequestBody ConvertRequest request) {
        return orchestrator.convert(request.getRawText(), request.getSourceFormat(), request.getTargetFormat());
    }
}
