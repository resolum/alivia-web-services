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
            @Value("${alivia.tracking.max-batch-size:100}") int maxBatchSize) {
        return new TrackingPolicy(maxAttributeKeys, maxAttributeBytes, maxDetail, clockTolerance, syncedAfter,
                maxBatchSize);
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
     * Writes the diagnostics readings in the background. With no {@code alivia.tracking.samples-retention} the readings
     * are not given an expiration, so none is deleted: the retention is a decision that deletes data (D-TR-6).
     */
    @Bean
    public TelemetrySampleRecorder telemetrySampleRecorder(
            TelemetrySampleRepository samples, Clock clock, MeterRegistry meters,
            @Value("${alivia.tracking.sample-interval:PT5M}") Duration interval,
            @Value("${alivia.tracking.samples-retention:}") String retention,
            @Value("${alivia.tracking.sample-queue-size:1000}") int queueSize) {
        return new TelemetrySampleRecorder(samples, clock, interval,
                retention.isBlank() ? null : Duration.parse(retention.strip()), queueSize, meters);
    }
}
