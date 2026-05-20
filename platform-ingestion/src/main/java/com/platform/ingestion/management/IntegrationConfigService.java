package com.platform.ingestion.management;

import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.ingestion.management.dto.IntegrationConfigDto;
import com.platform.ingestion.management.dto.SaveIntegrationConfigRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class IntegrationConfigService {

    private final ProjectIntegrationConfigRepository configRepo;

    public IntegrationConfigService(ProjectIntegrationConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Transactional(readOnly = true)
    public List<IntegrationConfigDto> list(UUID projectId) {
        return configRepo.findByProjectId(projectId).stream()
                .map(IntegrationConfigDto::from)
                .toList();
    }

    public IntegrationConfigDto save(UUID projectId, SaveIntegrationConfigRequest req) {
        ProjectIntegrationConfig config;
        if (req.id() != null) {
            config = configRepo.findById(req.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration config not found: " + req.id()));
            if (!config.getProjectId().equals(projectId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration config not found for project: " + projectId);
            }
        } else {
            String displayName = req.displayName() != null ? req.displayName() : req.integrationType();
            String syncDirection = req.syncDirection() != null ? req.syncDirection() : "INBOUND";
            config = new ProjectIntegrationConfig(projectId, "DEFAULT", req.integrationType(), displayName, syncDirection);
        }
        if (req.displayName() != null)  config.setDisplayName(req.displayName());
        if (req.syncDirection() != null) config.setSyncDirection(req.syncDirection());
        if (req.repoType() != null)      config.setRepoType(req.repoType());

        // Merge connection params: skip "***" values so the UI can display masked
        // secrets without overwriting the real stored value on save.
        Map<String, String> merged = new java.util.HashMap<>(
                config.getConnectionParams() != null ? config.getConnectionParams() : Map.of());
        if (req.connectionParams() != null) {
            req.connectionParams().forEach((k, v) -> {
                if (!"***".equals(v)) merged.put(k, v);
            });
        }
        config.setConnectionParams(merged);
        config.setFieldMappings(req.fieldMappings() != null ? req.fieldMappings() : Map.of());
        config.setFilterConfig(req.filterConfig() != null ? req.filterConfig() : Map.of());
        config.setEnabled(req.enabled());
        return IntegrationConfigDto.from(configRepo.save(config));
    }

    public void delete(UUID projectId, UUID configId) {
        var config = configRepo.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration config not found: " + configId));
        if (!config.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration config not found for project: " + projectId);
        }
        configRepo.delete(config);
    }
}
