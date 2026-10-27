package com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.valueobjects.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for {@link CareMembership} aggregates.
 */
public interface CareMembershipPersistenceRepository extends JpaRepository<CareMembership, Long> {

    /**
     * Finds the memberships of a user with the given status.
     *
     * @param userId the member user identifier
     * @param status the membership status
     * @return the matching memberships
     */
    List<CareMembership> findAllByUserIdAndStatus(Long userId, MembershipStatus status);

    /**
     * Finds the memberships of the given assisted persons with the given status.
     *
     * @param assistedPersonProfileIds the assisted person profile identifiers
     * @param status                   the membership status
     * @return the matching memberships
     */
    List<CareMembership> findAllByAssistedPersonProfileIdInAndStatus(Collection<Long> assistedPersonProfileIds,
                                                                     MembershipStatus status);
}
