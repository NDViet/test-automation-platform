package com.platform.ingestion.management.tcm;

import com.platform.core.domain.Environment;
import com.platform.core.domain.EnvironmentProperty;
import com.platform.core.repository.EnvironmentPropertyRepository;
import com.platform.core.repository.EnvironmentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EnvironmentService {

  private final EnvironmentRepository envRepo;
  private final EnvironmentPropertyRepository propRepo;

  public EnvironmentService(EnvironmentRepository envRepo, EnvironmentPropertyRepository propRepo) {
    this.envRepo = envRepo;
    this.propRepo = propRepo;
  }

  @Transactional(readOnly = true)
  public List<EnvironmentDto> list(UUID projectId) {
    return envRepo.findByProjectIdOrderByNameAsc(projectId).stream()
        .map(e -> EnvironmentDto.from(e, loadProps(e.getId())))
        .toList();
  }

  public EnvironmentDto create(UUID projectId, CreateEnvironmentRequest req) {
    Environment env = envRepo.save(new Environment(projectId, req.name(), req.description()));
    if (req.properties() != null) {
      req.properties().forEach((k, v) -> propRepo.save(new EnvironmentProperty(env.getId(), k, v)));
    }
    return EnvironmentDto.from(env, loadProps(env.getId()));
  }

  public void delete(UUID projectId, UUID envId) {
    Environment env =
        envRepo
            .findById(envId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Environment not found: " + envId));
    if (!projectId.equals(env.getProjectId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Environment not in project");
    }
    propRepo.findByEnvironmentId(envId).forEach(propRepo::delete);
    envRepo.delete(env);
  }

  private Map<String, String> loadProps(UUID envId) {
    Map<String, String> out = new LinkedHashMap<>();
    propRepo.findByEnvironmentId(envId).forEach(p -> out.put(p.getName(), p.getValue()));
    return out;
  }
}
