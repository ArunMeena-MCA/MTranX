package com.wiredesk.mtmx.orchestrate;

import com.wiredesk.mtmx.config.AppProperties;
import com.wiredesk.mtmx.convert.ConvertedMessage;
import com.wiredesk.mtmx.convert.ConverterService;
import com.wiredesk.mtmx.exception.MandatorySourceFieldMissingException;
import com.wiredesk.mtmx.exception.MappingDocIncompleteException;
import com.wiredesk.mtmx.exception.ValidationFailedException;
import com.wiredesk.mtmx.mapping.AuditResult;
import com.wiredesk.mtmx.mapping.CompletenessAuditor;
import com.wiredesk.mtmx.mapping.MappingRegistry;
import com.wiredesk.mtmx.mapping.model.MappingDocument;
import com.wiredesk.mtmx.parser.MtParserService;
import com.wiredesk.mtmx.parser.MxParserService;
import com.wiredesk.mtmx.parser.ParsedMessage;
import com.wiredesk.mtmx.validate.ValidationReport;
import com.wiredesk.mtmx.validate.ValidatorService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Wires the full pipeline together:
 *
 *   MappingRegistry.loadRaw
 *       -> CompletenessAuditor (gatekeeper, no LLM)
 *       -> MtParserService / MxParserService (Prowide for MT, generic DOM for MX)
 *       -> ConverterService (deterministic + narrowly-scoped LLM)
 *       -> ValidatorService (deterministic + LLM audit)
 *            -> loop back to ConverterService on CONVERSION_ERROR (bounded retries)
 *            -> abort with MappingDocIncompleteException on MAPPING_GAP
 *            -> return ConversionResult on success
 *
 * This class is the only place retry/abort policy lives.
 */
@Service
public class ConversionOrchestrator {

    private final MappingRegistry registry;
    private final CompletenessAuditor auditor;
    private final MtParserService mtParser;
    private final MxParserService mxParser;
    private final ConverterService converter;
    private final ValidatorService validator;
    private final AppProperties props;

    public ConversionOrchestrator(MappingRegistry registry,
                                   CompletenessAuditor auditor,
                                   MtParserService mtParser,
                                   MxParserService mxParser,
                                   ConverterService converter,
                                   ValidatorService validator,
                                   AppProperties props) {
        this.registry = registry;
        this.auditor = auditor;
        this.mtParser = mtParser;
        this.mxParser = mxParser;
        this.converter = converter;
        this.validator = validator;
        this.props = props;
    }

    public ConversionResult convert(String rawText, String sourceFormat, String targetFormat) {
        Map<String, Object> rawDoc = registry.loadRaw(sourceFormat, targetFormat);
        String conversionId = String.valueOf(rawDoc.getOrDefault("conversion_id", sourceFormat + "_TO_" + targetFormat));

        AuditResult audit = auditor.audit(rawDoc);
        if (!audit.isComplete()) {
            throw new MappingDocIncompleteException(conversionId, audit.getMissing());
        }

        MappingDocument doc = registry.loadValidated(sourceFormat, targetFormat);

        ParsedMessage parsed = sourceFormat.toUpperCase().startsWith("MT")
                ? mtParser.parse(rawText, sourceFormat)
                : mxParser.parse(rawText, sourceFormat);

        // Fail fast on a source message that is missing a field the mapping
        // doc declares mandatory per the real SWIFT standard (e.g. MT103's
        // 20/23B/32A/71A, or "at least one of 50A/50F/50K") - checked here,
        // BEFORE the first conversion attempt, because this is a property of
        // the input alone and can never be fixed by retrying the converter
        // against the same parsed fields. Previously this same check only
        // ran post-conversion inside ValidatorService, where its failure was
        // indistinguishable from a retryable CONVERSION_ERROR - burning up
        // to maxConverterRetries+2 pointless converter re-runs before
        // ValidationFailedException was finally thrown. That post-conversion
        // check is intentionally left in place too (defense in depth, same
        // pattern as VR006's dual-mechanism enforcement) in case some future
        // caller reaches ConverterService directly without going through
        // this orchestrator.
        List<String> mandatoryFieldProblems = validator.checkMandatorySourceFields(parsed.getFields(), doc);
        if (!mandatoryFieldProblems.isEmpty()) {
            throw new MandatorySourceFieldMissingException(mandatoryFieldProblems);
        }

        ValidationReport lastReport = null;
        int maxAttempts = props.getMaxConverterRetries() + 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ConvertedMessage converted = converter.convert(parsed, doc);
            ValidationReport report = validator.validate(parsed.getFields(), converted, doc);
            lastReport = report;

            if (report.isValid()) {
                ConversionResult result = new ConversionResult();
                result.setSourceFormat(sourceFormat);
                result.setTargetFormat(targetFormat);
                result.setRenderedOutput(converted.getRenderedText());
                result.setParsedSourceFields(parsed.getFields());
                result.setConvertedTree(converted.getTree());
                result.setValidationWarnings(report.getWarnings());
                result.setLlmAuditStatus(report.getLlmAuditStatus());
                result.setAttempts(attempt);
                result.setAuditWarnings(audit.getWarnings());
                result.setFieldTrace(converted.getFieldTrace());
                result.setPipelineSteps(pipelineSteps(null));
                return result;
            }

            if ("MAPPING_GAP".equals(report.getClassification())) {
                throw new MappingDocIncompleteException(conversionId, report.getErrors());
            }
            // CONVERSION_ERROR -> loop back and let the converter try again.
        }

        throw new ValidationFailedException(lastReport);
    }

    public static List<Map<String, Object>> pipelineSteps(String failedStage) {
        List<Map<String, Object>> steps = new ArrayList<>();
        List<String> names = List.of("mapping", "parse", "convert", "validate");
        int failedIndex = failedStage == null ? -1 : names.indexOf(failedStage);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("key", name);
            step.put("status", failedIndex == -1 || i < failedIndex ? "done"
                    : i == failedIndex ? "error" : "skipped");
            steps.add(step);
        }
        return steps;
    }
}
