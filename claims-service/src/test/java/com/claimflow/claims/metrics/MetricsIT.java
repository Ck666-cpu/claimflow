package com.claimflow.claims.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Metrics against the V2 seed only: this class starts its own fresh Postgres container, so no other
 * test's claims are present. Seed ages are relative to migration time, which is test start, so they are stable.
 *
 * Kafka is not needed: these endpoints only read. Three properties keep the context from touching a broker:
 * externalization is off (no outbox-to-Kafka listener), KafkaAdmin does not create topics on startup, and the
 * outbox resubmitter waits an hour. The bootstrap address points nowhere, so an accidental Kafka call would fail loudly.
 */
@SpringBootTest(properties = {
        "spring.modulith.events.externalization.enabled=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "claimflow.outbox.resubmit-interval=PT1H"
})
@Testcontainers
class MetricsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String ALICE = "00000000-0000-0000-0000-000000000001";
    private static final String BEN = "00000000-0000-0000-0000-000000000011";
    private static final String CHITRA = "00000000-0000-0000-0000-000000000012";
    private static final String DANIEL = "00000000-0000-0000-0000-000000000021";

    @Autowired WebApplicationContext context;
    @Autowired JsonMapper jsonMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private JsonNode getAs(String path, String userId) throws Exception {
        MvcResult result = mvc.perform(get(path).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- workload

    @Test
    void workloadMatchesTheSeed() throws Exception {
        JsonNode w = getAs("/api/v1/metrics/workload", DANIEL);

        assertThat(w.get("slaDays").asInt()).isEqualTo(10);
        assertThat(w.get("generatedAt").asString()).isNotBlank();

        assertThat(w.at("/queue/unassigned").asLong()).isEqualTo(2);
        assertThat(w.at("/queue/oldestAgeDays").asInt()).isEqualTo(12);

        assertThat(w.at("/waitingOnClaimant/count").asLong()).isEqualTo(1);
        assertThat(w.at("/waitingOnClaimant/oldestWaitingDays").asInt()).isEqualTo(4);

        assertThat(w.at("/totals/open").asLong()).isEqualTo(5);
        assertThat(w.at("/totals/slaBreaches").asLong()).isEqualTo(3); // CLM-000002 (12d), -000004 (18d), -000005 (25d)

        JsonNode officers = w.get("officers");
        assertThat(officers.size()).isEqualTo(2);

        JsonNode ben = officers.get(0); // sorted by open, descending
        assertThat(ben.get("officerId").asString()).isEqualTo(BEN);
        assertThat(ben.get("name").asString()).isEqualTo("Ben Lim");
        assertThat(ben.get("open").asLong()).isEqualTo(2);
        assertThat(ben.at("/byStatus/UNDER_REVIEW").asLong()).isEqualTo(1);
        assertThat(ben.at("/byStatus/INFO_REQUESTED").asLong()).isEqualTo(0);
        assertThat(ben.at("/byStatus/APPROVED").asLong()).isEqualTo(1);
        assertThat(ben.get("slaBreaches").asLong()).isEqualTo(1);
        assertThat(ben.get("decidedLast7Days").asLong()).isEqualTo(1);
        // CLM-000005 decided 22 days after submission, CLM-000007 after 8 days
        assertThat(ben.get("avgDaysToDecisionLast30").decimalValue()).isEqualByComparingTo("15.0");

        JsonNode chitra = officers.get(1);
        assertThat(chitra.get("officerId").asString()).isEqualTo(CHITRA);
        assertThat(chitra.get("open").asLong()).isEqualTo(1);
        assertThat(chitra.at("/byStatus/INFO_REQUESTED").asLong()).isEqualTo(1);
        assertThat(chitra.at("/byStatus/UNDER_REVIEW").asLong()).isEqualTo(0);
        assertThat(chitra.at("/byStatus/APPROVED").asLong()).isEqualTo(0);
        assertThat(chitra.get("slaBreaches").asLong()).isEqualTo(1);
        assertThat(chitra.get("decidedLast7Days").asLong()).isEqualTo(0);
        // CLM-000006 decided 20 days after submission, 20 days ago
        assertThat(chitra.get("avgDaysToDecisionLast30").decimalValue()).isEqualByComparingTo("20.0");
    }

    // ---------------------------------------------------------------- exposure

    @Test
    void exposureMatchesTheSeed() throws Exception {
        JsonNode markets = getAs("/api/v1/metrics/exposure", DANIEL).get("markets");

        List<String> codes = new ArrayList<>();
        markets.forEach(m -> codes.add(m.get("marketCode").asString()));
        assertThat(codes).containsExactly("AU", "HK", "ID", "MY", "SG", "TH");

        JsonNode sg = market(markets, "SG");
        assertThat(sg.get("currency").asString()).isEqualTo("SGD");
        assertThat(sg.get("marketName").asString()).isEqualTo("Singapore");
        assertThat(sg.get("openClaims").asLong()).isEqualTo(2);
        assertThat(sg.get("totalReserve").asString()).isEqualTo("10300.00");
        assertThat(sg.at("/byType/MOTOR").asString()).isEqualTo("2800.00");
        assertThat(sg.at("/byType/PROPERTY").asString()).isEqualTo("7500.00");
        assertThat(sg.at("/byStatus/SUBMITTED").asString()).isEqualTo("2800.00");
        assertThat(sg.at("/byStatus/UNDER_REVIEW").asString()).isEqualTo("0.00");
        assertThat(sg.at("/byStatus/INFO_REQUESTED").asString()).isEqualTo("7500.00");
        assertThat(sg.at("/byStatus/APPROVED").asString()).isEqualTo("0.00");
        assertThat(sg.at("/ageing/0-7d").asString()).isEqualTo("2800.00");
        assertThat(sg.at("/ageing/8-30d").asString()).isEqualTo("7500.00");
        assertThat(sg.at("/ageing/31d+").asString()).isEqualTo("0.00");

        JsonNode my = market(markets, "MY");
        assertThat(my.get("currency").asString()).isEqualTo("MYR");
        assertThat(my.get("openClaims").asLong()).isEqualTo(2);
        assertThat(my.get("totalReserve").asString()).isEqualTo("16100.00");
        assertThat(my.at("/byStatus/SUBMITTED").asString()).isEqualTo("15000.00");
        assertThat(my.at("/byStatus/APPROVED").asString()).isEqualTo("1100.00");

        JsonNode hk = market(markets, "HK");
        assertThat(hk.get("currency").asString()).isEqualTo("HKD");
        assertThat(hk.get("openClaims").asLong()).isEqualTo(1);
        assertThat(hk.get("totalReserve").asString()).isEqualTo("5200.00");

        for (String code : List.of("AU", "TH", "ID")) { // AU settled, TH rejected, ID no claims
            JsonNode m = market(markets, code);
            assertThat(m.get("openClaims").asLong()).as(code).isEqualTo(0);
            assertThat(m.get("totalReserve").asString()).as(code).isEqualTo("0.00");
            assertThat(m.get("byType").size()).as(code + " byType keys").isEqualTo(2);
            assertThat(m.get("byStatus").size()).as(code + " byStatus keys").isEqualTo(4);
            assertThat(m.get("ageing").size()).as(code + " ageing keys").isEqualTo(3);
            m.get("ageing").forEach(v -> assertThat(v.asString()).isEqualTo("0.00"));
        }
    }

    private static JsonNode market(JsonNode markets, String code) {
        for (JsonNode m : markets) {
            if (m.get("marketCode").asString().equals(code)) {
                return m;
            }
        }
        throw new AssertionError("market " + code + " missing");
    }

    // ---------------------------------------------------------------- roles

    @Test
    void claimantIsForbiddenFromBoth() throws Exception {
        mvc.perform(get("/api/v1/metrics/workload").header("X-User-Id", ALICE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/metrics/exposure").header("X-User-Id", ALICE)).andExpect(status().isForbidden());
    }

    @Test
    void officerSeesWorkloadButNotExposure() throws Exception {
        mvc.perform(get("/api/v1/metrics/workload").header("X-User-Id", BEN)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/metrics/exposure").header("X-User-Id", BEN)).andExpect(status().isForbidden());
    }

    @Test
    void missingUserIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/metrics/workload")).andExpect(status().isUnauthorized());
    }
}
