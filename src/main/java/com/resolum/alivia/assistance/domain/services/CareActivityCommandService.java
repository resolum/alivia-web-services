package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.ActivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.ActivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.AssignCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.ReassignCaregiverActivitiesCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateCareActivityCommand;

public interface CareActivityCommandService {
    Long handle(CreateCareActivityCommand command);
    Long handle(UpdateCareActivityCommand command);
    Long handle(ActivateCareActivityCommand command);
    Long handle(DeactivateCareActivityCommand command);
    Long handle(AssignCareActivityCommand command);
    Long handle(UpdateActivityAssignmentCommand command);
    Long handle(ActivateActivityAssignmentCommand command);
    Long handle(DeactivateActivityAssignmentCommand command);
    void handle(ReassignCaregiverActivitiesCommand command);
}
