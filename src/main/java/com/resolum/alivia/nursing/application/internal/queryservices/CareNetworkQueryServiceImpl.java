package com.resolum.alivia.nursing.application.internal.queryservices;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonsQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetCaregiversQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CaregiverSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.CareNetworkQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the care network read model of a household.
 *
 * <p>Nursing keeps no reference to the household. The assisted persons are resolved from the
 * active members of the household (Profiles), their person profiles (IAM) and the active care
 * memberships that point to those profiles. Names are read from Profiles and emails from IAM,
 * both through ACL adapters, so no aggregate of another context is touched.</p>
 */
@Slf4j
@Service
public class CareNetworkQueryServiceImpl implements CareNetworkQueryService {

    private static final Comparator<CaregiverSummary> BY_ASSISTED_PERSON_THEN_CAREGIVER =
            Comparator.comparing((CaregiverSummary caregiver) -> caregiver.assistedPersonProfileId().personProfileId())
                    .thenComparing(caregiver -> caregiver.personProfileId().personProfileId());

    private final CareMembershipRepository careMembershipRepository;
    private final ExternalHouseholdsService externalHouseholdsService;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;

    /**
     * Creates the service.
     *
     * @param careMembershipRepository   the membership repository
     * @param externalHouseholdsService  the Profiles ACL used to authorize and list household members
     * @param externalIamService         the IAM ACL used to resolve profiles and emails
     * @param externalProfilesService    the Profiles ACL used to resolve names
     */
    public CareNetworkQueryServiceImpl(CareMembershipRepository careMembershipRepository,
                                       ExternalHouseholdsService externalHouseholdsService,
                                       ExternalIamService externalIamService,
                                       ExternalProfilesService externalProfilesService) {
        this.careMembershipRepository = careMembershipRepository;
        this.externalHouseholdsService = externalHouseholdsService;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<AssistedPersonSummary> handle(GetAssistedPersonsQuery query) {
        log.debug("Handling assisted persons query for household {}", query.householdId());
        var memberships = activeMemberships(query.householdId(), query.userId());
        var profileIds = memberships.stream()
                .map(CareMembership::getAssistedPersonProfileId)
                .collect(Collectors.toCollection(HashSet::new));
        var names = externalProfilesService.fetchPersonNames(profileIds);
        var assistedPersons = profileIds.stream()
                .sorted(Comparator.comparing(PersonProfileId::personProfileId))
                .map(profileId -> new AssistedPersonSummary(profileId, names.get(profileId)))
                .toList();
        log.info("Found {} assisted persons in household {}", assistedPersons.size(), query.householdId());
        return assistedPersons;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<CaregiverSummary> handle(GetCaregiversQuery query) {
        log.debug("Handling caregivers query for household {}", query.householdId());
        var caregivers = activeMemberships(query.householdId(), query.userId()).stream()
                .filter(membership -> membership.getRole() == CareRole.CAREGIVER)
                .toList();
        var userIds = caregivers.stream().map(CareMembership::getUserId).collect(Collectors.toSet());
        var profileIds = caregivers.stream()
                .map(CareMembership::getMemberPersonProfileId)
                .collect(Collectors.toSet());
        var names = externalProfilesService.fetchPersonNames(profileIds);
        var emails = externalIamService.fetchEmails(userIds);
        var summaries = caregivers.stream()
                .map(membership -> new CaregiverSummary(membership.getUserId(), membership.getMemberPersonProfileId(),
                        membership.getAssistedPersonProfileId(), names.get(membership.getMemberPersonProfileId()),
                        emails.get(membership.getUserId())))
                .sorted(BY_ASSISTED_PERSON_THEN_CAREGIVER)
                .toList();
        log.info("Found {} caregivers in household {}", summaries.size(), query.householdId());
        return summaries;
    }

    /**
     * Returns the active memberships whose assisted person is an active member of the household.
     */
    private List<CareMembership> activeMemberships(Long householdId, UserId userId) {
        Set<UserId> members = externalHouseholdsService.requireReadable(householdId, userId);
        var memberProfiles = externalIamService.fetchPersonProfileIds(members).values();
        return careMembershipRepository.findActiveByAssistedPersonProfileIds(memberProfiles);
    }
}
