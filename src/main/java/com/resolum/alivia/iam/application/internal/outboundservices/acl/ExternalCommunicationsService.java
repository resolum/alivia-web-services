package com.resolum.alivia.iam.application.internal.outboundservices.acl;

import com.resolum.alivia.communications.interfaces.acl.CommunicationsContextFacade;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Outbound service that asks the Communications bounded context to notify IAM users.
 *
 * <p>IAM never builds or delivers emails itself; it only calls
 * {@link CommunicationsContextFacade}.</p>
 */
@Service
public class ExternalCommunicationsService {

    private final CommunicationsContextFacade communicationsContextFacade;

    /**
     * Creates the outbound service.
     *
     * @param communicationsContextFacade the Communications anti-corruption facade
     */
    public ExternalCommunicationsService(CommunicationsContextFacade communicationsContextFacade) {
        this.communicationsContextFacade = communicationsContextFacade;
    }

    /**
     * Sends the account verification email.
     *
     * @param email            the account email address
     * @param verificationLink the link that contains the plain-text verification token
     * @throws IllegalStateException when the email cannot be delivered
     */
    public void sendVerificationEmail(String email, String verificationLink) {
        communicationsContextFacade.sendEmail(email, "verify-email", Map.of("link", verificationLink));
    }

    /** Invitation for new staff: the link opens the page where they create their password. */
    public void sendAccountInvitationEmail(String email, String fullName, String roleLabel,
                                           String activationLink, int expirationHours) {
        communicationsContextFacade.sendEmail(email, "account-invitation", Map.of("name", fullName,
                "role", roleLabel, "link", activationLink, "hours", String.valueOf(expirationHours)));
    }
}
