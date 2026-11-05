package com.resolum.alivia.assets.application.internal.outboundservices.acl;

import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import org.springframework.stereotype.Service;

/** Assets' view of Profiles: who may manage a household. */
@Service("assetsExternalHouseholdsService")
public class ExternalHouseholdsService {
    private final HouseholdAccessFacade householdAccessFacade;

    public ExternalHouseholdsService(HouseholdAccessFacade householdAccessFacade) {
        this.householdAccessFacade = householdAccessFacade;
    }

    /** True when the account has an active membership that lets it manage the household. */
    public boolean canManageHousehold(Long householdId, Long userId) {
        return householdId != null && userId != null && householdAccessFacade.canManageHousehold(householdId, userId);
    }
}
