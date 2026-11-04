package com.resolum.alivia.profiles.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdMemberStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdPersistenceRepository extends JpaRepository<Household, Long> {

    /**
     * Finds a household by identifier, fetching its members in the same query.
     *
     * @param householdId the household identifier
     * @return the household, or empty when it does not exist
     */
    @Query("select distinct h from Household h left join fetch h.householdMembers.members "
            + "where h.id = :householdId")
    Optional<Household> findWithMembersById(@Param("householdId") Long householdId);

    /**
     * Finds the households where the user has a membership with the given status, fetching
     * their members in the same query.
     *
     * @param userId the member user identifier
     * @param status the membership status
     * @return the matching households
     */
    @Query("select distinct h from Household h left join fetch h.householdMembers.members m "
            + "where m.userId = :userId and m.status = :status")
    List<Household> findAllByMemberUserIdAndStatus(@Param("userId") Long userId,
                                                   @Param("status") HouseholdMemberStatus status);
}
