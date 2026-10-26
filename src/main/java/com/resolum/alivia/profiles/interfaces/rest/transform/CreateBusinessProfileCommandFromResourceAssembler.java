package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreateBusinessProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateBusinessProfileResource;

public final class CreateBusinessProfileCommandFromResourceAssembler {
    private CreateBusinessProfileCommandFromResourceAssembler() {
    }

    public static CreateBusinessProfileCommand toCommandFromResource(CreateBusinessProfileResource resource) {
        return new CreateBusinessProfileCommand(
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
