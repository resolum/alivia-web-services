package com.resolum.alivia.profiles.domain.model.commands;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;

import java.util.Arrays;
import java.util.Objects;

public record CreateBusinessProfileCommand(
        String emailAddress,
        byte[] photoData,
        String photoFileName,
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
                emailAddress, photoData, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, businessHours, slogan, supportEmail,
                streetAddress, latitude, longitude, placeId, false);
    }

    public CreateBusinessProfileCommand(
            String emailAddress, byte[] photoData, String photoFileName, String whatsappNum,
            String ruc, String businessName, String legalName, String description,
            String mainActivity, String emergencyPhone, BusinessHours businessHours,
            String slogan, String supportEmail, Address address) {
        this(emailAddress, photoData, photoFileName, whatsappNum, ruc, businessName, legalName,
                description, mainActivity, emergencyPhone, businessHours, slogan, supportEmail,
                address != null ? address.formattedAddress() : null,
                address != null ? address.latitude() : null,
                address != null ? address.longitude() : null,
                address != null ? address.placeId() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateBusinessProfileCommand that)) return false;
        return Objects.equals(emailAddress, that.emailAddress)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(whatsappNum, that.whatsappNum)
                && Objects.equals(ruc, that.ruc)
                && Objects.equals(businessName, that.businessName)
                && Objects.equals(legalName, that.legalName)
                && Objects.equals(description, that.description)
                && Objects.equals(mainActivity, that.mainActivity)
                && Objects.equals(emergencyPhone, that.emergencyPhone)
                && Objects.equals(businessHours, that.businessHours)
                && Objects.equals(slogan, that.slogan)
                && Objects.equals(supportEmail, that.supportEmail)
                && Objects.equals(streetAddress, that.streetAddress)
                && Objects.equals(latitude, that.latitude)
                && Objects.equals(longitude, that.longitude)
                && Objects.equals(placeId, that.placeId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(emailAddress, photoFileName, whatsappNum, ruc,
                businessName, legalName, description, mainActivity, emergencyPhone, businessHours,
                slogan, supportEmail, streetAddress, latitude, longitude, placeId);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateBusinessProfileCommand["
                + "emailAddress=" + emailAddress
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", whatsappNum=" + whatsappNum
                + ", ruc=" + ruc
                + ", businessName=" + businessName
                + ", legalName=" + legalName
                + ", description=" + description
                + ", mainActivity=" + mainActivity
                + ", emergencyPhone=" + emergencyPhone
                + ", businessHours=" + businessHours
                + ", slogan=" + slogan
                + ", supportEmail=" + supportEmail
                + ", streetAddress=" + streetAddress
                + ", latitude=" + latitude
                + ", longitude=" + longitude
                + ", placeId=" + placeId + "]";
    }
}
