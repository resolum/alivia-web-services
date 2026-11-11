package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.CreateRelativeCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateRelativeCommand;

public interface RelativeCommandService {
    Long handle(CreateRelativeCommand command);
    Long handle(UpdateRelativeCommand command);
}
