package com.resolum.alivia.profiles.interfaces.rest.resources;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;

public record CreateBusinessProfileResource(
        String emailAddress,
        String photoUrl,
        String photoPublicId,
        String whatsappNum,
        String ruc,
        String businessName,
        String legalName,
        String description,
        String mainActivity,
        String emergencyPhone,
        BusinessHours businessHours,
        String slogan,
        String supportEmail,
        String streetAddress,
        Double latitude,
        Double longitude,
        String placeId
) {
}
