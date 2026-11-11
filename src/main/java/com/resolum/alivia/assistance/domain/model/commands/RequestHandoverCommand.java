package com.resolum.alivia.assistance.domain.model.commands;

import java.util.List;

public record RequestHandoverCommand(
        Long fromShiftId,
        Long toShiftId,
        List<String> attended,
        List<String> pending
) {}
