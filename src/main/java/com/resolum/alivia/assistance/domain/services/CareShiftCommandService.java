package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeleteShiftCommand;

public interface CareShiftCommandService {
    CareShift handle(AssignShiftCommand command);
    void handle(DeleteShiftCommand command);
}
