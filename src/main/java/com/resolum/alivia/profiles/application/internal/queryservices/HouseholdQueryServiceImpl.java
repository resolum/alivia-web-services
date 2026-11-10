package com.resolum.alivia.profiles.application.internal.queryservices;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.queries.GetActiveHouseholdByUserIdQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.domain.services.HouseholdQueryService;

/**
 * Implementation of {@link HouseholdQueryService}.
 *
 * <p>Authorization is enforced by
 * {@link com.resolum.alivia.profiles.domain.services.HouseholdAccessService} before this service
 * is called, so the query only resolves the household or reports that it does not exist.</p>
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
        return householdRepository.findById(query.householdId())
                .orElseThrow(() -> new HouseholdNotFoundException(query.householdId()));
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public List<HouseholdMember> handle(GetHouseholdMembersQuery query) {
        return handle(new GetHouseholdQuery(query.householdId())).getMembers();
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public Optional<Household> handle(GetActiveHouseholdByUserIdQuery query) {
        List<Household> active = householdRepository.findActiveByMemberUserId(query.userId());
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }
}
