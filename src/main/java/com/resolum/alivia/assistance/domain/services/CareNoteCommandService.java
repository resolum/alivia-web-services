package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareNoteCommand;

public interface CareNoteCommandService {
    CareNote handle(CreateCareNoteCommand command);
}
