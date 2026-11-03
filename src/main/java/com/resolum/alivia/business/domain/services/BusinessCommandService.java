package com.resolum.alivia.business.domain.services;

import com.resolum.alivia.business.domain.model.commands.CreateBusinessCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateBusinessCommand;

public interface BusinessCommandService {
    Long handle(CreateBusinessCommand command);
    Long handle(UpdateBusinessCommand command);
}
