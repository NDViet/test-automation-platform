package com.platform.ingestion.coverage;

import com.platform.common.dto.CoverageManifest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Accepts a standalone coverage manifest — for teams that generate coverage data
 * outside of the normal test run (e.g. from JaCoCo XML post-processing).
 *
 * <pre>{@code
 * POST /api/v1/coverage
 * X-API-Key: plat_xxx
 * Content-Type: application/json
 *
 * {
 *   "projectId": "proj-checkout",
 *   "mappings": [
 *     { "testId": "com.example.CheckoutTest#verifyTotal",
 *       "coveredClasses": ["com.example.PaymentService", "com.example.CartService"] }
 *   ]
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/coverage")
@Tag(name = "Test Coverage", description = "Register test-to-class coverage mappings for Test Impact Analysis")
public class CoverageIngestionController {

    private final CoverageIngestionService coverageService;

    public CoverageIngestionController(CoverageIngestionService coverageService) {
        this.coverageService = coverageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Submit coverage manifest",
            description = "Registers test-to-class mappings. Used by JaCoCo post-processors " +
                          "or any CI step that knows which tests cover which production classes."
    )
    public Map<String, Object> ingest(@RequestBody CoverageManifest manifest) {
        int count = coverageService.ingestManifest(manifest);
        return Map.of(
                "projectId", manifest.projectId(),
                "mappingsUpserted", count,
                "status", "ACCEPTED"
        );
    }
}
