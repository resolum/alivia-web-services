package com.resolum.alivia.tracking;

import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.queryservices.DeviceTelemetryQueryServiceImpl;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery;
import com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.queries.GetDeviceTelemetryQuery;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.TelemetryPage;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The diagnostics readings of a device: the same limits as the history, read from the home where the device is now. */
class DeviceTelemetryQueryServiceTest {

    private static final long DEVICE = 10L;
    private static final long HOME = 30L;
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");

    private final AssetsContextFacade facade = mock(AssetsContextFacade.class);
    private final TelemetrySampleRepository samples = mock(TelemetrySampleRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final DeviceTelemetryQueryServiceImpl service = new DeviceTelemetryQueryServiceImpl(
            new ExternalAssetsService(facade), samples, HistoryPolicy.proposed(), Clock.fixed(NOW, ZoneOffset.UTC), meters);

    @BeforeEach
    void home() {
        when(facade.fetchHouseholdOfDevice(DEVICE)).thenReturn(Optional.of(HOME));
        when(samples.findByDevice(anyLong(), anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new TelemetryPage(List.of(), 0));
    }

    private static GetDeviceTelemetryQuery query(Instant from, Instant to, Integer page, Integer size) {
        return new GetDeviceTelemetryQuery(DEVICE, from, to, page, size);
    }

    private static TelemetrySample sample(Instant at) {
        return new TelemetrySample(DEVICE, 3L, HOME, at, new EventAttributes(Map.of("rssi", -55)), null);
    }

    @Test
    void withoutParametersItIsTheLongestPeriodBackFromNowInThePageOfTheDefaultSize() {
        var view = service.handle(query(null, null, null, null));

        assertThat(view.to()).isEqualTo(NOW);
        assertThat(view.from()).isEqualTo(NOW.minus(Duration.ofDays(31)));
        assertThat(view.size()).isEqualTo(20);
        verify(samples).findByDevice(HOME, DEVICE, NOW.minus(Duration.ofDays(31)), NOW, 0, 20);
    }

    @Test
    void thePageCarriesTheTotalAndHowManyPagesThereAre() {
        when(samples.findByDevice(anyLong(), anyLong(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new TelemetryPage(List.of(sample(NOW.minusSeconds(60)), sample(NOW.minusSeconds(360))), 45));

        var view = service.handle(query(NOW.minusSeconds(3600), NOW, 2, 20));

        assertThat(view.items()).hasSize(2);
        assertThat(view.total()).isEqualTo(45);
        assertThat(view.totalPages()).isEqualTo(3);
        assertThat(view.page()).isEqualTo(2);
    }

    @Test
    void aBadPeriodOrPageIsRefusedBeforeLookingAnythingUp() {
        assertThatThrownBy(() -> service.handle(query(NOW, NOW.minusSeconds(1), null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("invalid_range"));
        assertThatThrownBy(() -> service.handle(query(NOW.minus(Duration.ofDays(32)), NOW, null, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("range_too_large"));
        assertThatThrownBy(() -> service.handle(query(null, null, -1, null)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("invalid_page"));
        assertThatThrownBy(() -> service.handle(query(null, null, null, 101)))
                .isInstanceOfSatisfying(InvalidHistoryQuery.class, e -> assertThat(e.code()).isEqualTo("invalid_page_size"));

        verifyNoInteractions(facade, samples);
    }

    @Test
    void aDeviceThatIsNotPlacedInAHomeHasNoTelemetryToShow() {
        when(facade.fetchHouseholdOfDevice(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new GetDeviceTelemetryQuery(11L, null, null, null, null)))
                .isInstanceOf(DeviceNotAccessible.class);
        verifyNoInteractions(samples);
    }

    @Test
    void ifTheReadingsCannotBeReadItIsNotAnEmptySeries() {
        when(samples.findByDevice(anyLong(), anyLong(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("MongoDB is down"));

        assertThatThrownBy(() -> service.handle(query(null, null, null, null))).isInstanceOf(TrackingStorageUnavailable.class);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "read_device_telemetry").counter().count())
                .isEqualTo(1.0);
    }
}
