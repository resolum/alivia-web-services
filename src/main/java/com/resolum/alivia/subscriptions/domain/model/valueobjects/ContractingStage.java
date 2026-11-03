package com.resolum.alivia.subscriptions.domain.model.valueobjects;

/**
 * Step of the contracting request as the family follows it after the payment: the technical evaluation, the
 * confirmation of the configuration the technician validated, the installation and the activation.
 */
public enum ContractingStage {
    /** The evaluation visit is pending; the technician decides at the home. */
    EVALUATION,
    /** The home is viable: the family reviews and confirms the configuration. */
    CONFIRMATION,
    /** The family confirmed; the installation follows. */
    INSTALLATION,
    /** The installation was approved and the service is being activated. */
    ACTIVATION,
    /** The technician found the home not viable and the request is closed. */
    NOT_VIABLE
}
