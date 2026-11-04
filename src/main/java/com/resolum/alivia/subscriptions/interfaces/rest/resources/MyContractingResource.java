package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param stage CONFIGURATION, EVALUATION, CONFIRMATION, INSTALLATION, ACTIVATION or NOT_VIABLE
 * @param active true once the request reached Activación, so the family can use the platform
 */
public record MyContractingResource(String draftId, String code,
                                    @Schema(example = "INSTALLATION") String stage, boolean active,
                                    @Schema(example = "CUSTOM") String planCode,
                                    @Schema(example = "MONTHLY") String billingCycle) {
}
