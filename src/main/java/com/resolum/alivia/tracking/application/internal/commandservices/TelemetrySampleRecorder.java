package com.resolum.alivia.tracking.application.internal.commandservices;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the diagnostics readings of the devices, which are many and of little value: at most one per device every
 * {@code interval}, written in the background through a bounded queue.
 *
 * <p>Unlike an event, a sample may be lost: when the queue is full or the write fails it is dropped and counted
 * ({@code tracking.samples.dropped}), and the report that carried it is answered all the same. The queue is the owner of
 * its own threads and is not exposed as an {@link Executor} bean, which would switch off the task executor Spring Boot
 * configures for the rest of the application.</p>
 */
@Slf4j
public class TelemetrySampleRecorder {

    private final TelemetrySampleRepository samples;
    private final Executor executor;
    private final ThreadPoolExecutor ownPool;
    private final Clock clock;
    private final Duration interval;
    private final Duration retention;
    private final MeterRegistry meters;
    private final Map<Long, Instant> lastSampleOf = new ConcurrentHashMap<>();

    /**
     * @param interval  the least time between two samples of the same device
     * @param retention how long a sample is kept; {@code null} keeps it for ever (no retention is decided yet)
     * @param queueSize how many samples may wait to be written
     */
    public TelemetrySampleRecorder(TelemetrySampleRepository samples, Clock clock, Duration interval, Duration retention,
                                   int queueSize, MeterRegistry meters) {
        this(samples, clock, interval, retention, meters, newPool(queueSize), true);
    }

    /** For tests: the work runs in the executor they give. */
    public TelemetrySampleRecorder(TelemetrySampleRepository samples, Clock clock, Duration interval, Duration retention,
                                   MeterRegistry meters, Executor executor) {
        this(samples, clock, interval, retention, meters, executor, false);
    }

    private TelemetrySampleRecorder(TelemetrySampleRepository samples, Clock clock, Duration interval, Duration retention,
                                    MeterRegistry meters, Executor executor, boolean owned) {
        if (interval == null || interval.isNegative()) {
            throw new IllegalArgumentException("The interval cannot be negative.");
        }
        if (retention != null && (retention.isNegative() || retention.isZero())) {
            throw new IllegalArgumentException("The retention must be positive when it is set.");
        }
        this.samples = samples;
        this.clock = clock;
        this.interval = interval;
        this.retention = retention;
        this.meters = meters;
        this.executor = executor;
        this.ownPool = owned ? (ThreadPoolExecutor) executor : null;
    }

    private static ThreadPoolExecutor newPool(int queueSize) {
        var pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueSize),
                runnable -> {
                    var thread = new Thread(runnable, "tracking-samples");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }

    /**
     * Keeps the diagnostics of an applied state unless the device already has a sample from less than the interval ago.
     * Never throws and never waits.
     */
    public void record(DeviceState state, EventAttributes diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return;
        var now = clock.instant();
        // Claims the slot atomically so that two reports at once make one sample.
        var claimed = new boolean[1];
        lastSampleOf.compute(state.deviceId(), (device, last) -> {
            if (last == null || !last.plus(interval).isAfter(now)) {
                claimed[0] = true;
                return now;
            }
            return last;
        });
        if (!claimed[0]) return;
        var sample = new TelemetrySample(state.deviceId(), state.edgeId(), state.householdId(), state.observedAt(),
                diagnostics, retention == null ? null : now.plus(retention));
        try {
            executor.execute(() -> write(sample));
        } catch (RejectedExecutionException full) {
            drop("queue_full");
        }
    }

    private void write(TelemetrySample sample) {
        try {
            samples.save(sample);
        } catch (RuntimeException failure) {
            drop("write_failed");
            log.warn("A sample of the device {} could not be kept.", sample.deviceId(), failure);
        }
    }

    private void drop(String cause) {
        meters.counter("tracking.samples.dropped", "cause", cause).increment();
    }

    @PreDestroy
    void stop() {
        if (ownPool != null) {
            ownPool.shutdown();
            try {
                ownPool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
