package com.claimflow.claims.metrics;

import com.claimflow.claims.metrics.MetricsDtos.ExposureReport;
import com.claimflow.claims.metrics.MetricsDtos.WorkloadReport;
import com.claimflow.claims.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, real-time metrics for staff: workload and outstanding exposure. */
@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics")
class MetricsController {

    private final MetricsService metrics;

    MetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/workload")
    @Operation(summary = "Team workload: queue, claims waiting on claimants, SLA breaches, per-officer load (officer or lead)")
    public WorkloadReport workload(CurrentUser user) {
        return metrics.workload(user);
    }

    @GetMapping("/exposure")
    @Operation(summary = "Outstanding exposure (sum of reserves on open claims) per market and currency (lead only)")
    public ExposureReport exposure(CurrentUser user) {
        return metrics.exposure(user);
    }
}
