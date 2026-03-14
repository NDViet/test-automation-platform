package com.platform.ingestion.api;

import com.platform.ingestion.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/results")
@Tag(name = "Result Ingestion", description = "Ingest test results from CI/CD pipelines")
public class ResultIngestionController {

    private final ResultIngestionService ingestionService;

    public ResultIngestionController(ResultIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @AuditLog(eventType = "INGEST", resourceType = "TEST_RUN")
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Ingest test results",
            description = "Accepts raw test report files (JUnit XML, Cucumber JSON, TestNG, Allure, Playwright, Newman) " +
                          "and normalizes them into the platform. Returns a runId for tracking."
    )
    @ApiResponse(responseCode = "202", description = "Results accepted and queued for processing")
    @ApiResponse(responseCode = "400", description = "Invalid request or unsupported format")
    public IngestResponse ingest(
            @RequestParam String teamId,
            @RequestParam String projectId,
            @RequestParam String format,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false, defaultValue = "unknown") String environment,
            @RequestParam(required = false) String commitSha,
            @RequestParam(required = false) String ciRunUrl,
            @RequestPart("files") List<MultipartFile> files) throws IOException {

        var request = new IngestRequest(
                teamId, projectId,
                com.platform.common.enums.SourceFormat.valueOf(format.toUpperCase()),
                branch, environment, commitSha, ciRunUrl,
                null, null, null
        );
        return ingestionService.ingest(request, files);
    }
}
