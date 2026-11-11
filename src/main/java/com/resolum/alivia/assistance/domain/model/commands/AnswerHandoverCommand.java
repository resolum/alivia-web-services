package com.resolum.alivia.assistance.domain.model.commands;

import java.util.List;

public record AnswerHandoverCommand(
        Long handoverId,
        boolean accept,
        Long actorCaregiverId,
        String actorCaregiverName,
        List<String> attended,
        List<String> pending
) {}
