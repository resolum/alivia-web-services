package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.CreateCaregiverCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeCaregiverAccessCommand;

public interface CaregiverCommandService {
    Long handle(CreateCaregiverCommand command);
    Long handle(RevokeCaregiverAccessCommand command);
}
