package com.platform.ingestion.management;

import com.platform.core.domain.Organization;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.ingestion.management.dto.CreateOrganizationRequest;
import com.platform.ingestion.management.dto.UpdateOrganizationRequest;
import com.platform.ingestion.query.dto.OrganizationDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional
public class OrganizationManagementService {

    private final OrganizationRepository orgRepo;
    private final ProjectRepository projectRepo;

    public OrganizationManagementService(OrganizationRepository orgRepo, ProjectRepository projectRepo) {
        this.orgRepo     = orgRepo;
        this.projectRepo = projectRepo;
    }

    public OrganizationDto create(CreateOrganizationRequest req) {
        if (orgRepo.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use: " + req.slug());
        }
        return OrganizationDto.from(orgRepo.save(new Organization(req.name(), req.slug())));
    }

    public OrganizationDto update(UUID id, UpdateOrganizationRequest req) {
        var org = orgRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: " + id));
        if (req.name() != null && !req.name().isBlank()) org.setName(req.name());
        return OrganizationDto.from(orgRepo.save(org));
    }

    public void delete(UUID id) {
        var org = orgRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: " + id));
        if (projectRepo.existsByOrganizationId(org.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Organization still has projects. Delete or reassign them first.");
        }
        orgRepo.delete(org);
    }
}
