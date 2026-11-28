package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    boolean existsByActiveHouseholdId(String householdId);
    Optional<Subscription> findByIdempotencyKey(String key);
    Optional<Subscription> findByActiveHouseholdId(String householdId);
    Optional<Subscription> findFirstByUserIdOrderByIdDesc(Long userId);
    Optional<Subscription> findFirstByHouseholdIdOrderByIdDesc(String householdId);
    Optional<Subscription> findFirstByDraftId(String draftId);

    /** Rows of (status, count). */
    @Query("select s.status, count(s) from Subscription s group by s.status")
    List<Object[]> countByStatus();
}
