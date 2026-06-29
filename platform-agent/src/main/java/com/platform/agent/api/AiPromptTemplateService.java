package com.platform.agent.api;

import com.platform.core.domain.AiPromptTemplate;
import com.platform.core.repository.AiPromptTemplateRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Project-scoped CRUD for AI generation prompt templates plus default resolution. At most one
 * template per (project, kind) is the default; {@link #resolveDefault} falls back to a built-in
 * seed when the project has none, so generation always has a sensible prompt.
 */
@Service
public class AiPromptTemplateService {

  public static final String KIND_SYSTEM = "SYSTEM";
  public static final String KIND_USER = "USER";
  private static final Set<String> KINDS = Set.of(KIND_SYSTEM, KIND_USER);

  /** Built-in seed used when a project has no default SYSTEM template. */
  public static final String SEED_SYSTEM =
      """
      You are a QA expert that creates thorough manual test cases from product requirements.

      ## Your job
      Given a list of requirements (user stories, epics, tasks) with acceptance criteria, generate
      comprehensive manual test cases.

      ## Rules
      - Each test case must map to one requirement (use its ID as sourceRequirementId)
      - Cover happy path, edge cases, and error conditions
      - Write clear, executable steps (action + expected result per step)
      - Set priority: CRITICAL for core flows, HIGH for important features, MEDIUM for standard,
        LOW for edge cases
      - Response MUST be valid JSON — an array of test case objects\
      """;

  /** Built-in seed used when a project has no default USER template. */
  public static final String SEED_USER =
      "Generate comprehensive manual test cases for the provided requirements and context.";

  private final AiPromptTemplateRepository repo;

  public AiPromptTemplateService(AiPromptTemplateRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public List<AiPromptTemplateDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByKindAscNameAsc(projectId).stream()
        .map(AiPromptTemplateDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public AiPromptTemplateDto get(UUID projectId, UUID id) {
    return AiPromptTemplateDto.from(loadOwned(projectId, id));
  }

  @Transactional
  public AiPromptTemplateDto create(UUID projectId, AiPromptTemplateRequest req, String actor) {
    String kind = requireKind(req.kind());
    String name = requireName(req.name());
    requireBody(req.body());
    if (repo.existsByProjectIdAndKindAndName(projectId, kind, name)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A " + kind + " template named '" + name + "' already exists");
    }
    boolean isDefault = req.isDefaultOrFalse();
    if (isDefault) clearExistingDefault(projectId, kind);
    AiPromptTemplate t = new AiPromptTemplate(projectId, kind, name, req.body(), isDefault, actor);
    return AiPromptTemplateDto.from(repo.save(t));
  }

  @Transactional
  public AiPromptTemplateDto update(
      UUID projectId, UUID id, AiPromptTemplateRequest req, String actor) {
    AiPromptTemplate t = loadOwned(projectId, id);
    String name = requireName(req.name());
    requireBody(req.body());
    if (!name.equals(t.getName())
        && repo.existsByProjectIdAndKindAndName(projectId, t.getKind(), name)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "A " + t.getKind() + " template named '" + name + "' already exists");
    }
    boolean isDefault = req.isDefaultOrFalse();
    if (isDefault && !t.isDefault()) clearExistingDefault(projectId, t.getKind());
    t.update(name, req.body(), isDefault);
    return AiPromptTemplateDto.from(repo.save(t));
  }

  @Transactional
  public void delete(UUID projectId, UUID id) {
    repo.delete(loadOwned(projectId, id));
  }

  /** Resolve the default prompt body for a kind, falling back to the built-in seed. */
  @Transactional(readOnly = true)
  public String resolveDefault(UUID projectId, String kind) {
    String k = requireKind(kind);
    return repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, k)
        .map(AiPromptTemplate::getBody)
        .orElseGet(() -> KIND_SYSTEM.equals(k) ? SEED_SYSTEM : SEED_USER);
  }

  private void clearExistingDefault(UUID projectId, String kind) {
    repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, kind)
        .ifPresent(
            existing -> {
              existing.clearDefault();
              repo.save(existing);
            });
  }

  private AiPromptTemplate loadOwned(UUID projectId, UUID id) {
    AiPromptTemplate t =
        repo.findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    if (!t.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
    }
    return t;
  }

  private static String requireKind(String kind) {
    String k = kind == null ? null : kind.trim().toUpperCase();
    if (k == null || !KINDS.contains(k)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be SYSTEM or USER");
    }
    return k;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template name is required");
    }
    return name.trim();
  }

  private static void requireBody(String body) {
    if (body == null || body.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template body is required");
    }
  }
}
