package com.resolum.alivia.business.domain.repositories;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Optional;

public interface AdministratorRepository {
    Optional<Administrator> findById(Long id);
    Optional<Administrator> findByUserId(UserId userId);
    Optional<Administrator> findByStaffProfileId(StaffProfileId staffProfileId);
    boolean existsByUserId(UserId userId);
    boolean existsByStaffProfileId(StaffProfileId staffProfileId);
    boolean existsByBackupPersonalEmail(EmailAddress backupPersonalEmail);
    Administrator save(Administrator administrator);
}
