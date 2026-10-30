package com.resolum.alivia.business.infrastructure.adapters;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.infrastructure.persistence.jpa.repositories.AdministratorPersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdministratorRepositoryImpl implements AdministratorRepository {

    private final AdministratorPersistenceRepository persistenceRepository;

    public AdministratorRepositoryImpl(AdministratorPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<Administrator> findById(Long id) {
        return persistenceRepository.findById(id);
    }

    @Override
    public Optional<Administrator> findByUserId(UserId userId) {
        return persistenceRepository.findByUserId(userId);
    }

    @Override
    public Optional<Administrator> findByStaffProfileId(StaffProfileId staffProfileId) {
        return persistenceRepository.findByStaffProfileId(staffProfileId);
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        return persistenceRepository.existsByUserId(userId);
    }

    @Override
    public boolean existsByStaffProfileId(StaffProfileId staffProfileId) {
        return persistenceRepository.existsByStaffProfileId(staffProfileId);
    }

    @Override
    public boolean existsByBackupPersonalEmail(EmailAddress backupPersonalEmail) {
        if (backupPersonalEmail == null) {
            return false;
        }
        return persistenceRepository.existsByBackupPersonalEmail(backupPersonalEmail);
    }

    @Override
    public Administrator save(Administrator administrator) {
        return persistenceRepository.save(administrator);
    }
}
