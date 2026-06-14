package com.platform.ingestion.management.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MappingSuggesterTest {

    private final ObjectMapper om = new ObjectMapper();
    private final MappingSuggester s = new MappingSuggester();
    // The bundled default ruleset (mapping-rules.json) drives the suggester.
    private final MappingRules defaults = MappingRulesProvider.loadDefault(om);

    @Test
    void routesLanesFromTypeNames() {
        assertThat(s.suggestLane(defaults, "Acme Bug").lane()).isEqualTo("DEFECT");
        // "Issue"-type items are NOT defects (no defect field) — they route to the requirement lane.
        assertThat(s.suggestLane(defaults, "Acme Issue").lane()).isEqualTo("REQUIREMENT");
        assertThat(s.suggestLane(defaults, "Acme Team Epic")).isEqualTo(new MappingSuggester.Lane("REQUIREMENT", "EPIC"));
        assertThat(s.suggestLane(defaults, "Acme Capability").issueType()).isEqualTo("CAPABILITY");
        assertThat(s.suggestLane(defaults, "Acme Feature or Enabler").issueType()).isEqualTo("ENABLER");
        assertThat(s.suggestLane(defaults, "Acme PRQ").issueType()).isEqualTo("REQUIREMENT");
        assertThat(s.suggestLane(defaults, "Test Case").lane()).isEqualTo("IGNORE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsDefectProfileForCustomBug() {
        var fields = List.of(
                new MappingSuggester.Field("System.Title", "Title", false, true),
                new MappingSuggester.Field("System.Description", "Description", false, false),
                new MappingSuggester.Field("Microsoft.VSTS.Common.Severity", "Severity", false, false),
                new MappingSuggester.Field("Microsoft.VSTS.Common.Priority", "Priority", false, false),
                new MappingSuggester.Field("Acme.IssueOnProd", "Acme Issue on production", true, true),
                new MappingSuggester.Field("Acme.EscapedType", "Acme Escaped Type", true, true),
                new MappingSuggester.Field("Acme.AffectedVersion", "Acme Affected versions", true, false),
                new MappingSuggester.Field("Acme.JiraId", "Acme Jira ID", true, false));

        Map<String, Object> p = s.suggest(defaults, "AZURE_DEVOPS_BOARDS", "Acme Bug", fields,
                List.of("Proposed", "InProgress", "Completed", "Removed"), true);

        assertThat(p.get("apiVersion")).isEqualTo("quality.platform/v1");
        Map<String, Object> spec = (Map<String, Object>) p.get("spec");
        assertThat(spec.get("lane")).isEqualTo("DEFECT");

        Map<String, String> fieldMap = (Map<String, String>) spec.get("fieldMap");
        assertThat(fieldMap).containsEntry("System.Title", "title")
                .containsEntry("Acme.AffectedVersion", "affectedVersion")
                .containsEntry("Acme.IssueOnProd", "onProduction")
                .containsEntry("Acme.EscapedType", "escapedType")
                .containsEntry("Acme.JiraId", "externalRefs.jira");

        Map<String, Object> valueMap = (Map<String, Object>) spec.get("valueMap");
        Map<String, Object> status = (Map<String, Object>) valueMap.get("status");
        assertThat(status.get("by")).isEqualTo("stateCategory");
        assertThat((Map<String, String>) status.get("map")).containsEntry("InProgress", "IN_PROGRESS");
        assertThat(status).containsKey("overrides");
        assertThat(valueMap).containsKey("severity");

        assertThat(spec).containsKey("formulas");
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsRequirementProfileWithParent() {
        var fields = List.of(
                new MappingSuggester.Field("System.Title", "Title", false, true),
                new MappingSuggester.Field("System.Parent", "Parent", false, false));
        Map<String, Object> p = s.suggest(defaults, "AZURE_DEVOPS_BOARDS", "Acme PRQ", fields,
                List.of("Proposed", "InProgress", "Completed"), false);
        Map<String, Object> spec = (Map<String, Object>) p.get("spec");
        assertThat(spec.get("lane")).isEqualTo("REQUIREMENT");
        assertThat(spec.get("issueType")).isEqualTo("REQUIREMENT");
        assertThat((Map<String, String>) spec.get("fieldMap")).containsEntry("System.Parent", "parentExternalId");
        assertThat(spec).doesNotContainKey("formulas"); // requirement lane → no defect formulas
    }

    @Test
    void rulesAreConfigDriven_customRulesetChangesBehavior() throws Exception {
        // A custom org with no "bug"/"issue" wording — a "Ticket" type is its defect lane.
        String customJson = """
            {
              "defaultLane": "REQUIREMENT", "defaultIssueType": "STORY",
              "nonTracked": [],
              "laneRules": [ { "keywords": ["ticket", "incident"], "lane": "DEFECT" } ],
              "standardFields": [ { "referenceName": "System.Title", "target": "title" } ],
              "fieldHeuristics": [ { "keywords": ["component"], "target": "component" } ],
              "statusCategoryMap": { "Proposed": "OPEN" }
            }
            """;
        MappingRules custom = om.readValue(customJson, MappingRules.class);

        // "Bug" is no longer a defect under the custom ruleset; "Support Ticket" is.
        assertThat(s.suggestLane(custom, "Bug").lane()).isEqualTo("REQUIREMENT");
        assertThat(s.suggestLane(custom, "Support Ticket").lane()).isEqualTo("DEFECT");
    }
}
