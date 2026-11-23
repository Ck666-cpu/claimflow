package com.claimflow.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Claims service: the system of record for claims.
 *
 * Application modules (Spring Modulith treats each direct sub-package as a module):
 *   claim   - Claim aggregate, state machine, commands and queries, domain events
 *   metrics - workload and exposure read queries (depends on claim, never the reverse)
 *   shared  - cross-cutting bits: current-user resolution, error handling, money
 */
@SpringBootApplication
public class ClaimsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsServiceApplication.class, args);
    }
}
