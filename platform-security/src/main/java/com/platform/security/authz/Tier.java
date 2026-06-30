package com.platform.security.authz;

/**
 * Capability tiers, in increasing privilege. A user's tier for a scope is the max their roles grant;
 * a capability requires a minimum tier. Ordinal order is the privilege order — do not reorder.
 */
public enum Tier {
  VIEW,
  OPERATE,
  ADMIN_PROJECT,
  ADMIN_ORG,
  SUPER;

  /** True if this tier meets or exceeds {@code required}. */
  public boolean satisfies(Tier required) {
    return this.ordinal() >= required.ordinal();
  }
}
