package com.resolum.alivia.subscriptions.interfaces.acl;

import java.util.Map;
import java.util.Optional;

/** Anti-corruption layer of Subscriptions for the contexts that continue the guided contracting flow. */
public interface SubscriptionsContextFacade {

    record InstallationContext(Long assistedPersonId, Long businessId) {
    }

    /** Business and assisted person linked to the contracted request. */
    Optional<InstallationContext> fetchInstallationContext(String draftId);

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

    /**
     * The contracting request of the account whose service is active (its installation approved and its payment
     * captured), latest first; empty while the account has none.
     */
    Optional<String> fetchActiveDraftOfAccount(Long userId);

    /**
     * Releases the payment the family authorized for a request the technician found not viable, so the held
     * amount returns to the card. A request without an active hold changes nothing.
     *
     * @param draftId the configuration draft of the request
     * @return true when a held payment was released
     */
    boolean releaseConfigurationDraftPayment(String draftId);

    /** Requests capture of the held amount. Stripe's signed webhook confirms the final result. */
    boolean captureConfigurationDraftPayment(String draftId);

    /**
     * Actuators the household contracted, by type name ({@code LIGHT}, {@code DOOR}, {@code WINDOW},
     * {@code CURTAIN}). A type that was not contracted is absent.
     */
    record ContractedActuators(Map<String, Integer> quantityByType) {
        public ContractedActuators {
            quantityByType = Map.copyOf(quantityByType);
        }
    }

    /**
     * Read-only view of what the {@code ACTIVE} subscription of the household includes, so another
     * context can tell whether a device fits the plan. Empty when the household has no active subscription.
     */
    Optional<ContractedActuators> fetchContractedActuatorsOfHousehold(String householdId);

    /**
     * A contracting request whose payment the family authorized: a hiring. It is a sale once the amount was captured.
     *
     * @param billingCycle  {@code MONTHLY} or {@code ANNUAL}
     * @param amount        the monthly amount of the configuration, priced with the plan as it is now
     * @param contractedAt  when the payment was authorized
     * @param paid          whether the amount was captured ({@code SUCCEEDED})
     */
    record Sale(String draftId, Long planId, String planCode, String planName, String billingCycle,
                java.math.BigDecimal amount, String currency, java.time.Instant contractedAt, boolean paid) {
    }

    /**
     * The requests whose payment was authorized in the period ({@code from} included, {@code to} excluded) and was not
     * released or rejected since, oldest first.
     *
     * @param planId only the requests of this plan; {@code null} for every plan
     */
    java.util.List<Sale> fetchSales(java.time.Instant from, java.time.Instant to, Long planId);

    /** How many subscriptions there are in each status ({@code ACTIVE}, {@code PAST_DUE}...); absent when none. */
    Map<String, Long> countSubscriptionsByStatus();
}
