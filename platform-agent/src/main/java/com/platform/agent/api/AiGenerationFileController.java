package com.platform.agent.api;

import com.platform.security.web.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Upload reference input files for AI test-case generation. */
@RestController
@RequestMapping("/hub/projects/{projectId}/ai/generation-files")
public class AiGenerationFileController {

  private final GenerationInputService inputService;

  public AiGenerationFileController(GenerationInputService inputService) {
    this.inputService = inputService;
  }

  @PostMapping(consumes = "multipart/form-data")
  public AiGenerationFileDto upload(
      @PathVariable UUID projectId,
      @RequestParam("file") MultipartFile file) {
    String actor = CurrentUser.username();
    return inputService.upload(projectId, file, actor);
  }
}
