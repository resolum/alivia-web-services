package com.resolum.alivia.nursing.application.internal.outboundservices.acl;

import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outbound service acting as an Anti-Corruption Layer (ACL) adapter to the household data of the
 * Profiles bounded context.
 *
 * <p>Profiles decides who may read or manage a household; this adapter only translates its
 * answers into Nursing errors. A missing household and a denied user are told apart by reading the
 * members first, so that the first answers 404 and the second 403.</p>
 */
@Slf4j
@Service("nursingExternalHouseholdsService")
public class ExternalHouseholdsService {

    // Profiles responsibilities, sent by name because the facade exposes primitives only.
    // An assisted person only reads the household; managing it belongs to the principal.
    private static final String ASSISTED_PERSON_RESPONSIBILITY = "GUEST";
    private static final String CAREGIVER_RESPONSIBILITY = "CAREGIVER";

    private final HouseholdAccessFacade householdAccessFacade;
    private final HouseholdMembershipFacade householdMembershipFacade;

    /**
     * Creates the adapter.
     *
     * @param householdAccessFacade     the Profiles access facade
     * @param householdMembershipFacade the Profiles membership facade
     */
    public ExternalHouseholdsService(HouseholdAccessFacade householdAccessFacade,
                                     HouseholdMembershipFacade householdMembershipFacade) {
        this.householdAccessFacade = householdAccessFacade;
        this.householdMembershipFacade = householdMembershipFacade;
    }

    /**
     * Requires that the household exists and the user may read it.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return the users with an active membership in the household
     * @throws CareNetworkHouseholdNotFoundException when the household does not exist
     * @throws CareNetworkAccessDeniedException      when the user cannot read the household
     */
    public Set<UserId> requireReadable(Long householdId, UserId userId) {
        var members = fetchActiveMembers(householdId);
        if (!householdAccessFacade.canReadHousehold(householdId, userId.userId())) {
            throw denied("read", householdId, userId);
        }
        return members;
    }

    /**
     * Requires that the household exists and the user may manage it.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return the users with an active membership in the household
     * @throws CareNetworkHouseholdNotFoundException when the household does not exist
     * @throws CareNetworkAccessDeniedException      when the user cannot manage the household
     */
    public Set<UserId> requireManageable(Long householdId, UserId userId) {
        var members = fetchActiveMembers(householdId);
        if (!householdAccessFacade.canManageHousehold(householdId, userId.userId())) {
            throw denied("manage", householdId, userId);
        }
        return members;
    }

    /**
     * Checks whether the user may manage the household, without failing when it cannot.
     *
     * @param householdId the household identifier
     * @param userId      the authenticated user
     * @return {@code true} when Profiles grants management access
     */
    public boolean canManage(Long householdId, UserId userId) {
        return householdAccessFacade.canManageHousehold(householdId, userId.userId());
    }

    /**
     * Adds an assisted person to the household with read-only access.
     *
     * @param householdId the household identifier
     * @param userId      the assisted person user
     * @return {@code true} when the user was added; {@code false} when it was already a member
     */
    public boolean addAssistedPerson(Long householdId, UserId userId) {
        return householdMembershipFacade.addMember(householdId, userId.userId(), ASSISTED_PERSON_RESPONSIBILITY);
    }

    /**
     * Adds a caregiver to the household. A user that already belongs keeps its current responsibility.
     *
     * @param householdId the household identifier
     * @param userId      the caregiver user
     * @return {@code true} when the user was added; {@code false} when it was already a member
     */
    public boolean addCaregiver(Long householdId, UserId userId) {
        return householdMembershipFacade.addMember(householdId, userId.userId(), CAREGIVER_RESPONSIBILITY);
    }

    private Set<UserId> fetchActiveMembers(Long householdId) {
        return householdMembershipFacade.fetchActiveMemberUserIds(householdId)
                .orElseThrow(() -> new CareNetworkHouseholdNotFoundException(householdId))
                .stream()
                .map(UserId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static CareNetworkAccessDeniedException denied(String operation, Long householdId, UserId userId) {
        // Only identifiers and the operation are logged; no household data reaches the log.
        log.warn("Care network access denied: operation={}, householdId={}, userId={}",
                operation, householdId, userId.userId());
        return new CareNetworkAccessDeniedException(householdId);
    }
}
