package com.platform.ingestion.query;

import com.platform.core.repository.TeamRepository;
import com.platform.ingestion.query.dto.TeamDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TeamQueryService {

    private final TeamRepository teamRepo;

    public TeamQueryService(TeamRepository teamRepo) {
        this.teamRepo = teamRepo;
    }

    public List<TeamDto> findAll() {
        return teamRepo.findAll().stream().map(TeamDto::from).toList();
    }

    public java.util.Optional<TeamDto> findBySlug(String slug) {
        return teamRepo.findBySlug(slug).map(TeamDto::from);
    }
}
