package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    boolean existsByActiveHouseholdId(String householdId);
    Optional<Subscription> findByIdempotencyKey(String key);
    Optional<Subscription> findByActiveHouseholdId(String householdId);
    Optional<Subscription> findFirstByUserIdOrderByIdDesc(Long userId);
    Optional<Subscription> findFirstByHouseholdIdOrderByIdDesc(String householdId);
    Optional<Subscription> findFirstByDraftId(String draftId);
}
