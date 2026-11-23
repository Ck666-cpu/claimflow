package com.claimflow.claims;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build if one module reaches into another module's internals
 * or if module dependencies form a cycle. Runs without Docker.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ClaimsServiceApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
