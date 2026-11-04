package com.resolum.alivia.profiles.application.acl;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.commands.AddHouseholdMemberCommand;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.services.HouseholdMemberCommandService;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link HouseholdMembershipFacade} that delegates to the Profiles domain
 * services.
 */
@Service
public class HouseholdMembershipFacadeImpl implements HouseholdMembershipFacade {

    private final HouseholdQueryService householdQueryService;
    private final HouseholdMemberCommandService householdMemberCommandService;

    /**
     * Creates the facade.
     *
     * @param householdQueryService         the household query service
     * @param householdMemberCommandService the household member command service
     */
    public HouseholdMembershipFacadeImpl(HouseholdQueryService householdQueryService,
                                         HouseholdMemberCommandService householdMemberCommandService) {
        this.householdQueryService = householdQueryService;
        this.householdMemberCommandService = householdMemberCommandService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Set<Long>> fetchActiveMemberUserIds(Long householdId) {
        try {
            return Optional.of(householdQueryService.handle(new GetHouseholdMembersQuery(householdId)).stream()
                    .filter(HouseholdMember::isActive)
                    .map(member -> member.getUserId().userId())
                    .collect(Collectors.toUnmodifiableSet()));
        } catch (HouseholdNotFoundException exception) {
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addMember(Long householdId, Long userId, String responsibility) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than 0");
        }
        var command = new AddHouseholdMemberCommand(householdId, new UserId(userId),
                HouseholdResponsibility.valueOf(responsibility));
        try {
            householdMemberCommandService.handle(command);
            return true;
        } catch (HouseholdMemberAlreadyExistsException exception) {
            return false;
        }
    }
}
