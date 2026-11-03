package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.EvaluationResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param notes the adaptation the home needs; required when the result is {@code VIABLE_WITH_ADAPTATION}
 */
public record EvaluationResultResource(@NotNull EvaluationResult result, @Size(max = 300) String notes) {
}
