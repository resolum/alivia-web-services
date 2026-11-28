package com.resolum.alivia.analytics.application.internal.queryservices;

import com.resolum.alivia.analytics.domain.model.aggregates.Metric;
import com.resolum.alivia.analytics.domain.model.queries.GetAutonomyMetricsQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetBusinessOverviewQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetSalesMetricsQuery;
import com.resolum.alivia.analytics.domain.model.valueobjects.AnalyticsPeriod;
import com.resolum.alivia.analytics.domain.model.valueobjects.AutonomyMetrics;
import com.resolum.alivia.analytics.domain.model.valueobjects.BusinessOverview;
import com.resolum.alivia.analytics.domain.model.valueobjects.MetricKind;
import com.resolum.alivia.analytics.domain.model.valueobjects.SalesFigures;
import com.resolum.alivia.analytics.domain.model.valueobjects.SalesMetrics;
import com.resolum.alivia.analytics.domain.repositories.MetricRepository;
import com.resolum.alivia.analytics.domain.services.AnalyticsQueryService;
import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade.Sale;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Computes the indicators from the contexts that own the data, through their facades, and keeps each result as a
 * {@link Metric} in MongoDB.
 */
@Slf4j
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final SubscriptionsContextFacade subscriptions;
    private final OperationsContextFacade operations;
    private final TrackingContextFacade tracking;
    private final AssistanceContextFacade assistance;
    private final MetricRepository metrics;
    private final Clock clock;

    public AnalyticsQueryServiceImpl(SubscriptionsContextFacade subscriptions, OperationsContextFacade operations,
                                     TrackingContextFacade tracking, AssistanceContextFacade assistance,
                                     MetricRepository metrics, Clock clock) {
        this.subscriptions = subscriptions;
        this.operations = operations;
        this.tracking = tracking;
        this.assistance = assistance;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public SalesMetrics handle(GetSalesMetricsQuery query) {
        var now = clock.instant();
        var period = AnalyticsPeriod.of(query.from(), query.to(), now);
        var previous = period.previous();
        var sales = subscriptions.fetchSales(period.from(), period.to(), query.planId());
        var before = subscriptions.fetchSales(previous.from(), previous.to(), query.planId());

        var byPlan = sales.stream().collect(Collectors.groupingBy(Sale::planId, TreeMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> new SalesMetrics.PlanSales(group.getFirst().planId(), group.getFirst().planCode(),
                        group.getFirst().planName(), figures(group)))
                .toList();
        var byCycle = sales.stream()
                .collect(Collectors.groupingBy(Sale::billingCycle, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new SalesMetrics.CycleSales(e.getKey(), figures(e.getValue())))
                .toList();

        var result = new SalesMetrics(period.from(), period.to(), query.planId(), now, !sales.isEmpty(), figures(sales),
                byPlan, byCycle, new SalesMetrics.PreviousPeriod(previous.from(), previous.to(), figures(before)));
        keep(MetricKind.SALES, query.planId() == null ? "all" : "plan:" + query.planId(), period, now, result, List.of());
        return result;
    }

    @Override
    public BusinessOverview handle(GetBusinessOverviewQuery query) {
        var now = clock.instant();
        var period = AnalyticsPeriod.of(query.from(), query.to(), now);
        var incomplete = new ArrayList<String>();

        var commercial = fromSource("sales", incomplete,
                () -> figures(subscriptions.fetchSales(period.from(), period.to(), null)));
        var byStatus = fromSource("subscriptions", incomplete, subscriptions::countSubscriptionsByStatus);
        var installations = fromSource("installations", incomplete, () -> {
            var homes = operations.fetchInstalledHomes(null);
            return new BusinessOverview.Installations(homes.size(),
                    homes.stream().filter(h -> period.contains(h.installedAt())).count());
        });
        var alerts = fromSource("alerts", incomplete, () -> tracking.countAlertsBySeverity(period.from(), period.to()));
        var devices = fromSource("devices", incomplete, tracking::countDevicesByConnectionStatus);

        var result = new BusinessOverview(period.from(), period.to(), now, commercial, byStatus, installations, alerts,
                devices, List.copyOf(incomplete));
        keep(MetricKind.OVERVIEW, "all", period, now, result, incomplete);
        return result;
    }

    @Override
    public AutonomyMetrics handle(GetAutonomyMetricsQuery query) {
        // The period is checked before anything is read.
        var now = clock.instant();
        var period = AnalyticsPeriod.of(query.from(), query.to(), now);
        // Same answer for a person who does not exist and one the caller is not linked to: nothing is revealed.
        if (query.personId() == null || !assistance.canReadAssistedPerson(query.personId(), query.userId())) {
            throw new AccessDeniedException("The caller is not linked to the assisted person.");
        }
        var household = assistance.fetchHouseholdIdOfAssistedPerson(query.personId());
        var result = household
                .map(id -> tracking.summarizeAutonomy(id, null, period.from(), period.to()))
                .map(s -> new AutonomyMetrics(query.personId(), period.from(), period.to(), now, s.total(),
                        s.byDevice(), s.withoutDevice(), s.edgeLink(), s.lastUpdatedAt(), s.mayHavePendingEvents()))
                .orElseGet(() -> AutonomyMetrics.withoutHome(query.personId(), period, now));
        keep(MetricKind.AUTONOMY, "person:" + query.personId(), period, now, result, List.of());
        return result;
    }

    /** Every request is a hiring; only the captured ones are sold and bring revenue. */
    static SalesFigures figures(List<Sale> sales) {
        if (sales.isEmpty()) return SalesFigures.NONE;
        Map<String, BigDecimal> revenue = sales.stream().filter(Sale::paid).collect(Collectors.groupingBy(
                Sale::currency, TreeMap::new, Collectors.reducing(BigDecimal.ZERO, Sale::amount, BigDecimal::add)));
        return new SalesFigures(sales.size(), sales.stream().filter(Sale::paid).count(), revenue);
    }

    /** A source that fails leaves its section empty and is named, so the overview is answered with the rest. */
    private static <T> T fromSource(String name, List<String> incomplete, Supplier<T> source) {
        try {
            return source.get();
        } catch (RuntimeException unavailable) {
            log.warn("Analytics overview without {}: {}", name, unavailable.toString());
            incomplete.add(name);
            return null;
        }
    }

    /** The answer does not depend on the copy: a failed write is logged and the indicators are still answered. */
    private void keep(MetricKind kind, String scope, AnalyticsPeriod period, Instant at, Object values,
                      List<String> incomplete) {
        try {
            metrics.save(new Metric(kind, scope, period.from(), period.to(), at, values, incomplete));
        } catch (RuntimeException failure) {
            log.warn("Analytics metric {} {} was not kept: {}", kind, scope, failure.toString());
        }
    }
}
