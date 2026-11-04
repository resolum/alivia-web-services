package com.resolum.alivia.subscriptions.domain.model.valueobjects;

/**
 * Where the contracting of the signed-in family stands, so the web decides between the flow and the platform.
 *
 * @param draftId the request of the family
 * @param stage   {@code CONFIGURATION} until the payment is held; then the tracking stage ({@code EVALUATION},
 *                {@code CONFIRMATION}, {@code INSTALLATION}, {@code ACTIVATION} or {@code NOT_VIABLE})
 * @param planCode     the chosen plan, so another browser can resume the request
 * @param billingCycle {@code MONTHLY} or {@code ANNUAL}
 */
public record MyContracting(String draftId, String stage, String planCode, String billingCycle, boolean serviceActive) {
    public static final String CONFIGURATION = "CONFIGURATION";

    /** The service is active: the last stage, Activación, was reached. */
    public boolean isActive() {
        return serviceActive;
    }
}
