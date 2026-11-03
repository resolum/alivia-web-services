package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.EvaluationSlotHold;
import com.resolum.alivia.operations.domain.model.commands.HoldEvaluationSlotCommand;
import com.resolum.alivia.operations.domain.model.commands.ReleaseEvaluationSlotCommand;

public interface EvaluationSlotHoldCommandService {
    EvaluationSlotHold handle(HoldEvaluationSlotCommand command);

    void handle(ReleaseEvaluationSlotCommand command);
}
