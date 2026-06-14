package com.platform.ingestion.management.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationSchemaSnapshot;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.repository.IntegrationSchemaSnapshotRepository;
import com.platform.ingestion.management.discovery.dto.SchemaDriftReport;
import com.platform.ingestion.management.discovery.dto.SchemaDriftReport.FieldChange;
import com.platform.ingestion.management.discovery.dto.SchemaDriftReport.TypeChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase-1 schema-drift detection: compares the live upstream work-item-type schema
 * against a captured baseline ({@link IntegrationSchemaSnapshot}) and reports removed /
 * added / type-changed fields and state-category changes. Mapped-field removals are
 * flagged (high risk). Read-only w.r.t. sync — purely detect & surface.
 */
@Service
public class SchemaDriftService {

    private static final String INTEGRATION_TYPE = "AZURE_DEVOPS_BOARDS";
    private static final TypeReference<List<FieldSnap>> FIELDS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> CATS_TYPE = new TypeReference<>() {};

    private final AdoDiscoveryService discovery;
    private final IntegrationSchemaSnapshotRepository repo;
    private final MappingRulesProvider rulesProvider;
    private final MappingSuggester suggester;
    private final ObjectMapper mapper;

    public SchemaDriftService(AdoDiscoveryService discovery, IntegrationSchemaSnapshotRepository repo,
                              MappingRulesProvider rulesProvider, MappingSuggester suggester, ObjectMapper mapper) {
        this.discovery     = discovery;
        this.repo          = repo;
        this.rulesProvider = rulesProvider;
        this.suggester     = suggester;
        this.mapper        = mapper;
    }

    /** A field as stored in a baseline snapshot. */
    public record FieldSnap(String referenceName, String name, String type, boolean custom) {}

