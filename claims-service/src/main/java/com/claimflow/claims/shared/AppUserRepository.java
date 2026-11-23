package com.claimflow.claims.shared;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Public: the claim module uses it to check that an assignee is an existing officer. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
