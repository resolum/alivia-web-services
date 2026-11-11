package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.commands.AcceptInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RegisterCaregiverAccountCommand;
import com.resolum.alivia.assistance.domain.model.commands.ResendInvitationCommand;
import com.resolum.alivia.assistance.domain.model.commands.RevokeInvitationCommand;

public interface InvitationCommandService {
    Long handle(CreateInvitationCommand command);
    Long handle(RegisterCaregiverAccountCommand command);
    Long handle(AcceptInvitationCommand command);
    Long handle(RevokeInvitationCommand command);
    Long handle(ResendInvitationCommand command);
}
