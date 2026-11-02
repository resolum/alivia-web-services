package com.resolum.alivia.profiles.interfaces.rest.resources;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;

public record BusinessProfileResource(
        Long id,
        String emailAddress,
        String photoUrl,
        String photoPublicId,
        String whatsappNum,
        String ruc,
        String businessName,
        String legalName,
        String description,
        String mainActivity,
        String businessPhone,
        BusinessHours businessHours,
        String slogan,
        String supportEmail,
        Address address
) {
}
