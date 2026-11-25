package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.queryservices.DeviceEventsQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceEventsQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The history of a device: the limits of what may be asked, the home it is read from and what a failure means. */
class DeviceEventsQueryServiceTest {

    private static final long USER = 7L;
    private static final long DEVICE = 10L;
    private static final long HOME = 30L;
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final DeviceEventRepository events = mock(DeviceEventRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final DeviceEventsQueryServiceImpl service = new DeviceEventsQueryServiceImpl(access, events,
            HistoryPolicy.proposed(), Clock.fixed(NOW, ZoneOffset.UTC), meters);

    @BeforeEach
    void home() {
        when(access.requireReadableHouseholdOfDevice(USER, DEVICE)).thenReturn(HOME);
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new EventPage(List.of(), 0));
    }

    private static GetDeviceEventsQuery query(Instant from, Instant to, String type, Integer page, Integer size) {
        return new GetDeviceEventsQuery(USER, DEVICE, from, to, type, page, size);
    }

    private static DeviceEvent event(String id) {
        var context = new ReportingContext(3L, 30L, HOME, Set.of(DEVICE));
        return DeviceEvent.fromEdge(context, new ReportedEvent(id, "DEVICE_STATE_CHANGED", NOW.minusSeconds(10), DEVICE,
                null, null, null, null, null, null, null), NOW, false, TrackingPolicy.defaults());
    }

    @Test
    void withoutParametersItIsTheLongestPeriodBackFromNowInThePageOfTheDefaultSize() {
        var view = service.handle(query(null, null, null, null, null));

        assertThat(view.to()).isEqualTo(NOW);
        assertThat(view.from()).isEqualTo(NOW.minus(Duration.ofDays(31)));
        assertThat(view.page()).isZero();
        assertThat(view.size()).isEqualTo(20);
        verify(events).findByDevice(HOME, DEVICE, NOW.minus(Duration.ofDays(31)), NOW, null, 0, 20);
    }

    @Test
    void theHistoryIsReadFromTheHomeWhereTheDeviceIsPlacedNow() {
        service.handle(query(null, null, null, null, null));

        verify(events).findByDevice(eq(HOME), eq(DEVICE), any(), any(), isNull(), eq(0), eq(20));
    }

    @Test
    void thePageCarriesTheTotalAndHowManyPagesThereAre() {
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new EventPage(List.of(event("a"), event("b")), 45));

        var view = service.handle(query(NOW.minusSeconds(3600), NOW, null, 1, 20));

        assertThat(view.items()).hasSize(2);
        assertThat(view.total()).isEqualTo(45);
        assertThat(view.totalPages()).isEqualTo(3);
        assertThat(view.page()).isEqualTo(1);
    }

    @Test
    void aTypeIsReadInAnyCaseAndPassedToTheSearch() {
        service.handle(query(NOW.minusSeconds(60), NOW, " low_battery ", null, null));

        verify(events).findByDevice(HOME, DEVICE, NOW.minusSeconds(60), NOW, EventType.LOW_BATTERY, 0, 20);
    }

    @ParameterizedTest
    @CsvSource({"0,20,true", "0,100,true", "0,1,true", "5,100,true", "-1,20,false", "0,0,false", "0,101,false", "0,-3,false"})
    void thePageAndTheSizeStayWithinTheLimits(int page, int size, boolean valid) {
        if (valid) {
            assertThat(service.handle(query(null, null, null, page, size)).size()).isEqualTo(size);
        } else {
            assertThatThrownBy(() -> service.handle(query(null, null, null, page, size)))
                    .isInstanceOfSatisfying(InvalidHistoryQuery.class, e ->
                            assertThat(e.code()).isEqualTo(page < 0 ? "invalid_page" : "invalid_page_size"));
        }
    }

    @Test
    void thePeriodCannotStartAfterItEndsButMayBeEmpty() {
        assertThatThrownBy(() -> service.handle(query(NOW, NOW.minusSeconds(1), null, null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("invalid_range"));

        assertThat(service.handle(query(NOW, NOW, null, null, null)).items()).isEmpty();
    }

    @Test
    void thePeriodCannotBeLongerThanTheLimitButTheLimitItselfIsAllowed() {
        var from = NOW.minus(Duration.ofDays(31));

        assertThat(service.handle(query(from, NOW, null, null, null)).from()).isEqualTo(from);
        assertThatThrownBy(() -> service.handle(query(from.minusSeconds(1), NOW, null, null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("range_too_large"));
    }

    @Test
    void aStartWithoutEndRunsUntilNowAndAnEndWithoutStartGoesBackTheLongestPeriod() {
        var to = Instant.parse("2026-11-01T00:00:00Z");

        assertThat(service.handle(query(NOW.minusSeconds(100), null, null, null, null)).to()).isEqualTo(NOW);
        assertThat(service.handle(query(null, to, null, null, null)).from()).isEqualTo(to.minus(Duration.ofDays(31)));
        assertThatThrownBy(() -> service.handle(query(NOW.minus(Duration.ofDays(40)), null, null, null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("range_too_large"));
    }

    @Test
    void anUnknownTypeIsRefused() {
        assertThatThrownBy(() -> service.handle(query(null, null, "EXPLODED", null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("invalid_type"));
    }

    @Test
    void aBlankTypeMeansEveryType() {
        service.handle(query(null, null, "  ", null, null));

        verify(events).findByDevice(eq(HOME), eq(DEVICE), any(), any(), isNull(), anyInt(), anyInt());
    }

    @Test
    void whoMayNotReadTheDeviceReadsNothing() {
        when(access.requireReadableHouseholdOfDevice(8L, DEVICE)).thenThrow(new DeviceNotAccessible(DEVICE));

        assertThatThrownBy(() -> service.handle(new GetDeviceEventsQuery(8L, DEVICE, null, null, null, null, null)))
                .isInstanceOf(DeviceNotAccessible.class);
        verifyNoInteractions(events);
    }

    @Test
    void aBadRequestIsRefusedBeforeLookingAnythingUp() {
        assertThatThrownBy(() -> service.handle(query(NOW, NOW.minusSeconds(1), null, null, null)))
                .isInstanceOf(InvalidHistoryQuery.class);

        verify(access, never()).requireReadableHouseholdOfDevice(anyLong(), anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void ifTheEventsCannotBeReadItIsNotAnEmptyHistory() {
        when(events.findByDevice(anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("MongoDB is down"));

        assertThatThrownBy(() -> service.handle(query(null, null, null, null, null)))
                .isInstanceOf(TrackingStorageUnavailable.class);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "read_device_events").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void theLimitsMustMakeSense() {
        assertThatThrownBy(() -> new HistoryPolicy(Duration.ZERO, 20, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoryPolicy(Duration.ofDays(1), 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoryPolicy(Duration.ofDays(1), 50, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
