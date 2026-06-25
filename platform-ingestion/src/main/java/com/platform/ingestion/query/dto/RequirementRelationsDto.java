package com.platform.ingestion.query.dto;

import com.platform.core.domain.PlatformRequirement;
import java.util.List;
import java.util.UUID;

/**
 * Hierarchy of a requirement: its parent (if any) and its direct children — used by the detail
 * panel to surface work-item relationships (e.g. ADO parent/child links).
 */
public record RequirementRelationsDto(Ref parent, List<Ref> children) {

  /**
   * Lightweight reference to a related requirement (enough to display + navigate + open upstream).
   */
  public record Ref(
      UUID id,
      String externalId,
      String title,
      String issueType,
      String status,
      int depth,
      String sourceUrl) {
    public static Ref from(PlatformRequirement r, String sourceUrlBase) {
      return new Ref(
          r.getId(),
          r.getExternalId(),
          r.getTitle(),
          r.getIssueType(),
          r.getStatus(),
          r.getDepth(),
          sourceUrl(sourceUrlBase, r.getExternalId()));
    }

    private static String sourceUrl(String base, String externalId) {
      if (base == null || externalId == null || !externalId.matches("\\d+")) return null;
      return base + externalId;
    }
  }
}
