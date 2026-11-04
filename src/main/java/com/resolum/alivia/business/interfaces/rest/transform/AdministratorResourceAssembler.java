package com.resolum.alivia.business.interfaces.rest.transform;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.commands.CreateAdministratorCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateAdministratorCommand;
import com.resolum.alivia.business.interfaces.rest.resources.AdministratorResource;
import com.resolum.alivia.business.interfaces.rest.resources.CreateAdministratorResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateAdministratorResource;

public final class AdministratorResourceAssembler {

    private AdministratorResourceAssembler() {
    }

    public static AdministratorResource toResourceFromEntity(Administrator entity) {
        if (entity == null) {
            return null;
        }
        return new AdministratorResource(
                entity.getId(),
                entity.getUserId() != null ? entity.getUserId().userId() : null,
                entity.getStaffProfileId() != null ? entity.getStaffProfileId().staffProfileId() : null,
                entity.getBackupPersonalEmail() != null ? entity.getBackupPersonalEmail().email() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static CreateAdministratorCommand toCommandFromResource(CreateAdministratorResource resource) {
        return new CreateAdministratorCommand(
                resource.corporateEmail(),
                resource.password(),
                resource.photoData(),
                resource.photoFileName(),
                resource.firstName(),
                resource.lastName(),
                resource.dni(),
                resource.birthDate(),
                resource.phoneNumber(),
                resource.backupPersonalEmail()
        );
    }

    public static UpdateAdministratorCommand toCommandFromResource(Long administratorId, UpdateAdministratorResource resource) {
        return new UpdateAdministratorCommand(
                administratorId,
                resource.photoData(),
                resource.photoFileName(),
                resource.firstName(),
                resource.lastName(),
                resource.birthDate(),
                resource.phoneNumber(),
                resource.backupPersonalEmail()
        );
    }
}
