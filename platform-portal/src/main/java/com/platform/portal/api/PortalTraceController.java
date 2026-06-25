package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Proxies Playwright trace ZIPs from platform-ingestion to the browser.
 *
 * <p>Traces are stored by platform-ingestion (it receives the upload from the reporter). Serving
 * them from ingestion avoids cross-container filesystem access problems that arise in "filesystem"
 * storage mode when analytics and ingestion run as separate Docker containers with separate local
 * disks.
 *
 * <p>In MinIO mode, ingestion issues a 302 to a presigned URL; RestClient follows it automatically
 * so the portal transparently streams the bytes to the browser.
 */
@RestController
@RequestMapping("/api/portal/traces")
public class PortalTraceController {

  private final RestClient ingestionClient;

  public PortalTraceController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/{resultId}")
  @Operation(summary = "Download Playwright trace ZIP for a test result")
  public void downloadTrace(@PathVariable String resultId, HttpServletResponse response)
      throws IOException {

    String uri =
        UriComponentsBuilder.fromPath("/api/v1/stream/traces/{resultId}")
            .buildAndExpand(resultId)
            .toUriString();

    byte[] traceBytes = ingestionClient.get().uri(uri).retrieve().body(byte[].class);

    if (traceBytes == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    response.setContentType("application/zip");
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"trace-" + resultId + ".zip\"");
    response.setHeader("Access-Control-Allow-Origin", "https://trace.playwright.dev");
    response.setHeader("Access-Control-Allow-Methods", "GET");
    response.setHeader("Cache-Control", "private, max-age=3600");
    response.getOutputStream().write(traceBytes);
  }
}
