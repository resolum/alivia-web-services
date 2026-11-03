package com.resolum.alivia.nursing.application.internal.commandservices;

import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.nursing.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.RegisterAssistedPersonCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.AssistedPersonSummary;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Registers assisted persons in a household.
 *
 * <p>Checks run in this order: household existence (404), management access (403) and the
 * requester profile. Only then the account is created in IAM, so a rejected request never creates
 * an account. Everything runs in one transaction: a failure after the sign-up rolls it back.</p>
 */
@Slf4j
@Service
public class AssistedPersonCommandServiceImpl implements AssistedPersonCommandService {

    private final CareMembershipRepository careMembershipRepository;
    private final ExternalHouseholdsService externalHouseholdsService;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;

    /**
     * Creates the service.
     *
     * @param careMembershipRepository   the membership repository
     * @param externalHouseholdsService  the Profiles ACL used to authorize and add the member
     * @param externalIamService         the IAM ACL used to create the account and resolve profiles
     * @param externalProfilesService    the Profiles ACL used to resolve the name
     */
    public AssistedPersonCommandServiceImpl(CareMembershipRepository careMembershipRepository,
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
    @Transactional
    public AssistedPersonSummary handle(RegisterAssistedPersonCommand command) {
        log.debug("Handling register assisted person command: {}", command);
        var requester = command.requesterUserId();
        externalHouseholdsService.requireManageable(command.householdId(), requester);
        var requesterProfile = requireProfile(requester);

        // IAM does not use the familiar identifier yet; the requester user is the familiar.
        var accountId = externalIamService.signUpAssistedPerson(command.email(), command.password(),
                command.firstName(), command.lastName(), command.phoneNumber(), requester.userId(),
                command.dni(), command.birthDate());
        var assistedUser = new UserId(accountId);
        var assistedProfile = externalIamService.fetchPersonProfileIds(Set.of(assistedUser)).get(assistedUser);
        if (assistedProfile == null) {
            // Rolls the whole registration back: an assisted person without profile cannot be linked.
            throw new IllegalStateException("IAM created assisted person account " + accountId + " without a person profile.");
        }

        externalHouseholdsService.addAssistedPerson(command.householdId(), assistedUser);
        careMembershipRepository.save(new CareMembership(requester, requesterProfile, assistedProfile, CareRole.RELATIVE));

        var name = externalProfilesService.fetchPersonNames(Set.of(assistedProfile)).get(assistedProfile);
        log.info("Registered assisted person profile {} in household {} by user id {}",
                assistedProfile.personProfileId(), command.householdId(), requester.userId());
        return new AssistedPersonSummary(assistedProfile, name);
    }

    private PersonProfileId requireProfile(UserId userId) {
        var profileId = externalIamService.fetchPersonProfileIds(Set.of(userId)).get(userId);
        if (profileId == null) {
            throw new CareMemberProfileNotFoundException(userId);
        }
        return profileId;
    }
}
