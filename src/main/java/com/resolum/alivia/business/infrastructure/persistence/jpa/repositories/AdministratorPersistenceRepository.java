package com.resolum.alivia.business.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdministratorPersistenceRepository extends JpaRepository<Administrator, Long> {
    Optional<Administrator> findByUserId(UserId userId);

    Optional<Administrator> findByStaffProfileId(StaffProfileId staffProfileId);

    boolean existsByUserId(UserId userId);

    boolean existsByStaffProfileId(StaffProfileId staffProfileId);

    boolean existsByBackupPersonalEmail(EmailAddress backupPersonalEmail);
}
