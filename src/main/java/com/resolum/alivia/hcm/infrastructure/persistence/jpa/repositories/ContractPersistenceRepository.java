package com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ContractPersistenceRepository
        extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    boolean existsByCodeIgnoreCase(String code);

    List<Contract> findByEmployeeId(Long employeeId);

    @Query("""
            select (count(c) > 0) from Contract c
            where c.employeeId = :employeeId
              and c.status in :statuses
              and c.startDate <= :endDate
              and c.endDate >= :startDate
              and (:excludedId is null or c.id <> :excludedId)
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("statuses") Collection<ContractStatus> statuses,
                              @Param("excludedId") Long excludedId);
}
