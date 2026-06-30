package com.platform.security.authz;

/**
 * Coarse-grained capabilities mapped to the minimum {@link Tier} required. One enum is the single
 * source of truth for the permission matrix; controllers reference these, never raw roles.
 */
public enum Capability {
  /** Read results, dashboards, analyses, executions, reports. */
  VIEW_RESULTS(Tier.VIEW),
  /** CRUD quality/business features: test cases, suites, runs, requirements ops, generation, agents. */
  OPERATE_QUALITY(Tier.OPERATE),
  /** Project config: integrations, credentials, GitHub config, mapping rules, in-project role grants. */
  MANAGE_PROJECT(Tier.ADMIN_PROJECT),
  /** Org config: create/delete projects, org settings, org-scope role grants. */
  MANAGE_ORG(Tier.ADMIN_ORG),
  /** Platform user administration. */
  MANAGE_PLATFORM(Tier.SUPER),
  /** AI / LiteLLM gateway settings (platform-wide). */
  MANAGE_AI_GATEWAY(Tier.SUPER),
  /** ADO structure import / platform onboarding (provisioning orgs/projects). */
  IMPORT_ADO_STRUCTURE(Tier.SUPER);

  private final Tier minTier;

  Capability(Tier minTier) {
    this.minTier = minTier;
  }

  public Tier minTier() {
    return minTier;
  }
}
