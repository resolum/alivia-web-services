package com.resolum.alivia.profiles.application.internal.commandservices;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdMemberAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.commands.AddHouseholdMemberCommand;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdMemberCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HouseholdMemberCommandService}.
 *
 * <p>The one-member-per-household invariant is enforced by the {@code Household} aggregate.
 * Authorization is the caller's responsibility.</p>
 *
 * <p>A duplicated member is rejected before anything changes, so it does not roll back the
 * transaction: callers that join it can handle the conflict and keep going.</p>
 */
@Slf4j
@Service
public class HouseholdMemberCommandServiceImpl implements HouseholdMemberCommandService {

    private final HouseholdRepository householdRepository;

    /**
     * Creates the service.
     *
     * @param householdRepository the household repository
     */
    public HouseholdMemberCommandServiceImpl(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(noRollbackFor = HouseholdMemberAlreadyExistsException.class)
    public HouseholdMember handle(AddHouseholdMemberCommand command) {
        var household = householdRepository.findById(command.householdId())
                .orElseThrow(() -> new HouseholdNotFoundException(command.householdId()));
        household.addMember(command.userId(), command.responsibility());
        var saved = householdRepository.save(household);
        log.info("Added user id {} to household {} as {}", command.userId().userId(),
                command.householdId(), command.responsibility());
        return saved.findMember(command.userId()).orElseThrow();
    }
}
