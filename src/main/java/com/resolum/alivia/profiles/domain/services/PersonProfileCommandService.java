package com.resolum.alivia.profiles.domain.services;

import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.domain.model.commands.UpdatePersonProfileCommand;

public interface PersonProfileCommandService {
    Long handle(CreatePersonProfileCommand command);

    Long handle(UpdatePersonProfileCommand command);
}
