package com.resolum.alivia.nursing.domain.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Collection;
import java.util.List;

/**
 * Persistence port for {@link CareMembership} aggregates.
 */
public interface CareMembershipRepository {

    /**
     * Saves a care membership.
     *
     * @param careMembership the membership
     * @return the saved membership
     */
    CareMembership save(CareMembership careMembership);

    /**
     * Checks whether the user already has a membership with the assisted person in the role,
     * whatever its status.
     *
     * @param userId                  the member user
     * @param assistedPersonProfileId the assisted person profile
     * @param role                    the role of the member
     * @return {@code true} when a membership exists
     */
    boolean existsByUserIdAndAssistedPersonProfileIdAndRole(UserId userId, PersonProfileId assistedPersonProfileId,
                                                            CareRole role);

    /**
     * Finds the active memberships of a user, as caregiver or relative.
     *
     * @param userId the member user
     * @return the active memberships
     */
    List<CareMembership> findActiveByUserId(UserId userId);

    /**
     * Finds the active memberships of the given assisted persons, whoever the member is.
     *
     * @param assistedPersonProfileIds the assisted person profiles
     * @return the active memberships
     */
    List<CareMembership> findActiveByAssistedPersonProfileIds(Collection<PersonProfileId> assistedPersonProfileIds);
}
