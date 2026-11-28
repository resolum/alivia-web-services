package com.resolum.alivia.analytics;

import com.resolum.alivia.analytics.application.internal.queryservices.AnalyticsQueryServiceImpl;
import com.resolum.alivia.analytics.domain.model.aggregates.Metric;
import com.resolum.alivia.analytics.domain.repositories.MetricRepository;
import com.resolum.alivia.analytics.interfaces.rest.controllers.AnalyticsController;
import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade.Sale;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.AutonomyCounts;
import com.resolum.alivia.tracking.interfaces.acl.TrackingContextFacade.AutonomySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The analytics endpoints with the real service over doubles of the facades they read (TS-31, TS-32, TS-33). */
class AnalyticsControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-27T00:00:00Z");
    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("7", null, List.of());

    private final SubscriptionsContextFacade subscriptions = mock(SubscriptionsContextFacade.class);
    private final OperationsContextFacade operations = mock(OperationsContextFacade.class);
    private final TrackingContextFacade tracking = mock(TrackingContextFacade.class);
    private final AssistanceContextFacade assistance = mock(AssistanceContextFacade.class);
    private final MetricRepository metrics = mock(MetricRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new AnalyticsQueryServiceImpl(subscriptions, operations, tracking, assistance, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static Sale sale(String draft, long plan, String cycle, String amount, String at, boolean paid) {
        return new Sale(draft, plan, "P" + plan, "Plan " + plan, cycle, new BigDecimal(amount), "PEN", Instant.parse(at),
                paid);
    }

    @Test
    void hiringsSalesAndRevenueAreCountedByPlanAndBillingCycleWithThePreviousPeriod() throws Exception {
        when(subscriptions.fetchSales(FROM, TO, null)).thenReturn(List.of(
                sale("d1", 1, "MONTHLY", "100.00", "2026-11-02T10:00:00Z", true),
                sale("d2", 2, "ANNUAL", "250.00", "2026-11-03T10:00:00Z", true),
                sale("d3", 1, "MONTHLY", "100.00", "2026-11-04T10:00:00Z", false)));
        when(subscriptions.fetchSales(Instant.parse("2026-10-06T00:00:00Z"), FROM, null))
                .thenReturn(List.of(sale("d0", 1, "MONTHLY", "100.00", "2026-10-07T10:00:00Z", true)));

        mvc.perform(get("/api/v1/analytics/sales").param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResults").value(true))
                .andExpect(jsonPath("$.total.hirings").value(3))
                .andExpect(jsonPath("$.total.subscriptionsSold").value(2))
                .andExpect(jsonPath("$.total.revenue.PEN").value(350.00))
                .andExpect(jsonPath("$.byPlan[0].planId").value(1))
                .andExpect(jsonPath("$.byPlan[0].figures.hirings").value(2))
                .andExpect(jsonPath("$.byPlan[0].figures.subscriptionsSold").value(1))
                .andExpect(jsonPath("$.byPlan[1].figures.revenue.PEN").value(250.00))
                .andExpect(jsonPath("$.byBillingCycle[0].billingCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.byBillingCycle[1].figures.hirings").value(2))
                .andExpect(jsonPath("$.previous.figures.hirings").value(1))
                .andExpect(jsonPath("$.computedAt").exists());
        verify(metrics).save(any(Metric.class));
    }

    @Test
    void aPlanFilterIsPassedToTheSourceAndAPeriodWithoutSalesAnswersZeros() throws Exception {
        when(subscriptions.fetchSales(any(), any(), eq(9L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/analytics/sales").param("from", FROM.toString()).param("to", TO.toString())
                        .param("planId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(9))
                .andExpect(jsonPath("$.hasResults").value(false))
                .andExpect(jsonPath("$.total.hirings").value(0))
                .andExpect(jsonPath("$.total.revenue").isEmpty())
                .andExpect(jsonPath("$.byPlan").isEmpty())
                .andExpect(jsonPath("$.byBillingCycle").isEmpty());
        verify(subscriptions, never()).fetchSales(any(), any(), isNull());
    }

    @Test
    void aPeriodWhoseStartIsAfterItsEndIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/analytics/sales").param("from", TO.toString()).param("to", FROM.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOverviewNamesTheSourceThatFailedAndAnswersTheRest() throws Exception {
        when(subscriptions.fetchSales(FROM, TO, null)).thenReturn(List.of(sale("d1", 1, "MONTHLY", "80.00", "2026-11-02T10:00:00Z", true)));
        when(subscriptions.countSubscriptionsByStatus()).thenReturn(Map.of("ACTIVE", 4L));
        when(operations.fetchInstalledHomes(null)).thenReturn(List.of(
                new OperationsContextFacade.InstalledHome("d1", null, null, null, Instant.parse("2026-11-05T00:00:00Z"),
                        null, List.of(), List.of()),
                new OperationsContextFacade.InstalledHome("d2", null, null, null, Instant.parse("2026-09-05T00:00:00Z"),
                        null, List.of(), List.of())));
        when(tracking.countAlertsBySeverity(FROM, TO)).thenThrow(new IllegalStateException("mongo down"));
        when(tracking.countDevicesByConnectionStatus()).thenReturn(Map.of("CONNECTED", 3L));

        mvc.perform(get("/api/v1/analytics/overview").param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(FROM.toString()))
                .andExpect(jsonPath("$.computedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.commercial.hirings").value(1))
                .andExpect(jsonPath("$.subscriptionsByStatus.ACTIVE").value(4))
                .andExpect(jsonPath("$.installations.total").value(2))
                .andExpect(jsonPath("$.installations.inPeriod").value(1))
                .andExpect(jsonPath("$.alertsBySeverity").doesNotExist())
                .andExpect(jsonPath("$.devicesByConnectionStatus.CONNECTED").value(3))
                .andExpect(jsonPath("$.incompleteSources[0]").value("alerts"));
    }

    @Test
    void autonomyIsAnsweredForTheCareNetworkFromTheTrackingSummary() throws Exception {
        when(assistance.canReadAssistedPerson(5L, 7L)).thenReturn(true);
        when(assistance.fetchHouseholdIdOfAssistedPerson(5L)).thenReturn(Optional.of(30L));
        var counts = new AutonomyCounts(3, 2, 1, 0, 0, 0, 0, 1);
        var last = Instant.parse("2026-11-26T23:00:00Z");
        when(tracking.summarizeAutonomy(30L, null, FROM, TO)).thenReturn(new AutonomySummary(30L, null, FROM, TO, counts,
                List.of(new TrackingContextFacade.DeviceAutonomy(10L, counts)),
                new AutonomyCounts(0, 0, 0, 0, 0, 0, 0, 0), "OFFLINE", last, true));

        mvc.perform(get("/api/v1/assisted-persons/5/analytics/autonomy").param("from", FROM.toString())
                        .param("to", TO.toString()).principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(5))
                .andExpect(jsonPath("$.total.requested").value(3))
                .andExpect(jsonPath("$.total.succeeded").value(2))
                .andExpect(jsonPath("$.total.failed").value(1))
                .andExpect(jsonPath("$.byDevice[0].deviceId").value(10))
                .andExpect(jsonPath("$.lastUpdatedAt").value(last.toString()))
                .andExpect(jsonPath("$.mayHavePendingEvents").value(true));

        var kept = ArgumentCaptor.forClass(Metric.class);
        verify(metrics).save(kept.capture());
        assertThat(kept.getValue().scope()).isEqualTo("person:5");
    }

    @Test
    void aCaregiverWhoIsNotLinkedGetsForbiddenAndNoTelemetryIsRead() throws Exception {
        when(assistance.canReadAssistedPerson(5L, 7L)).thenReturn(false);

        mvc.perform(get("/api/v1/assisted-persons/5/analytics/autonomy").principal(USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        verify(tracking, never()).summarizeAutonomy(any(), any(), any(), any());
    }

    @Test
    void aMetricThatCannotBeKeptDoesNotFailTheAnswer() throws Exception {
        when(subscriptions.fetchSales(any(), any(), any())).thenReturn(List.of());
        doThrow(new IllegalStateException("mongo down")).when(metrics).save(any());

        mvc.perform(get("/api/v1/analytics/sales")).andExpect(status().isOk());
    }

    @Test
    void salesNeedCommercialPermissionsAndTheOverviewAnAdministrator() throws Exception {
        var sales = AnalyticsController.class.getMethod("sales", Instant.class, Instant.class, Long.class);
        var overview = AnalyticsController.class.getMethod("overview", Instant.class, Instant.class);

        assertThat(sales.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMINISTRATOR', 'SUBSCRIPTION_MANAGER')");
        assertThat(overview.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMINISTRATOR')");
    }
}
