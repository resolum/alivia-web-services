package com.resolum.alivia.operations.domain.services;

import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.commands.CreateIncidentCommand;
import com.resolum.alivia.operations.domain.model.commands.ReportIncidentCommand;

public interface IncidentCommandService {
    Incident handle(CreateIncidentCommand command);

    Incident handle(AttachEvidenceCommand command);

    /** Registers the report of a family, starts its evaluation and books the visit in the next free block. */
    Incident handle(ReportIncidentCommand command);
}
