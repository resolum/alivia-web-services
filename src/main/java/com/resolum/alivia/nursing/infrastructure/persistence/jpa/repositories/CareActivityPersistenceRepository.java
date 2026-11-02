package com.resolum.alivia.nursing.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link CareActivity} aggregates.
 */
public interface CareActivityPersistenceRepository extends JpaRepository<CareActivity, Long> {

    /**
     * Finds the activities of the given assisted persons, fetching their assignments in the same query.
     *
     * @param assistedPersonProfileIds the assisted person profile identifiers
     * @return the matching activities
     */
    @Query("select distinct a from CareActivity a left join fetch a.activityAssignments.activityAssignments "
            + "where a.assistedPersonProfileId in :assistedPersonProfileIds")
    List<CareActivity> findAllByAssistedPersonProfileIdIn(Collection<Long> assistedPersonProfileIds);

    /**
     * Finds the activity that owns the given assignment, fetching its assignments in the same query.
     *
     * @param assignmentId the assignment identifier
     * @return the activity, or empty when the assignment does not exist
     */
    @Query("select distinct a from CareActivity a left join fetch a.activityAssignments.activityAssignments "
            + "where exists (select 1 from ActivityAssignment x where x.careActivity = a and x.id = :assignmentId)")
    Optional<CareActivity> findByAssignmentId(Long assignmentId);
}
