package com.resolum.alivia.tracking.infrastructure.configuration;

import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            @Value("${alivia.tracking.synced-after:PT60S}") Duration syncedAfter) {
        return new TrackingPolicy(maxAttributeKeys, maxAttributeBytes, maxDetail, clockTolerance, syncedAfter);
    }
}
