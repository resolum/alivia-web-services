package com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.hcm.domain.model.entities.EmployeeChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeChangeLogRepository extends JpaRepository<EmployeeChangeLog, Long> {
}
