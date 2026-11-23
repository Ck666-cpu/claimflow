package com.claimflow.claims.shared;

import java.util.Arrays;
import java.util.UUID;

/**
 * The caller, resolved from the {@code X-User-Id} header by {@link CurrentUserArgumentResolver}.
 * Stub authentication (ADR-9): the header is trusted; real OIDC would replace the resolver only.
 */
public record CurrentUser(UUID id, String name, Role role) {

    public boolean is(Role r) {
        return role == r;
    }

    public boolean isStaff() {
        return role == Role.OFFICER || role == Role.LEAD;
    }

    /** Throws 403 unless the caller has one of the given roles. */
    public void requireRole(Role... allowed) {
        if (Arrays.stream(allowed).noneMatch(r -> r == role)) {
            throw new ForbiddenException("This action requires role " + Arrays.toString(allowed));
        }
    }
}
