package com.resolum.alivia.tracking.application.internal.outboundservices.acl;

import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Tracking's view of who takes care of an assisted person, and where the person lives (Assistance). */
@Service("trackingExternalAssistanceService")
public class ExternalAssistanceService {

    private final AssistanceContextFacade assistance;

    public ExternalAssistanceService(AssistanceContextFacade assistance) {
        this.assistance = assistance;
    }

    /** Whether the user is in the care network of the person (relative, caregiver or reader of the household). */
    public boolean canReadPerson(Long assistedPersonId, Long userId) {
        return assistance.canReadAssistedPerson(assistedPersonId, userId);
    }

    /** The household of the person; empty when it has not been placed in one. */
    public Optional<Long> fetchHouseholdOfPerson(Long assistedPersonId) {
        return assistance.fetchHouseholdIdOfAssistedPerson(assistedPersonId);
    }

    /** Whether the user is in the care network of someone who lives in the household. */
    public boolean canReadHousehold(Long householdId, Long userId) {
        return assistance.canReadHousehold(householdId, userId);
    }
}
