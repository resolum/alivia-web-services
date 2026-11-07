package com.resolum.alivia.nursing.domain.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link CareActivity} aggregates.
 */
public interface CareActivityRepository {

    /**
     * Saves a care activity and its assignments.
     *
     * @param careActivity the activity
     * @return the saved activity
     */
    CareActivity save(CareActivity careActivity);

    /**
     * Finds the activity by its identifier.
     *
     * @param id the activity identifier
     * @return the activity, or empty when not found
     */
    Optional<CareActivity> findById(Long id);

    /**
     * Finds the activities of the given assisted persons, with their assignments loaded.
     *
     * @param assistedPersonProfileIds the assisted person profiles
     * @return the matching activities
     */
    List<CareActivity> findAllByAssistedPersonProfileIds(Collection<PersonProfileId> assistedPersonProfileIds);

    /**
     * Finds the activity that owns the given assignment, with its assignments loaded.
     *
     * @param assignmentId the assignment identifier
     * @return the activity, or empty when the assignment does not exist
     */
    Optional<CareActivity> findByAssignmentId(Long assignmentId);

    /**
     * Deletes a care activity.
     *
     * @param careActivity the activity to delete
     */
    void delete(CareActivity careActivity);
}
