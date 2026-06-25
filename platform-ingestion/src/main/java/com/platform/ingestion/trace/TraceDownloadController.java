package com.platform.ingestion.trace;

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

/**
 * Serves Playwright trace ZIPs.
 *
 * <p>Ingestion owns the blob store (it wrote the trace), so it is the right service to serve it —
 * avoids cross-container filesystem access when storage type is "filesystem" (different containers
 * have different local disks).
 */
@RestController
@RequestMapping("/api/v1/stream/traces")
public class TraceDownloadController {

  private static final Logger log = LoggerFactory.getLogger(TraceDownloadController.class);

  private final TestCaseResultRepository resultRepo;
  private final BlobStore blobStore;
  private final BlobStoreProperties storageProps;

  public TraceDownloadController(
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
      log.warn("Trace download: no TestCaseResult for resultId={}", resultId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Result not found: " + resultId);
    }
    if (result.getTraceStorePath() == null) {
      log.warn(
          "Trace download: resultId={} exists but traceStorePath is null (trace not uploaded)",
          resultId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No trace for this result");
    }

    BlobRef ref =
        new BlobRef(
            BlobStoreBuckets.TRACES, result.getTraceStorePath(), null, "application/zip", 0);

    log.debug(
        "Serving trace resultId={} key={} storageType={}",
        resultId,
        result.getTraceStorePath(),
        storageProps.getType());

    if (storageProps.getType() != BlobStoreProperties.Type.filesystem) {
      URI presigned =
          blobStore.presignUrl(ref, Duration.ofMinutes(storageProps.getPresignUrlTtlMinutes()));
      response.setStatus(HttpServletResponse.SC_FOUND);
      response.setHeader("Location", presigned.toString());
    } else {
      byte[] bytes =
          blobStore
              .fetchBytes(ref)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "Trace blob not found on disk: " + ref.key()));
      response.setContentType("application/zip");
      response.setHeader(
          "Content-Disposition", "attachment; filename=\"trace-" + resultId + ".zip\"");
      response.setHeader("Access-Control-Allow-Origin", "https://trace.playwright.dev");
      response.setHeader("Access-Control-Allow-Methods", "GET");
      response.setHeader("Cache-Control", "private, max-age=3600");
      response.getOutputStream().write(bytes);
    }
  }
}
