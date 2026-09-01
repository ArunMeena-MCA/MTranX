package com.wiredesk.mtmx.web;

import com.wiredesk.mtmx.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import com.wiredesk.mtmx.orchestrate.ConversionOrchestrator;

/**
 * Maps every engine exception to the same structured 422 response shape
 * the previous FastAPI backend used - {"detail": {"error_type", "stage",
 * "message", ...}} - so the existing frontend's error handling
 * (frontend/src/lib/api.js) needs no changes. Keys are written as
 * literal snake_case strings deliberately: Spring's global
 * SNAKE_CASE Jackson naming strategy only rewrites POJO field names, not
 * literal Map<String,Object> keys, so writing them out explicitly here
 * keeps the contract exact regardless of that global setting.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MappingDocNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MappingDocNotFoundException e) {
        return respond("mapping", e, null, null, null);
    }

    @ExceptionHandler(MappingDocIncompleteException.class)
    public ResponseEntity<Map<String, Object>> handleIncomplete(MappingDocIncompleteException e) {
        return respond("mapping", e, e.getMissing(), null, null);
    }

    @ExceptionHandler(MappingDocInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(MappingDocInvalidException e) {
        return respond("mapping", e, null, null, null);
    }

    @ExceptionHandler(ParsingException.class)
    public ResponseEntity<Map<String, Object>> handleParsing(ParsingException e) {
        return respond("parse", e, null, null, null);
    }

    @ExceptionHandler(SemanticDecompositionGapException.class)
    public ResponseEntity<Map<String, Object>> handleDecompositionGap(SemanticDecompositionGapException e) {
        return respond("parse", e, null, null, null);
    }

    @ExceptionHandler(UnmappableFieldException.class)
    public ResponseEntity<Map<String, Object>> handleUnmappable(UnmappableFieldException e) {
        return respond("convert", e, null, null, null);
    }

    @ExceptionHandler(TransformationException.class)
    public ResponseEntity<Map<String, Object>> handleTransformation(TransformationException e) {
        return respond("convert", e, null, null, null);
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleValidationFailed(ValidationFailedException e) {
        return respond("validate", e, null, e.getReport().getErrors(), e.getReport().getWarnings());
    }

    @ExceptionHandler(LlmResponseException.class)
    public ResponseEntity<Map<String, Object>> handleLlm(LlmResponseException e) {
        return respond("unknown", e, null, null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(MethodArgumentNotValidException e) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error_type", "InvalidRequest");
        detail.put("stage", "request");
        detail.put("message", e.getMessage());
        detail.put("pipeline_steps", ConversionOrchestrator.pipelineSteps("request"));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("detail", detail));
    }

    @ExceptionHandler(MtmxException.class)
    public ResponseEntity<Map<String, Object>> handleGenericEngine(MtmxException e) {
        return respond("unknown", e, null, null, null);
    }

    private ResponseEntity<Map<String, Object>> respond(String stage, MtmxException e,
                                                          Object missing, Object errors, Object warnings) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error_type", e.getClass().getSimpleName());
        detail.put("stage", stage);
        detail.put("message", e.getMessage());
        detail.put("pipeline_steps", ConversionOrchestrator.pipelineSteps(stage));
        if (missing != null) {
            detail.put("missing", missing);
        }
        if (errors != null) {
            detail.put("errors", errors);
        }
        if (warnings != null) {
            detail.put("warnings", warnings);
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("detail", detail));
    }
}
