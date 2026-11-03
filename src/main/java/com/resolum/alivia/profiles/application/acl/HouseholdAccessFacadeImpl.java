package com.resolum.alivia.profiles.application.acl;

import org.springframework.stereotype.Service;

import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;

/**
 * Implementation of {@link HouseholdAccessFacade} that delegates to the Profiles domain service.
 */
@Service
public class HouseholdAccessFacadeImpl implements HouseholdAccessFacade {

    private final HouseholdAccessService householdAccessService;

    public HouseholdAccessFacadeImpl(HouseholdAccessService householdAccessService) {
        this.householdAccessService = householdAccessService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canReadHousehold(Long householdId, Long userId) {
        return householdAccessService.canRead(householdId, userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canManageHousehold(Long householdId, Long userId) {
        return householdAccessService.canManage(householdId, userId);
    }
}
