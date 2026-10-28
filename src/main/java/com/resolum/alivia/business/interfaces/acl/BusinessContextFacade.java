package com.resolum.alivia.business.interfaces.acl;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Facade interface exposing Business bounded context operations to other bounded contexts
 * (e.g. Analytics, Assistance, Subscriptions).
 */
public interface BusinessContextFacade {

    record BusinessData(Long id, Long administratorId, Long businessProfileId, LocalDateTime createdAt) {}

    Optional<BusinessData> fetchBusinessById(Long businessId);

    boolean existsBusinessById(Long businessId);
}
