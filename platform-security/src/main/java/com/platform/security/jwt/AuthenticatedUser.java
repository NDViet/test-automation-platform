package com.platform.security.jwt;

import java.util.UUID;

/**
 * The verified principal carried by a request (derived from the JWT, never a client header). Roles
 * are NOT carried here — they are resolved fresh per request so grants/revocations apply
 * immediately.
 */
public record AuthenticatedUser(UUID userId, String username, boolean superAdmin) {}
