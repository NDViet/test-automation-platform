package com.platform.ingestion.management;

import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import com.platform.core.domain.Organization;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.ingestion.management.dto.CreateOrganizationRequest;
import com.platform.ingestion.management.dto.UpdateOrganizationRequest;
import com.platform.ingestion.query.dto.OrganizationDto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrganizationManagementService {

  private static final String LOGO_BUCKET = "platform-artifacts";

  private final OrganizationRepository orgRepo;
  private final ProjectRepository projectRepo;
  private final BlobStore blobStore;

  public OrganizationManagementService(
      OrganizationRepository orgRepo, ProjectRepository projectRepo, BlobStore blobStore) {
    this.orgRepo = orgRepo;
    this.projectRepo = projectRepo;
    this.blobStore = blobStore;
  }

  public OrganizationDto create(CreateOrganizationRequest req) {
    if (orgRepo.existsBySlug(req.slug())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use: " + req.slug());
    }
    return OrganizationDto.from(orgRepo.save(new Organization(req.name(), req.slug())));
  }

  public OrganizationDto update(UUID id, UpdateOrganizationRequest req) {
    var org =
        orgRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organization not found: " + id));
    if (req.name() != null && !req.name().isBlank()) org.setName(req.name());
    if (req.displayName() != null && !req.displayName().isBlank())
      org.setDisplayName(req.displayName());
    return OrganizationDto.from(orgRepo.save(org));
  }

  public void delete(UUID id) {
    var org =
        orgRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organization not found: " + id));
    if (projectRepo.existsByOrganizationId(org.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Organization still has projects. Delete or reassign them first.");
    }
    orgRepo.delete(org);
  }

  public OrganizationDto uploadLogo(UUID id, byte[] bytes, String contentType) {
    var org =
        orgRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organization not found: " + id));
    String ct = contentType != null ? contentType : "application/octet-stream";
    BlobRef ref = blobStore.storeBytes(LOGO_BUCKET, bytes, ct);
    org.setLogoKey(ref.key());
    org.setLogoContentType(ct);
    return OrganizationDto.from(orgRepo.save(org));
  }

  @Transactional(readOnly = true)
  public Optional<LogoResult> getLogo(UUID id) {
    var org =
        orgRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Organization not found: " + id));
    if (org.getLogoKey() == null) return Optional.empty();
    BlobRef ref = new BlobRef(LOGO_BUCKET, org.getLogoKey(), "", "", 0L);
    return blobStore
        .fetchBytes(ref)
        .map(
            bytes ->
                new LogoResult(
                    bytes,
                    org.getLogoContentType() != null
                        ? org.getLogoContentType()
                        : "application/octet-stream"));
  }
}
