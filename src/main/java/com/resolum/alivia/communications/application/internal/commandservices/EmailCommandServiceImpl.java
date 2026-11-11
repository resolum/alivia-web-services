package com.resolum.alivia.communications.application.internal.commandservices;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.commands.SendEmailCommand;
import com.resolum.alivia.communications.domain.repositories.EmailRepository;
import com.resolum.alivia.communications.domain.services.EmailCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
public class EmailCommandServiceImpl implements EmailCommandService {

    private final EmailRepository emailRepository;
    private final EmailSender emailSender;

    public EmailCommandServiceImpl(EmailRepository emailRepository,
                                  @Qualifier("emailSender") EmailSender emailSender) {
        this.emailRepository = emailRepository;
        this.emailSender = emailSender;
    }

    @Override
    public Long handle(SendEmailCommand command) {
        EmailAddress address = new EmailAddress(command.emailAddress());
        Email email = new Email(address, command.template(), command.variables());
        Email saved = emailRepository.save(email);

        try {
            String subject = "Notificación de Alivia - " + command.template().name();
            String bodyText = command.variables() != null ? command.variables().toString() : "";
            emailSender.send(address.email(), subject, bodyText, "<p>" + bodyText + "</p>");
            saved.markSent(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to deliver email {}: {}", saved.getId(), e.getMessage());
            saved.markFailed(e.getMessage());
        }

        emailRepository.save(saved);
        return saved.getId();
    }
}
