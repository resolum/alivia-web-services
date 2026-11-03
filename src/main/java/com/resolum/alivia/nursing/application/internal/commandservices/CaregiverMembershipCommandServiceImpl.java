package com.resolum.alivia.nursing.application.internal.commandservices;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonNotInHouseholdException;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CaregiverAlreadyLinkedException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.LinkCaregiverCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.CareNetworkQueryService;
import com.resolum.alivia.nursing.domain.services.CaregiverMembershipCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Links caregivers to the assisted persons of a household.
 *
 * <p>Checks run in this order: household existence (404), management access (403), the assisted
 * person belongs to the household (404), the caregiver has a profile (404), the caregiver is not
 * the assisted person (400) and the pair is not linked yet (409). A concurrent duplicate is
 * rejected by the unique constraint and also answered as a conflict.</p>
 */
@Slf4j
@Service
public class CaregiverMembershipCommandServiceImpl implements CaregiverMembershipCommandService {

    private final CareMembershipRepository careMembershipRepository;
    private final CareNetworkQueryService careNetworkQueryService;
    private final ExternalHouseholdsService externalHouseholdsService;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;

    /**
     * Creates the service.
     *
     * @param careMembershipRepository   the membership repository
     * @param careNetworkQueryService    the query service that lists the assisted persons of the household
     * @param externalHouseholdsService  the Profiles ACL used to authorize and add the member
     * @param externalIamService         the IAM ACL used to resolve profiles and emails
     * @param externalProfilesService    the Profiles ACL used to resolve the name
     */
    public CaregiverMembershipCommandServiceImpl(CareMembershipRepository careMembershipRepository,
                                                 CareNetworkQueryService careNetworkQueryService,
                                                 ExternalHouseholdsService externalHouseholdsService,
                                                 ExternalIamService externalIamService,
                                                 ExternalProfilesService externalProfilesService) {
        this.careMembershipRepository = careMembershipRepository;
        this.careNetworkQueryService = careNetworkQueryService;
        this.externalHouseholdsService = externalHouseholdsService;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public CaregiverSummary handle(LinkCaregiverCommand command) {
        log.debug("Handling link caregiver command for household {}", command.householdId());
        var householdId = command.householdId();
        var assistedProfile = command.assistedPersonProfileId();
        var caregiver = command.caregiverUserId();
        externalHouseholdsService.requireManageable(householdId, command.requesterUserId());

        var belongsToHousehold = careNetworkQueryService
                .handle(new GetAssistedPersonsQuery(command.requesterUserId(), householdId)).stream()
                .map(AssistedPersonSummary::personProfileId)
                .anyMatch(assistedProfile::equals);
        if (!belongsToHousehold) {
            throw new AssistedPersonNotInHouseholdException(householdId, assistedProfile);
        }

        var caregiverProfile = externalIamService.fetchPersonProfileIds(Set.of(caregiver)).get(caregiver);
        if (caregiverProfile == null) {
            throw new CareMemberProfileNotFoundException(caregiver);
        }
        if (caregiverProfile.equals(assistedProfile)) {
            throw new IllegalArgumentException("An assisted person cannot be their own caregiver.");
        }
        if (careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(caregiver, assistedProfile,
                CareRole.CAREGIVER)) {
            throw new CaregiverAlreadyLinkedException(caregiver, assistedProfile);
        }

        // A caregiver of several assisted persons of the household is already a member the second time.
        externalHouseholdsService.addCaregiver(householdId, caregiver);
        try {
            careMembershipRepository.save(new CareMembership(caregiver, caregiverProfile, assistedProfile,
                    CareRole.CAREGIVER));
        } catch (DataIntegrityViolationException exception) {
            log.warn("Caregiver link rejected: concurrent link already exists for assisted person {}",
                    assistedProfile.personProfileId());
            throw new CaregiverAlreadyLinkedException(caregiver, assistedProfile);
        }

        var name = externalProfilesService.fetchPersonNames(Set.of(caregiverProfile)).get(caregiverProfile);
        var email = externalIamService.fetchEmails(Set.of(caregiver)).get(caregiver);
        log.info("Linked user id {} as caregiver of assisted person {} in household {}", caregiver.userId(),
                assistedProfile.personProfileId(), householdId);
        return new CaregiverSummary(caregiver, caregiverProfile, assistedProfile, name, email);
    }
}
