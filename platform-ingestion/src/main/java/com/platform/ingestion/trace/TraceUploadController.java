package com.platform.ingestion.trace;

import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stream/runs/{runId}/trace")
public class TraceUploadController {

  private final TraceStorageService traceStorage;

  public TraceUploadController(TraceStorageService traceStorage) {
    this.traceStorage = traceStorage;
  }

  @PostMapping(consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Upload a Playwright trace ZIP for a test result")
  public void uploadTrace(
      @PathVariable String runId,
      @RequestParam("testId") String testId,
      @RequestParam("trace") MultipartFile trace)
      throws IOException {
    traceStorage.storeTrace(runId, testId, trace);
  }
}
