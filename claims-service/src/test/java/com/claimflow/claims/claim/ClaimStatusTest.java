package com.claimflow.claims.claim;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.claimflow.claims.claim.ClaimStatus.APPROVED;
import static com.claimflow.claims.claim.ClaimStatus.INFO_REQUESTED;
import static com.claimflow.claims.claim.ClaimStatus.REJECTED;
import static com.claimflow.claims.claim.ClaimStatus.SETTLED;
import static com.claimflow.claims.claim.ClaimStatus.SUBMITTED;
import static com.claimflow.claims.claim.ClaimStatus.UNDER_REVIEW;
import static com.claimflow.claims.claim.ClaimStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    /** The transition table from the spec, written out independently of the enum. */
    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED, WITHDRAWN),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            APPROVED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(ClaimStatus.class),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            WITHDRAWN, EnumSet.noneOf(ClaimStatus.class));

    @Test
    void allFortyNinePairsMatchTheTable() {
        int checked = 0;
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                boolean expected = ALLOWED.get(from).contains(to);
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
                checked++;
            }
        }
        assertThat(checked).isEqualTo(49);
    }

    @Test
    void allowedNextIsExactlyTheTable() {
        for (ClaimStatus from : ClaimStatus.values()) {
            assertThat(from.allowedNext()).as("%s", from).isEqualTo(ALLOWED.get(from));
        }
    }

    @Test
    void terminalStatesAreTerminalAndNotOpen() {
        for (ClaimStatus s : EnumSet.of(SETTLED, REJECTED, WITHDRAWN)) {
            assertThat(s.isTerminal()).as("%s terminal", s).isTrue();
            assertThat(ClaimStatus.OPEN).as("OPEN").doesNotContain(s);
        }
        for (ClaimStatus s : EnumSet.of(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, APPROVED)) {
            assertThat(s.isTerminal()).as("%s terminal", s).isFalse();
        }
    }

    @Test
    void openSetAndAssessment() {
        assertThat(ClaimStatus.OPEN).containsExactlyInAnyOrder(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, APPROVED);
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(s.isUnderAssessment())
                    .as("%s under assessment", s)
                    .isEqualTo(s == UNDER_REVIEW || s == INFO_REQUESTED);
        }
    }
}
