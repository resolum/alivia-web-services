package com.resolum.alivia.profiles.domain.model.commands;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;

public record CreateBusinessProfileCommand(
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
        String placeId) {

    public CreateBusinessProfileCommand {
        ProfileCommandValidator.validateBusinessFields(
                emailAddress, photoUrl, photoPublicId, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, businessHours, slogan, supportEmail,
                streetAddress, latitude, longitude, placeId);
    }

    public CreateBusinessProfileCommand(
            String emailAddress, String photoUrl, String photoPublicId, String whatsappNum,
            String ruc, String businessName, String legalName, String description,
            String mainActivity, String emergencyPhone, BusinessHours businessHours,
            String slogan, String supportEmail, Address address) {
        this(emailAddress, photoUrl, photoPublicId, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, businessHours, slogan, supportEmail,
                address != null ? address.formattedAddress() : null,
                address != null ? address.latitude() : null,
                address != null ? address.longitude() : null,
                address != null ? address.placeId() : null);
    }
}
