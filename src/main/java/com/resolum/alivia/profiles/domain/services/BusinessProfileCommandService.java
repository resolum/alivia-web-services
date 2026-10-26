package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;

public interface BusinessProfileCommandService {
    Long handle(CreateBusinessProfileCommand command);

    Long handle(UpdateBusinessProfileCommand command);
}
