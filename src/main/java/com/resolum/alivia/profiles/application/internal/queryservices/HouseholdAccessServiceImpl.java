package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdAccessService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Implementation of {@link HouseholdAccessService}.
 *
 * <p>Every decision is derived from the household aggregate, so it is deterministic and does not
 * depend on the access role carried by the token.</p>
 */
@Service
public class HouseholdAccessServiceImpl implements HouseholdAccessService {

    private static final Set<HouseholdResponsibility> READ_RESPONSIBILITIES =
            EnumSet.of(HouseholdResponsibility.PRINCIPAL,
                    HouseholdResponsibility.CAREGIVER,
                    HouseholdResponsibility.GUEST);

    private static final Set<HouseholdResponsibility> MANAGE_RESPONSIBILITIES =
            EnumSet.of(HouseholdResponsibility.PRINCIPAL);

    private final HouseholdRepository householdRepository;

    public HouseholdAccessServiceImpl(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public boolean canRead(Long householdId, Long userId) {
        validate(householdId, userId);
        return householdRepository.findById(householdId)
                .map(household -> grants(household, userId, READ_RESPONSIBILITIES))
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public boolean canManage(Long householdId, Long userId) {
        validate(householdId, userId);
        return householdRepository.findById(householdId)
                .map(household -> grants(household, userId, MANAGE_RESPONSIBILITIES))
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public void assertCanRead(Long householdId, Long userId) {
        assertGranted(householdId, userId, READ_RESPONSIBILITIES);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public void assertCanManage(Long householdId, Long userId) {
        assertGranted(householdId, userId, MANAGE_RESPONSIBILITIES);
    }

    private void assertGranted(Long householdId, Long userId,
                               Set<HouseholdResponsibility> required) {
        validate(householdId, userId);
        var household = householdRepository.findById(householdId)
                .orElseThrow(() -> new HouseholdNotFoundException(householdId));
        if (!grants(household, userId, required)) {
            throw new HouseholdAccessDeniedException(householdId, userId);
        }
    }

    private static boolean grants(Household household, Long userId,
                                  Set<HouseholdResponsibility> required) {
        return household.findMember(new UserId(userId))
                .filter(member -> member.isActive()
                        && required.contains(member.getResponsibility()))
                .isPresent();
    }

    private static void validate(Long householdId, Long userId) {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than 0");
        }
    }
}
