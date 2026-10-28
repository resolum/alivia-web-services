package com.resolum.alivia.subscriptions.interfaces.acl;

/** Anti-corruption layer of Subscriptions for the contexts that continue the guided contracting flow. */
public interface SubscriptionsContextFacade {

    /**
     * True when the configuration draft exists and its card holds a preauthorization, which is the
     * condition to book the technical evaluation.
     */
    boolean isConfigurationDraftPreauthorized(String draftId);
}
