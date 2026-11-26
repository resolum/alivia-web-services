package com.resolum.alivia.tracking.infrastructure.configuration;

import com.resolum.alivia.tracking.domain.model.valueobjects.HistoryPolicy;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resolum.alivia.tracking.application.internal.commandservices.TelemetrySampleRecorder;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** The limits of what an edge may report, from {@code alivia.tracking.*}; the defaults are the ones of the edge contract. */
@Configuration
public class TrackingConfiguration {

    @Bean
    public TrackingPolicy trackingPolicy(
            @Value("${alivia.tracking.max-attribute-keys:20}") int maxAttributeKeys,
            @Value("${alivia.tracking.max-attribute-bytes:2048}") int maxAttributeBytes,
            @Value("${alivia.tracking.max-detail:200}") int maxDetail,
            @Value("${alivia.tracking.clock-tolerance:PT5M}") Duration clockTolerance,
            @Value("${alivia.tracking.synced-after:PT60S}") Duration syncedAfter,
            @Value("${alivia.tracking.max-batch-size:100}") int maxBatchSize,
            @Value("${alivia.tracking.help-delayed-after:PT5M}") Duration helpDelayedAfter,
            @Value("${alivia.tracking.zone:America/Lima}") String zone) {
        return new TrackingPolicy(maxAttributeKeys, maxAttributeBytes, maxDetail, clockTolerance, syncedAfter,
                maxBatchSize, helpDelayedAfter, ZoneId.of(zone.strip()));
    }

    /** The limits of the history a user may read; the defaults are the ones proposed in the plan (D-TR-9, to confirm). */
    @Bean
    public HistoryPolicy historyPolicy(
            @Value("${alivia.tracking.history-max-range:P31D}") Duration maxRange,
            @Value("${alivia.tracking.history-default-page-size:20}") int defaultPageSize,
            @Value("${alivia.tracking.history-max-page-size:100}") int maxPageSize) {
        return new HistoryPolicy(maxRange, defaultPageSize, maxPageSize);
    }

    /**
     * Writes the diagnostics readings in the background. They expire after 7 days (decision D-TR-6, 2026-11-24): the
     * free cluster would fill in weeks without it. {@code alivia.tracking.samples-retention} changes the period, and
     * {@code none} keeps the readings for ever. The events do not expire.
     */
    @Bean
    public TelemetrySampleRecorder telemetrySampleRecorder(
            TelemetrySampleRepository samples, Clock clock, MeterRegistry meters,
            @Value("${alivia.tracking.sample-interval:PT5M}") Duration interval,
            @Value("${alivia.tracking.samples-retention:P7D}") String retention,
            @Value("${alivia.tracking.sample-queue-size:1000}") int queueSize) {
        return new TelemetrySampleRecorder(samples, clock, interval,
                keepsForEver(retention) ? null : Duration.parse(retention.strip()), queueSize, meters);
    }

    /** An empty value or {@code none} means the readings are not given an expiration. */
    private static boolean keepsForEver(String retention) {
        return retention == null || retention.isBlank() || "none".equalsIgnoreCase(retention.strip());
    }
}
