package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.commandservices.TelemetrySampleRecorder;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The sampling of diagnostics: one per device per interval, never at the cost of the report that carried it. */
class TelemetrySampleRecorderTest {

    private static final Instant T0 = Instant.parse("2026-11-24T15:00:00Z");

    /** A clock the test moves. */
    private static final class MovingClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(T0);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private final TelemetrySampleRepository repository = mock(TelemetrySampleRepository.class);
    private final MovingClock clock = new MovingClock();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private static DeviceState state(long deviceId) {
        return new DeviceState(deviceId, 3L, 300L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", 80, T0, T0,
                T0, List.of());
    }

    private static EventAttributes diagnostics() {
        return EventAttributes.of(Map.of("rssi", -55), TrackingPolicy.defaults());
    }

    private TelemetrySampleRecorder recorder(Duration retention) {
        return new TelemetrySampleRecorder(repository, clock, Duration.ofMinutes(5), retention, meters, Runnable::run);
    }

    @Test
    void aDeviceIsSampledOncePerIntervalAndAgainWhenItHasPassed() {
        var recorder = recorder(null);

        recorder.record(state(10L), diagnostics());
        clock.now.set(T0.plus(Duration.ofMinutes(4)));
        recorder.record(state(10L), diagnostics());
        recorder.record(state(11L), diagnostics());
        verify(repository, times(2)).save(any());

        clock.now.set(T0.plus(Duration.ofMinutes(5)));
        recorder.record(state(10L), diagnostics());
        verify(repository, times(3)).save(any());
    }

    @Test
    void nothingIsKeptWhenThereAreNoDiagnostics() {
        var recorder = recorder(null);

        recorder.record(state(10L), EventAttributes.empty());
        recorder.record(state(10L), null);

        verify(repository, never()).save(any());
    }

    @Test
    void theSampleExpiresOnlyWhenARetentionIsConfigured() {
        var withoutRetention = new AtomicReference<com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample>();
        var withRetention = new AtomicReference<com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample>();
        var forever = mock(TelemetrySampleRepository.class);
        org.mockito.Mockito.doAnswer(call -> {
            withoutRetention.set(call.getArgument(0));
            return null;
        }).when(forever).save(any());
        org.mockito.Mockito.doAnswer(call -> {
            withRetention.set(call.getArgument(0));
            return null;
        }).when(repository).save(any());

        new TelemetrySampleRecorder(forever, clock, Duration.ofMinutes(5), null, meters, Runnable::run)
                .record(state(10L), diagnostics());
        recorder(Duration.ofDays(30)).record(state(10L), diagnostics());

        assertThat(withoutRetention.get().expiresAt()).isNull();
        assertThat(withRetention.get().expiresAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
    }

    @Test
    void aFailureToWriteIsCountedAndNeverReachesTheCaller() {
        doThrow(new IllegalStateException("MongoDB is down")).when(repository).save(any());

        recorder(null).record(state(10L), diagnostics());

        assertThat(meters.get("tracking.samples.dropped").tag("cause", "write_failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void anInvalidConfigurationIsRefused() {
        assertThatThrownBy(() -> new TelemetrySampleRecorder(repository, clock, Duration.ofMinutes(-1), null, meters,
                Runnable::run)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TelemetrySampleRecorder(repository, clock, Duration.ofMinutes(5), Duration.ZERO,
                meters, Runnable::run)).isInstanceOf(IllegalArgumentException.class);
    }
}
