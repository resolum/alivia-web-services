package com.resolum.alivia.business.domain.services;

import com.resolum.alivia.business.domain.model.commands.CreateAdministratorCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateAdministratorCommand;

public interface AdministratorCommandService {
    Long handle(CreateAdministratorCommand command);
    Long handle(UpdateAdministratorCommand command);
}
