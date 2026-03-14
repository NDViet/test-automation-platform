package com.platform.ingestion.api;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.service.ExecutionPersistenceService;
import com.platform.ingestion.coverage.CoverageIngestionService;
import com.platform.ingestion.parser.ParseContext;
import com.platform.ingestion.parser.ResultParserFactory;
import com.platform.ingestion.publisher.ResultEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ResultIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ResultIngestionService.class);

    private final ResultParserFactory parserFactory;
    private final ExecutionPersistenceService persistenceService;
    private final ResultEventPublisher eventPublisher;
    private final CoverageIngestionService coverageService;
    private final MeterRegistry meterRegistry;

    public ResultIngestionService(ResultParserFactory parserFactory,
                                   ExecutionPersistenceService persistenceService,
                                   ResultEventPublisher eventPublisher,
                                   CoverageIngestionService coverageService,
                                   MeterRegistry meterRegistry) {
        this.parserFactory      = parserFactory;
        this.persistenceService = persistenceService;
        this.eventPublisher     = eventPublisher;
        this.coverageService    = coverageService;
        this.meterRegistry      = meterRegistry;
    }

    public IngestResponse ingest(IngestRequest request, List<MultipartFile> files) throws IOException {
        String runId = UUID.randomUUID().toString();

        var ctx = new ParseContext(
                runId,
                request.teamId(),
                request.projectId(),
                request.branch() != null ? request.branch() : "unknown",
                request.environment() != null ? request.environment() : "unknown",
                request.commitSha(),
                null,
                request.ciRunUrl(),
                request.executionMode() != null ? request.executionMode() : "UNKNOWN",
                request.parallelism() != null ? request.parallelism() : 0,
                request.suiteName() != null ? request.suiteName() : ""
        );

        List<byte[]> fileBytes = new ArrayList<>();
        for (MultipartFile f : files) {
            fileBytes.add(f.getBytes());
        }

        var parser = parserFactory.getParser(request.format());
        List<UnifiedTestResult> results = parser.parse(fileBytes, ctx);

        for (UnifiedTestResult result : results) {
            TestExecution execution = persistenceService.persist(result);
            eventPublisher.publish(result);
            recordIngestionMetrics(result);
            // Extract @AffectedBy / coveredModules coverage data (no-op if empty)
            if (result.testCases() != null) {
                coverageService.ingestFromResults(
                        execution.getProject().getId(), result.testCases());
            }
        }

        int totalTests = results.stream().mapToInt(UnifiedTestResult::total).sum();

        log.info("Ingested runId={} format={} teamId={} total={} mode={} parallelism={}",
                runId, request.format(), request.teamId(), totalTests,
                ctx.executionMode(), ctx.parallelism());

        return new IngestResponse(runId, "ACCEPTED", totalTests,
                "/api/v1/executions/" + runId);
    }

    private void recordIngestionMetrics(UnifiedTestResult r) {
        String format   = r.sourceFormat() != null ? r.sourceFormat().name() : "UNKNOWN";
        String mode     = r.executionMode();
        String provider = r.ciProvider() != null ? r.ciProvider() : "unknown";

        Counter.builder("ingestion.results.total")
                .tag("format", format)
                .register(meterRegistry).increment();

        Counter.builder("ingestion.execution.mode")
                .tag("mode", mode)
                .register(meterRegistry).increment();

        Counter.builder("ingestion.ci.provider")
                .tag("provider", provider)
                .register(meterRegistry).increment();

        Counter.builder("ingestion.tests.total")
                .tag("format", format)
                .register(meterRegistry).increment(r.total());

        Counter.builder("ingestion.tests.failed")
                .tag("format", format)
                .register(meterRegistry).increment(r.failed());
    }
}
