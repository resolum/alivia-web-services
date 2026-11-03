package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;

public record RecordEvaluationResultCommand(Long orderId, EvaluationResult result, String notes) {
}
