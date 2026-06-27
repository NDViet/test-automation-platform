package com.platform.ingestion.management.tcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-runs")
@Tag(name = "Test Case Management")
public class TestRunController {

  private final TestRunService service;
  private final ExecutionAttachmentService attachments;

  public TestRunController(TestRunService service, ExecutionAttachmentService attachments) {
    this.service = service;
    this.attachments = attachments;
  }

  @GetMapping
  public List<TestRunDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping
  public ResponseEntity<TestRunDto> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateTestRunRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
  }

  @GetMapping("/{runId}")
  public TestRunDto get(@PathVariable UUID projectId, @PathVariable UUID runId) {
    return service.get(projectId, runId);
  }

  @PutMapping("/{runId}")
  public TestRunDto update(
      @PathVariable UUID projectId, @PathVariable UUID runId, @RequestBody UpdateRunRequest req) {
    return service.updateRun(projectId, runId, req);
  }

  @DeleteMapping("/{runId}")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID runId) {
    service.delete(projectId, runId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{runId}/complete")
  public TestRunDto complete(@PathVariable UUID projectId, @PathVariable UUID runId) {
    return service.complete(projectId, runId);
  }

  @PostMapping("/{runId}/reopen")
  public TestRunDto reopen(@PathVariable UUID projectId, @PathVariable UUID runId) {
    return service.reopen(projectId, runId);
  }

  @PostMapping("/{runId}/cases")
  public TestRunDto addCases(
      @PathVariable UUID projectId, @PathVariable UUID runId, @RequestBody AddCasesRequest req) {
    return service.addCases(projectId, runId, req);
  }

  @GetMapping("/{runId}/executions")
  public List<TestCaseExecutionDto> listExecutions(
      @PathVariable UUID projectId, @PathVariable UUID runId) {
    return service.listExecutions(projectId, runId);
  }

  @PutMapping("/{runId}/executions/{execId}")
  public TestCaseExecutionDto updateExecution(
      @PathVariable UUID projectId,
      @PathVariable UUID runId,
      @PathVariable UUID execId,
      @Valid @RequestBody UpdateExecutionRequest req) {
    return service.updateExecution(projectId, runId, execId, req);
  }

  @PostMapping("/{runId}/executions/{execId}/defect")
  public TestCaseExecutionDto linkDefect(
      @PathVariable UUID projectId,
      @PathVariable UUID runId,
      @PathVariable UUID execId,
      @RequestBody LinkDefectRequest req) {
    return service.linkDefect(projectId, runId, execId, req);
  }

  @DeleteMapping("/{runId}/executions/{execId}/defect")
  public TestCaseExecutionDto unlinkDefect(
      @PathVariable UUID projectId, @PathVariable UUID runId, @PathVariable UUID execId) {
    return service.unlinkDefect(projectId, runId, execId);
  }

  // ── Evidence attachments ───────────────────────────────────────────────────

  @GetMapping("/{runId}/executions/{execId}/attachments")
  public List<ExecutionAttachmentDto> listAttachments(
      @PathVariable UUID projectId, @PathVariable UUID runId, @PathVariable UUID execId) {
    return attachments.list(projectId, runId, execId);
  }

  @PostMapping(value = "/{runId}/executions/{execId}/attachments", consumes = "multipart/form-data")
  public ExecutionAttachmentDto uploadAttachment(
      @PathVariable UUID projectId,
      @PathVariable UUID runId,
      @PathVariable UUID execId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return attachments.upload(projectId, runId, execId, file, actor);
  }

  @GetMapping("/{runId}/attachments/{attachmentId}/download")
  public void downloadAttachment(
      @PathVariable UUID projectId,
      @PathVariable UUID runId,
      @PathVariable UUID attachmentId,
      HttpServletResponse response)
      throws IOException {
    ExecutionAttachmentService.AttachmentBytes a =
        attachments.download(projectId, runId, attachmentId);
    response.setContentType(a.contentType() != null ? a.contentType() : "application/octet-stream");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + a.fileName() + "\"");
    response.getOutputStream().write(a.bytes());
  }

  @DeleteMapping("/{runId}/attachments/{attachmentId}")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable UUID projectId, @PathVariable UUID runId, @PathVariable UUID attachmentId) {
    attachments.delete(projectId, runId, attachmentId);
    return ResponseEntity.noContent().build();
  }
}
