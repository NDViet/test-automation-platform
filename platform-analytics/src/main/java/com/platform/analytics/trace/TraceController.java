package com.platform.analytics.trace;

import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.common.storage.BlobStoreBuckets;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.storage.BlobStoreProperties;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics/traces")
public class TraceController {

  private static final Logger log = LoggerFactory.getLogger(TraceController.class);

  private final TestCaseResultRepository resultRepo;
  private final BlobStore blobStore;
  private final BlobStoreProperties storageProps;

  public TraceController(
      TestCaseResultRepository resultRepo, BlobStore blobStore, BlobStoreProperties storageProps) {
    this.resultRepo = resultRepo;
    this.blobStore = blobStore;
    this.storageProps = storageProps;
  }

  @GetMapping("/{resultId}")
  @Operation(summary = "Download the Playwright trace ZIP for a test result")
  public void downloadTrace(@PathVariable UUID resultId, HttpServletResponse response)
      throws IOException {

    var result = resultRepo.findById(resultId).orElse(null);
    if (result == null) {
      log.warn("Trace request for unknown resultId={}", resultId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Result not found: " + resultId);
    }

    if (result.getTraceStorePath() == null) {
      log.warn(
          "Trace request for resultId={} but traceStorePath is null (trace was never uploaded)",
          resultId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No trace for this result");
    }

    log.debug(
        "Serving trace resultId={} storePath={} storageType={}",
        resultId,
        result.getTraceStorePath(),
        storageProps.getType());

    BlobRef ref =
        new BlobRef(
            BlobStoreBuckets.TRACES, result.getTraceStorePath(), null, "application/zip", 0);

    if (storageProps.getType() != BlobStoreProperties.Type.filesystem) {
      // MinIO / S3: redirect browser directly to a time-limited presigned URL.
      // BlobStoreBucketInitializer already applied CORS for trace.playwright.dev on this bucket.
      URI presigned =
          blobStore.presignUrl(ref, Duration.ofMinutes(storageProps.getPresignUrlTtlMinutes()));
      response.setStatus(HttpServletResponse.SC_FOUND);
      response.setHeader("Location", presigned.toString());
    } else {
      // Local dev (FilesystemBlobStore): stream bytes directly.
      byte[] bytes =
          blobStore
              .fetchBytes(ref)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "Trace not found in store"));
      response.setContentType("application/zip");
      response.setHeader(
          "Content-Disposition", "attachment; filename=\"trace-" + resultId + ".zip\"");
      response.setHeader("Access-Control-Allow-Origin", "https://trace.playwright.dev");
      response.setHeader("Cache-Control", "private, max-age=3600");
      response.getOutputStream().write(bytes);
    }
  }
}
