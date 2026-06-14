package com.platform.core.service;

import com.platform.core.domain.PlatformSetting;
import com.platform.core.domain.ScopedSetting;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.ScopedSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingResolverTest {

    private PlatformSettingRepository orgRepo;
    private ScopedSettingRepository scopedRepo;
    private SettingResolver resolver;

    private final UUID projectId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final String KEY = "ai.provider";

    @BeforeEach
    void setUp() {
        orgRepo = mock(PlatformSettingRepository.class);
        scopedRepo = mock(ScopedSettingRepository.class);
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        resolver = new SettingResolver(orgRepo, scopedRepo, projectRepo);

        when(scopedRepo.findByScopeAndScopeIdAndKey(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(orgRepo.findById(anyString())).thenReturn(Optional.empty());
    }

    private void orgDefault(String value) {
        PlatformSetting s = mock(PlatformSetting.class);
        when(s.getValue()).thenReturn(value);
        when(orgRepo.findById(KEY)).thenReturn(Optional.of(s));
    }

    private void scoped(ScopedSetting.Scope scope, UUID id, String value) {
        when(scopedRepo.findByScopeAndScopeIdAndKey(scope.name(), id, KEY))
                .thenReturn(Optional.of(new ScopedSetting(scope, id, KEY, value)));
    }

    @Test
    void orgDefault_whenNoOverrides() {
        orgDefault("claude");
        assertThat(resolver.resolve(projectId, KEY)).contains("claude");
    }

    @Test
    void projectOverridesOrgDefault() {
        orgDefault("claude");
        scoped(ScopedSetting.Scope.PROJECT, projectId, "openai");
        assertThat(resolver.resolve(projectId, KEY)).contains("openai");
    }

    @Test
    void teamOverridesProjectAndOrg() {
        orgDefault("claude");
        scoped(ScopedSetting.Scope.PROJECT, projectId, "openai");
        scoped(ScopedSetting.Scope.TEAM, teamId, "claude");
        assertThat(resolver.resolve(projectId, teamId, KEY)).contains("claude");
    }

    @Test
    void emptyEverywhere_returnsEmpty() {
        assertThat(resolver.resolve(projectId, KEY)).isEmpty();
    }

    @Test
    void resolveOrDefault_usesFallback() {
        assertThat(resolver.resolveOrDefault(projectId, KEY, "claude")).isEqualTo("claude");
    }
}