    /** Compute drift vs the baseline; if no baseline exists yet, capture it now. */
    @Transactional
    public SchemaDriftReport report(UUID projectId, String adoProject, String type, String actor) {
        AdoDiscoveryService.TypeSchema live = discovery.typeSchema(projectId, adoProject, type);
        List<FieldSnap> liveFields = toSnaps(live);
        List<String> liveCats = liveCategories(live);

        var existing = repo.findByProjectIdAndIntegrationTypeAndAdoProjectAndWorkItemType(
                projectId, INTEGRATION_TYPE, adoProject, type);

        if (existing.isEmpty()) {
            save(projectId, adoProject, type, liveFields, liveCats, actor, null);
            return new SchemaDriftReport(type, false, true, false, nowOf(projectId, adoProject, type),
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        IntegrationSchemaSnapshot base = existing.get();
        List<FieldSnap> baseFields = parseFields(base.getFieldsJson());
        List<String> baseCats = parseCats(base.getStateCategoriesJson());

        Map<String, FieldSnap> baseByRef = byRef(baseFields);
        Map<String, FieldSnap> liveByRef = byRef(liveFields);

        Set<String> baseMapped = mappedRefs(projectId, type, baseFields, baseCats);
        Set<String> liveMapped = mappedRefs(projectId, type, liveFields, liveCats);

        List<FieldChange> removed = new ArrayList<>();
        for (FieldSnap b : baseFields) {
            if (!liveByRef.containsKey(b.referenceName())) {
                removed.add(new FieldChange(b.referenceName(), b.name(), b.type(), baseMapped.contains(b.referenceName())));
            }
        }
        List<FieldChange> added = new ArrayList<>();
        for (FieldSnap l : liveFields) {
            if (!baseByRef.containsKey(l.referenceName())) {
                added.add(new FieldChange(l.referenceName(), l.name(), l.type(), liveMapped.contains(l.referenceName())));
            }
        }
        List<TypeChange> typeChanged = new ArrayList<>();
        for (FieldSnap l : liveFields) {
            FieldSnap b = baseByRef.get(l.referenceName());
            if (b != null && !eq(b.type(), l.type())) {
                typeChanged.add(new TypeChange(l.referenceName(), l.name(), b.type(), l.type()));
            }
        }
        List<String> removedCats = baseCats.stream().filter(c -> !liveCats.contains(c)).toList();
        List<String> addedCats = liveCats.stream().filter(c -> !baseCats.contains(c)).toList();

        boolean hasDrift = !removed.isEmpty() || !added.isEmpty() || !typeChanged.isEmpty()
                || !removedCats.isEmpty() || !addedCats.isEmpty();

        return new SchemaDriftReport(type, true, false, hasDrift, base.getCapturedAt(),
                removed, added, typeChanged, removedCats, addedCats);
    }

    /** (Re)capture the baseline from the live schema = accept the current schema. */
    @Transactional
    public SchemaDriftReport captureBaseline(UUID projectId, String adoProject, String type, String actor) {
        AdoDiscoveryService.TypeSchema live = discovery.typeSchema(projectId, adoProject, type);
        save(projectId, adoProject, type, toSnaps(live), liveCategories(live), actor,
                repo.findByProjectIdAndIntegrationTypeAndAdoProjectAndWorkItemType(
                        projectId, INTEGRATION_TYPE, adoProject, type).orElse(null));
        return new SchemaDriftReport(type, true, true, false, nowOf(projectId, adoProject, type),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // ── helpers ────────────────────────────────────────────────────────────────────

    private void save(UUID projectId, String adoProject, String type, List<FieldSnap> fields,
                      List<String> cats, String actor, IntegrationSchemaSnapshot existing) {
        String fieldsJson = write(fields);
        String catsJson = write(cats);
        String fp = fingerprint(fields, cats);
        if (existing != null) {
            existing.update(fieldsJson, catsJson, fp, actor);
            repo.save(existing);
        } else {
            repo.save(new IntegrationSchemaSnapshot(projectId, INTEGRATION_TYPE, adoProject, type,
                    fieldsJson, catsJson, fp, actor));
        }
    }

    private Instant nowOf(UUID projectId, String adoProject, String type) {
        return repo.findByProjectIdAndIntegrationTypeAndAdoProjectAndWorkItemType(
                projectId, INTEGRATION_TYPE, adoProject, type)
                .map(IntegrationSchemaSnapshot::getCapturedAt).orElse(null);
    }

    private List<FieldSnap> toSnaps(AdoDiscoveryService.TypeSchema s) {
        return s.fields().stream()
                .map(f -> new FieldSnap(f.referenceName(), f.name(), f.type(), f.custom()))
                .toList();
    }

    private List<String> liveCategories(AdoDiscoveryService.TypeSchema s) {
        return s.states().stream().map(AdoDiscoveryService.StateInfo::category)
                .filter(c -> c != null && !c.isBlank())
                .distinct().toList();
    }

    /** referenceNames the resolved profile maps (fieldMap keys + severity/priority fields). */
    private Set<String> mappedRefs(UUID projectId, String type, List<FieldSnap> fields, List<String> cats) {
        MappingRules rules = rulesProvider.effectiveForProject(projectId);
        List<MappingSuggester.Field> sfields = fields.stream()
                .map(f -> new MappingSuggester.Field(f.referenceName(), f.name(), f.custom(), false))
                .toList();
        Map<String, Object> profile = suggester.suggest(rules, INTEGRATION_TYPE, type, sfields, cats, false);
        Object specObj = profile.get("spec");
        Set<String> refs = new LinkedHashSet<>();
        if (specObj instanceof Map<?, ?> spec) {
            if (spec.get("fieldMap") instanceof Map<?, ?> fm) {
                fm.keySet().forEach(k -> refs.add(String.valueOf(k)));
            }
            if (spec.get("valueMap") instanceof Map<?, ?> vm) {
                for (Object v : vm.values()) {
                    if (v instanceof Map<?, ?> rule && rule.get("by") instanceof String by) refs.add(by);
                }
            }
        }
        return refs;
    }

    private static Map<String, FieldSnap> byRef(List<FieldSnap> fields) {
        return fields.stream().collect(Collectors.toMap(FieldSnap::referenceName, f -> f, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private List<FieldSnap> parseFields(String json) {
        try { return mapper.readValue(json, FIELDS_TYPE); } catch (Exception e) { return List.of(); }
    }
    private List<String> parseCats(String json) {
        try { return mapper.readValue(json, CATS_TYPE); } catch (Exception e) { return List.of(); }
    }
    private String write(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    private static String fingerprint(List<FieldSnap> fields, List<String> cats) {
        String basis = fields.stream().map(f -> f.referenceName() + "|" + f.type()).sorted().collect(Collectors.joining(","))
                + "#" + cats.stream().sorted().collect(Collectors.joining(","));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(basis.hashCode());
        }
    }
}
