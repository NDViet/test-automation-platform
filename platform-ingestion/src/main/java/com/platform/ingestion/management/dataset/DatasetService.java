package com.platform.ingestion.management.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.TestDataset;
import com.platform.core.repository.TestDatasetRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** CRUD for reusable test-data datasets. Rows are a key/value map stored as JSON. */
@Service
@Transactional
public class DatasetService {

  private final TestDatasetRepository repo;
  private final ObjectMapper om;

  public DatasetService(TestDatasetRepository repo, ObjectMapper om) {
    this.repo = repo;
    this.om = om;
  }

  @Transactional(readOnly = true)
  public List<DatasetDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByNameAsc(projectId).stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public DatasetDto get(UUID projectId, UUID id) {
    return toDto(load(projectId, id));
  }

  public DatasetDto create(UUID projectId, DatasetForm req) {
    requireName(req.name());
    try {
      TestDataset d =
          repo.save(
              new TestDataset(projectId, req.name().trim(), req.env(), writeRows(req.rows())));
      return toDto(d);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A dataset named '" + req.name() + "' already exists for this env");
    }
  }

  public DatasetDto update(UUID projectId, UUID id, DatasetForm req) {
    requireName(req.name());
    TestDataset d = load(projectId, id);
    d.update(req.name().trim(), req.env(), writeRows(req.rows()));
    try {
      return toDto(repo.save(d));
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A dataset with that name/env exists");
    }
  }

  public void delete(UUID projectId, UUID id) {
    repo.delete(load(projectId, id));
  }

  private TestDataset load(UUID projectId, UUID id) {
    return repo.findByIdAndProjectId(id, projectId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found: " + id));
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A dataset name is required");
    }
  }

  DatasetDto toDto(TestDataset d) {
    return new DatasetDto(
        d.getId() != null ? d.getId().toString() : null,
        d.getName(),
        d.getEnv(),
        readRows(d.getRows()),
        d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
  }

  private String writeRows(Map<String, String> rows) {
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    try {
      return om.writeValueAsString(rows);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rows");
    }
  }

  @SuppressWarnings("unchecked")
  Map<String, String> readRows(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return om.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }
}
