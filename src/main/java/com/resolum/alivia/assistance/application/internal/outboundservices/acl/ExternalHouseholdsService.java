package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import org.springframework.stereotype.Service;

/**
 * Outbound ACL service for asking Profiles whether a user belongs to a household.
 *
 * <p>The answers are booleans: a missing household and a denied membership both return {@code false}.</p>
 */
@Service("assistanceExternalHouseholdsService")
public class ExternalHouseholdsService {

    private final HouseholdAccessFacade householdAccessFacade;

    public ExternalHouseholdsService(HouseholdAccessFacade householdAccessFacade) {
        this.householdAccessFacade = householdAccessFacade;
    }

    public boolean canRead(Long householdId, Long userId) {
        return householdId != null && userId != null && householdAccessFacade.canReadHousehold(householdId, userId);
    }

    public boolean canManage(Long householdId, Long userId) {
        return householdId != null && userId != null && householdAccessFacade.canManageHousehold(householdId, userId);
    }
}
