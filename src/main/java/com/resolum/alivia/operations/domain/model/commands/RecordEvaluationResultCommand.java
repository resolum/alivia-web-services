package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;

/**
 * Closes an evaluation with the technician's decision. A viable home waits for the family to confirm the
 * configuration from the web; that confirmation opens the installation order.
 */
public record RecordEvaluationResultCommand(Long orderId, EvaluationResult result, String notes) {
}
