package com.resolum.alivia.profiles.application.internal.outboundservices.acl;

import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import org.springframework.stereotype.Service;

/** Outbound ACL service for asking Assistance whether a user takes care of the person of a profile. */
@Service
public class ExternalAssistanceService {

    private final AssistanceContextFacade assistanceContextFacade;

    public ExternalAssistanceService(AssistanceContextFacade assistanceContextFacade) {
        this.assistanceContextFacade = assistanceContextFacade;
    }

    public boolean canReadPersonProfile(Long personProfileId, Long userId) {
        return assistanceContextFacade.canReadPersonProfile(personProfileId, userId);
    }

    public boolean canOperatePersonProfile(Long personProfileId, Long userId) {
        return assistanceContextFacade.canOperatePersonProfile(personProfileId, userId);
    }
}
