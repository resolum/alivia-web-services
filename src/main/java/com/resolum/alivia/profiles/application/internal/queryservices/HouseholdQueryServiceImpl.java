package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException;
import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Implementation of {@link HouseholdQueryService}.
 *
 * <p>The membership check lives here as the minimal authorization required by TS-04
 * scenario 4. AP-355 will replace it with the household authorization service that also
 * considers the member responsibility.</p>
 */
@Service
public class HouseholdQueryServiceImpl implements HouseholdQueryService {

    private final HouseholdRepository householdRepository;

    public HouseholdQueryServiceImpl(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public Household handle(GetHouseholdQuery query) {
        var household = householdRepository.findById(query.householdId())
                .orElseThrow(() -> new HouseholdNotFoundException(query.householdId()));
        assertActiveMember(household, query.requesterUserId());
        return household;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public List<HouseholdMember> handle(GetHouseholdMembersQuery query) {
        return handle(new GetHouseholdQuery(query.householdId(), query.requesterUserId())).getMembers();
    }

    private static void assertActiveMember(Household household, Long userId) {
        var member = household.findMember(new UserId(userId))
                .orElseThrow(() -> new HouseholdAccessDeniedException(household.getId(), userId));
        if (!member.isActive()) {
            throw new HouseholdAccessDeniedException(household.getId(), userId);
        }
    }
}
