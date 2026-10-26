package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdateBusinessProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateBusinessProfileResource;

public final class UpdateBusinessProfileCommandFromResourceAssembler {
    private UpdateBusinessProfileCommandFromResourceAssembler() {
    }

    public static UpdateBusinessProfileCommand toCommandFromResource(Long profileId, UpdateBusinessProfileResource resource) {
        return new UpdateBusinessProfileCommand(
                profileId,
                resource.emailAddress(),
                resource.photoUrl(),
                resource.photoPublicId(),
                resource.whatsappNum(),
                resource.ruc(),
                resource.businessName(),
                resource.legalName(),
                resource.description(),
                resource.mainActivity(),
                resource.emergencyPhone(),
                resource.businessHours(),
                resource.slogan(),
                resource.supportEmail(),
                resource.streetAddress(),
                resource.latitude(),
                resource.longitude(),
                resource.placeId()
        );
    }
}
