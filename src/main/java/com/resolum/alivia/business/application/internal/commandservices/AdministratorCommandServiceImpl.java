package com.resolum.alivia.business.application.internal.commandservices;

import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.business.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BackupPersonalEmailAlreadyExists;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.commands.CreateAdministratorCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateAdministratorCommand;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.domain.services.AdministratorCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AdministratorCommandServiceImpl implements AdministratorCommandService {

    private final AdministratorRepository administratorRepository;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;

    public AdministratorCommandServiceImpl(
            AdministratorRepository administratorRepository,
            ExternalIamService externalIamService,
            ExternalProfilesService externalProfilesService) {
        this.administratorRepository = administratorRepository;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
    }

    @Override
    @Transactional
    public Long handle(CreateAdministratorCommand command) {
        log.info("Handling CreateAdministratorCommand for corporateEmail: {}", command.corporateEmail());

        EmailAddress backupEmail = null;
        if (command.backupPersonalEmail() != null && !command.backupPersonalEmail().isBlank()) {
            backupEmail = new EmailAddress(command.backupPersonalEmail());
            if (administratorRepository.existsByBackupPersonalEmail(backupEmail)) {
                throw new BackupPersonalEmailAlreadyExists(command.backupPersonalEmail());
            }
        }

        // 1. Create StaffProfile in Profiles (validates DNI, email address uniqueness across profiles)
        StaffProfileId staffProfileId = externalProfilesService.createStaffProfile(
                command.corporateEmail(),
                command.photoData(),
                command.photoFileName(),
                command.firstName(),
                command.lastName(),
                command.dni(),
                command.birthDate(),
                command.phoneNumber()
        );

        // 2. Create IAM Account with ROLE_ADMIN (validates email uniqueness in IAM)
        Long iamUserId = externalIamService.signUpAdministrator(
                command.corporateEmail(),
                command.password(),
                command.firstName(),
                command.lastName(),
                command.phoneNumber()
        );

        // 3. Persist Administrator aggregate
        Administrator administrator = new Administrator(new UserId(iamUserId), staffProfileId, backupEmail);
        Administrator savedAdministrator = administratorRepository.save(administrator);

        log.info("Administrator created successfully with ID: {}", savedAdministrator.getId());
        return savedAdministrator.getId();
    }

    @Override
    @Transactional
    public Long handle(UpdateAdministratorCommand command) {
        log.info("Handling UpdateAdministratorCommand for administrator ID: {}", command.administratorId());

        Administrator administrator = administratorRepository.findById(command.administratorId())
                .orElseThrow(() -> new AdministratorNotFound(command.administratorId()));

        EmailAddress backupEmail = null;
        if (command.backupPersonalEmail() != null && !command.backupPersonalEmail().isBlank()) {
            backupEmail = new EmailAddress(command.backupPersonalEmail());
            if (administrator.getBackupPersonalEmail() == null ||
                    !administrator.getBackupPersonalEmail().equals(backupEmail)) {
                if (administratorRepository.existsByBackupPersonalEmail(backupEmail)) {
                    throw new BackupPersonalEmailAlreadyExists(command.backupPersonalEmail());
                }
            }
        }

        administrator.updateBackupPersonalEmail(backupEmail);

        // Update personal information in StaffProfile via ACL
        externalProfilesService.updateStaffProfile(
                administrator.getStaffProfileId().staffProfileId(),
                command.photoData(),
                command.photoFileName(),
                command.firstName(),
                command.lastName(),
                command.birthDate(),
                command.phoneNumber()
        );

        administratorRepository.save(administrator);
        log.info("Administrator updated successfully with ID: {}", administrator.getId());
        return administrator.getId();
    }
}
