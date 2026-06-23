package com.platform.ingestion.management.release;

import com.platform.core.domain.SotRelease;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the SQL predicate that selects the requirements making up a release's scope,
 * AND-combining every mapping dimension the release sets. Requirements table must be
 * aliased {@code r}. Adds the needed bind values to the given parameter source.
 */
public final class ReleaseScope {

    private ReleaseScope() {}

    /** Returns a boolean SQL fragment over alias {@code r} (platform_requirements). */
    public static String whereClause(SotRelease rel, MapSqlParameterSource p) {
        List<String> conds = new ArrayList<>();
        if (rel.getMapIterationPath() != null) {
            conds.add("r.iteration_path = :map_iter");
            p.addValue("map_iter", rel.getMapIterationPath());
        }
        if (rel.getMapAreaPath() != null) {
            conds.add("r.area_path = :map_area");
            p.addValue("map_area", rel.getMapAreaPath());
        }
        if (rel.getMapTeamId() != null) {
            conds.add("""
                    EXISTS (SELECT 1 FROM ado_teams t WHERE t.id = :map_team
                            AND t.default_area_path IS NOT NULL
                            AND starts_with(r.area_path, t.default_area_path))""");
            p.addValue("map_team", rel.getMapTeamId());
        }
        if (rel.getMapTag() != null) {
            conds.add(":map_tag = ANY(r.labels)");
            p.addValue("map_tag", rel.getMapTag());
        }
        if (rel.getMappingField() != null && rel.getMappingValue() != null) {
            conds.add("r.raw_upstream->>:map_field = :map_fieldval");
            p.addValue("map_field", rel.getMappingField());
            p.addValue("map_fieldval", rel.getMappingValue());
        }
        return conds.isEmpty() ? "false" : String.join(" AND ", conds);
    }
}
