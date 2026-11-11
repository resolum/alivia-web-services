package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.commands.SendEmailCommand;

public interface EmailCommandService {
    Long handle(SendEmailCommand command);
}
