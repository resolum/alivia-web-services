package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.CreateAssistedPersonCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateAssistedPersonCommand;

public interface AssistedPersonCommandService {
    Long handle(CreateAssistedPersonCommand command);
    Long handle(UpdateAssistedPersonCommand command);
}
