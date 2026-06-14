package com.platform.ai.api;

import com.platform.core.domain.ScopedSetting;
import com.platform.core.service.SettingResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-team / per-project AI settings overrides on top of the global
 * {@code /api/v1/ai/settings} (ORG) controller.
 *
 * <p>Resolution precedence (handled by {@link SettingResolver}): PROJECT → TEAM → ORG.
 * Lets a project pin a different provider/model/enable flag without changing the
 * organization default.</p>
 */
@RestController
@RequestMapping("/api/v1/ai/settings/scoped")
public class ScopedAiSettingsController {

    /** AI keys exposed for per-scope override. */
    private static final String[] AI_KEYS = {"ai.enabled", "ai.realtime.enabled", "ai.provider", "ai.model"};

    private final SettingResolver settingResolver;

    public ScopedAiSettingsController(SettingResolver settingResolver) {
        this.settingResolver = settingResolver;
    }

    /** The effective (merged) AI settings for a project. */
    @GetMapping("/effective")
    public Map<String, String> effective(@RequestParam UUID projectId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : AI_KEYS) {
            out.put(key, settingResolver.resolve(projectId, key).orElse(null));
        }
        return out;
    }

    /** Upserts a single override at TEAM or PROJECT scope. */
    @PutMapping("/{scope}/{scopeId}")
    public ResponseEntity<Void> set(@PathVariable String scope,
                                    @PathVariable UUID scopeId,
                                    @RequestBody SettingUpdate body) {
        ScopedSetting.Scope s;
        try {
            s = ScopedSetting.Scope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null || body.key() == null || body.key().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        settingResolver.set(s, scopeId, body.key(), body.value());
        return ResponseEntity.noContent().build();
    }

    public record SettingUpdate(String key, String value) {}
}
