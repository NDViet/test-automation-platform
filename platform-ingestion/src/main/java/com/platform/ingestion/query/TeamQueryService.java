package com.platform.ingestion.query;

import com.platform.core.repository.TeamRepository;
import com.platform.ingestion.query.dto.TeamDto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TeamQueryService {

  private final TeamRepository teamRepo;

  public TeamQueryService(TeamRepository teamRepo) {
    this.teamRepo = teamRepo;
  }

  /** Teams within a project (ADO-first: Org → Project → Team). */
  public List<TeamDto> findByProject(UUID projectId) {
    return teamRepo.findByProjectIdOrderByNameAsc(projectId).stream().map(TeamDto::from).toList();
  }
}
