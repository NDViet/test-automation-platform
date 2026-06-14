package com.platform.ingestion.query;

import com.platform.core.repository.OrganizationRepository;
import com.platform.ingestion.query.dto.OrganizationDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OrganizationQueryService {

    private final OrganizationRepository orgRepo;

    public OrganizationQueryService(OrganizationRepository orgRepo) {
        this.orgRepo = orgRepo;
    }

    public List<OrganizationDto> findAll() {
        return orgRepo.findAll().stream().map(OrganizationDto::from).toList();
    }

    public Optional<OrganizationDto> findBySlug(String slug) {
        return orgRepo.findBySlug(slug).map(OrganizationDto::from);
    }
}
