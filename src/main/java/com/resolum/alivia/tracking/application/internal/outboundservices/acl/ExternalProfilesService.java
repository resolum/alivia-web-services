package com.resolum.alivia.tracking.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import org.springframework.stereotype.Service;

/** Tracking's view of who belongs to a household (Profiles). */
@Service("trackingExternalProfilesService")
public class ExternalProfilesService {

    private final HouseholdAccessFacade households;

    public ExternalProfilesService(HouseholdAccessFacade households) {
        this.households = households;
    }

    /** Whether the user has an active membership of the household with read access. */
    public boolean canReadHousehold(Long householdId, Long userId) {
        return householdId != null && userId != null && households.canReadHousehold(householdId, userId);
    }
}
