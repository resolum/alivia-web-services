package com.resolum.alivia.business.interfaces.rest.transform;

import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.commands.CreateBusinessCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateBusinessCommand;
import com.resolum.alivia.business.interfaces.rest.resources.BusinessResource;
import com.resolum.alivia.business.interfaces.rest.resources.CreateBusinessResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateBusinessResource;

public final class BusinessResourceAssembler {

    private BusinessResourceAssembler() {
    }

    public static BusinessResource toResourceFromEntity(Business entity) {
        if (entity == null) {
            return null;
        }
        return new BusinessResource(
                entity.getId(),
                entity.getBusinessProfileId() != null ? entity.getBusinessProfileId().businessProfileId() : null,
                entity.getAdministrator() != null ? entity.getAdministrator().getId() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static CreateBusinessCommand toCommandFromResource(CreateBusinessResource resource) {
        return new CreateBusinessCommand(
                resource.photoData(),
                resource.photoFileName(),
                resource.businessName(),
                resource.legalName(),
                resource.ruc(),
                resource.mainActivity(),
                resource.description(),
                resource.emailAddress(),
                resource.phoneNumber(),
                resource.formattedAddress(),
                resource.latitude(),
                resource.longitude(),
                resource.placeId(),
                resource.businessHours(),
                resource.supportEmail(),
                resource.emergencyPhone(),
                resource.whatsappNum(),
                resource.slogan(),
                resource.administratorId()
        );
    }

    public static UpdateBusinessCommand toCommandFromResource(Long businessId, UpdateBusinessResource resource) {
        return new UpdateBusinessCommand(
                businessId,
                resource.administratorId(),
                resource.photoData(),
                resource.photoFileName(),
                resource.businessName(),
                resource.legalName(),
                resource.mainActivity(),
                resource.description(),
                resource.phoneNumber(),
                resource.businessHours(),
                resource.supportEmail(),
                resource.emergencyPhone(),
                resource.whatsappNum(),
                resource.slogan()
        );
    }
}
