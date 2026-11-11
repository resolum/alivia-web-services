package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.communications.interfaces.acl.CommunicationsContextFacade;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Outbound adapter that delegates to the Communications bounded context.
 */
@Service("assistanceExternalCommunicationsService")
public class ExternalCommunicationsService {

    private final CommunicationsContextFacade communicationsContextFacade;

    public ExternalCommunicationsService(CommunicationsContextFacade communicationsContextFacade) {
        this.communicationsContextFacade = communicationsContextFacade;
    }

    public void sendCaregiverInvitation(String email, String inviterName, String assistedPersonName,
                                        String passwordSetupUrl, int days) {
        communicationsContextFacade.sendEmail(email, "caregiver-invitation", Map.of(
                "inviterName", inviterName != null ? inviterName : "Familiar",
                "householdAlias", "Alivia Care",
                "assistedPersonName", assistedPersonName != null ? assistedPersonName : "Persona asistida",
                "link", passwordSetupUrl != null ? passwordSetupUrl : "#",
                "days", String.valueOf(days)));
    }
}
