package com.resolum.alivia.subscriptions.interfaces.acl;

import java.util.Optional;

/** Anti-corruption layer of Subscriptions for the contexts that continue the guided contracting flow. */
public interface SubscriptionsContextFacade {

    /**
     * True when the configuration draft exists and its card holds a preauthorization, which is the
     * condition to book the technical evaluation.
     */
    boolean isConfigurationDraftPreauthorized(String draftId);

    /**
     * The subscription in force of a household.
     *
     * @param userId  account that contracted it
     * @param draftId configuration draft it was contracted with, or null when it was not linked
     */
    record ActiveSubscription(Long subscriptionId, Long userId, String draftId) {
    }

    /** The {@code ACTIVE} subscription of the household, if it has one. */
    Optional<ActiveSubscription> fetchActiveSubscriptionOfHousehold(String householdId);
}
