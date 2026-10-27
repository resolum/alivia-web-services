package com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkSchedulePersistenceRepository
        extends JpaRepository<WorkSchedule, Long>, JpaSpecificationExecutor<WorkSchedule> {

    /** {@code endDate} must not be null: open-ended periods are passed as a far-future date. */
    @Query("""
            select (count(w) > 0) from WorkSchedule w
            where w.employeeId = :employeeId
              and w.status = com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus.ACTIVE
              and w.startDate <= :endDate
              and (w.endDate is null or w.endDate >= :startDate)
              and (:excludedId is null or w.id <> :excludedId)
            """)
    boolean existsActiveOverlapping(@Param("employeeId") Long employeeId,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("excludedId") Long excludedId);

    List<WorkSchedule> findByBusinessIdAndStatus(Long businessId, WorkScheduleStatus status);
}
