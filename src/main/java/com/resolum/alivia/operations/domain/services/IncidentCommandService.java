package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;

public interface IncidentCommandService {
    Incident handle(CreateIncidentCommand command);

    Incident handle(AttachEvidenceCommand command);
}
