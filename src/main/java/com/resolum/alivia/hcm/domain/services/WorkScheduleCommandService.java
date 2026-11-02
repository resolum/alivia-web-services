package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.commands.CreateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.DeactivateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateWorkScheduleCommand;

public interface WorkScheduleCommandService {
    WorkSchedule handle(CreateWorkScheduleCommand command);
    WorkSchedule handle(UpdateWorkScheduleCommand command);
    WorkSchedule handle(DeactivateWorkScheduleCommand command);
}
