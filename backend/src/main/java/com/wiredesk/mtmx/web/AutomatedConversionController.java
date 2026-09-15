package com.wiredesk.mtmx.web;

import com.wiredesk.mtmx.automation.FetchedMtMessage;
import com.wiredesk.mtmx.automation.OracleMessageFetchService;
import com.wiredesk.mtmx.automation.SampleArchiveService;
import com.wiredesk.mtmx.orchestrate.ConversionOrchestrator;
import com.wiredesk.mtmx.orchestrate.ConversionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The automated dashboard's two endpoints: pull a fixed batch of real MT103 messages from Oracle
 * (see OracleMessageFetchService), and convert one of them on request. Hardcoded to MT103 ->
 * pacs.008.001.08 - this whole feature is scoped to that one pair (the Oracle query itself
 * filters on swift_msg_type='103'), unlike ConversionController's generic /api/convert.
 *
 * <p>Conversion is triggered ONE ROW AT A TIME, driven by the frontend's own sequential loop
 * (see AutomatedDashboard.jsx) rather than a single "convert all 10" backend call - this keeps
 * the backend stateless (consistent with the rest of this engine - see DOCUMENTATION.md §5.5)
 * and lets the frontend show live, one-at-a-time progress without needing a streaming/WebSocket
 * mechanism just for a 10-row demo.
 */
@RestController
@RequestMapping("/api/automated")
public class AutomatedConversionController {

    private static final String SOURCE_FORMAT = "MT103";
    private static final String TARGET_FORMAT = "pacs.008.001.08";
    private static final int FETCH_LIMIT = 10;

    private final OracleMessageFetchService fetchService;
    private final ConversionOrchestrator orchestrator;
    private final SampleArchiveService archiveService;

    public AutomatedConversionController(OracleMessageFetchService fetchService,
                                          ConversionOrchestrator orchestrator,
                                          SampleArchiveService archiveService) {
        this.fetchService = fetchService;
        this.orchestrator = orchestrator;
        this.archiveService = archiveService;
    }

    @GetMapping("/messages")
    public List<FetchedMtMessage> fetchMessages() {
        return fetchService.fetchFirst(FETCH_LIMIT);
    }

    @PostMapping("/convert")
    public ConversionResult convert(@Valid @RequestBody AutomatedConvertRequest request) {
        ConversionResult result = orchestrator.convert(request.getMessage(), SOURCE_FORMAT, TARGET_FORMAT);
        // Prefer the real combined <Envelope> (AppHdr + Document) when a head.001 header applies;
        // falls back to the bare Document otherwise - see ConversionResult.envelopeOutput's Javadoc.
        String mxOutput = result.getEnvelopeOutput() != null ? result.getEnvelopeOutput() : result.getRenderedOutput();
        archiveService.write(request.getReferenceNo(), TARGET_FORMAT, request.getMessage(), mxOutput);
        return result;
    }
}
