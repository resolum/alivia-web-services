package com.resolum.alivia.analytics.interfaces.rest.controllers;

import com.resolum.alivia.analytics.domain.model.queries.GetAutonomyMetricsQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetBusinessOverviewQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetSalesMetricsQuery;
import com.resolum.alivia.analytics.domain.model.valueobjects.AutonomyMetrics;
import com.resolum.alivia.analytics.domain.model.valueobjects.BusinessOverview;
import com.resolum.alivia.analytics.domain.model.valueobjects.SalesMetrics;
import com.resolum.alivia.analytics.domain.services.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The indicators of the service. Periods are ISO-8601 instants in UTC: {@code from} included, {@code to} excluded;
 * without {@code to} it is now and without {@code from} the 30 days before {@code to}; at most 366 days.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Sales, business overview and autonomy metrics")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/analytics/sales")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUBSCRIPTION_MANAGER')")
    @Operation(summary = "Sales metrics of a period",
            description = "Hirings, subscriptions sold and revenue (confirmed charges, by currency), by plan and by "
                    + "month, with the previous period of the same length to compare. planId limits it to one plan. "
                    + "A period without sales answers zeros, empty lists and hasResults=false.")
    public ResponseEntity<SalesMetrics> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long planId) {
        return ResponseEntity.ok(queryService.handle(new GetSalesMetricsQuery(from, to, planId)));
    }

    @GetMapping("/analytics/overview")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Overview of the business",
            description = "Commercial indicators, subscriptions by status, installations, alerts and device status, "
                    + "with the period and computedAt. A source that cannot be read leaves its section null and is "
                    + "named in incompleteSources.")
    public ResponseEntity<BusinessOverview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(queryService.handle(new GetBusinessOverviewQuery(from, to)));
    }

    @GetMapping("/assisted-persons/{personId}/analytics/autonomy")
    @Operation(summary = "Autonomy metrics of an assisted person",
            description = "Voice commands requested, succeeded, failed and rejected, by device, each command counted "
                    + "once even when it was resent. lastUpdatedAt and mayHavePendingEvents tell whether events may "
                    + "still be waiting in the home. Counts only, never audio. Only the care network of the person.")
    public ResponseEntity<AutonomyMetrics> autonomy(
            @PathVariable Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(hidden = true) Authentication authentication) {
        return ResponseEntity.ok(queryService.handle(
                new GetAutonomyMetricsQuery(userId(authentication), personId, from, to)));
    }

    /** IAM names a user by its numeric id; an edge or anything else is refused. */
    private static Long userId(Authentication authentication) {
        var name = authentication == null ? null : authentication.getName();
        try {
            var id = name == null ? 0L : Long.parseLong(name);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // falls through to the refusal
        }
        throw new AccessDeniedException("The caller is not a user account.");
    }
}
