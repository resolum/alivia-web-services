package com.resolum.alivia.profiles.application.acl;

import org.springframework.stereotype.Service;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;

import java.util.Optional;

/**
 * Implementation of {@link HouseholdAccessFacade} that delegates to the Profiles domain service.
 */
@Service
public class HouseholdAccessFacadeImpl implements HouseholdAccessFacade {

    private final HouseholdAccessService householdAccessService;
    private final HouseholdRepository householdRepository;

    public HouseholdAccessFacadeImpl(HouseholdAccessService householdAccessService,
                                     HouseholdRepository householdRepository) {
        this.householdAccessService = householdAccessService;
        this.householdRepository = householdRepository;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> fetchHouseholdAlias(Long householdId) {
        if (householdId == null || householdId <= 0) {
            return Optional.empty();
        }
        return householdRepository.findById(householdId).map(Household::getAlias);
    }
}
