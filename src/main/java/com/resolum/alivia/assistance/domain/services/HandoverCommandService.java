package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.commands.AnswerHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.EscalateHandoverCommand;
import com.resolum.alivia.assistance.domain.model.commands.ProposeSubstituteCommand;
import com.resolum.alivia.assistance.domain.model.commands.RequestHandoverCommand;

public interface HandoverCommandService {
    Handover handle(RequestHandoverCommand command);
    Handover handle(AnswerHandoverCommand command);
    Handover handle(ProposeSubstituteCommand command);
    Handover handle(EscalateHandoverCommand command);
}
