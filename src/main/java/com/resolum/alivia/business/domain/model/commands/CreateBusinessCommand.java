package com.resolum.alivia.business.domain.model.commands;

import java.util.Arrays;
import java.util.Objects;

public record CreateBusinessCommand(
        byte[] photoData,
        String photoFileName,
        String businessName,
        String legalName,
        Long ruc,
        String mainActivity,
        String description,
        String emailAddress,
        String phoneNumber,
        String formattedAddress,
        Double latitude,
        Double longitude,
        String placeId,
        String businessHours,
        String supportEmail,
        String emergencyPhone,
        String whatsappNum,
        String slogan,
        Long administratorId
) {
    public CreateBusinessCommand {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("Legal name cannot be null or blank");
        }
        if (ruc == null || ruc <= 0) {
            throw new IllegalArgumentException("RUC must be a positive number");
        }
        if (administratorId == null || administratorId <= 0) {
            throw new IllegalArgumentException("Administrator ID must be a positive number");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateBusinessCommand that)) return false;
        return Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(businessName, that.businessName)
                && Objects.equals(legalName, that.legalName)
                && Objects.equals(ruc, that.ruc)
                && Objects.equals(mainActivity, that.mainActivity)
                && Objects.equals(description, that.description)
                && Objects.equals(emailAddress, that.emailAddress)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(formattedAddress, that.formattedAddress)
                && Objects.equals(latitude, that.latitude)
                && Objects.equals(longitude, that.longitude)
                && Objects.equals(placeId, that.placeId)
                && Objects.equals(businessHours, that.businessHours)
                && Objects.equals(supportEmail, that.supportEmail)
                && Objects.equals(emergencyPhone, that.emergencyPhone)
                && Objects.equals(whatsappNum, that.whatsappNum)
                && Objects.equals(slogan, that.slogan)
                && Objects.equals(administratorId, that.administratorId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(photoFileName, businessName, legalName, ruc, mainActivity, description,
                emailAddress, phoneNumber, formattedAddress, latitude, longitude, placeId, businessHours,
                supportEmail, emergencyPhone, whatsappNum, slogan, administratorId);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateBusinessCommand["
                + "photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", businessName=" + businessName
                + ", legalName=" + legalName
                + ", ruc=" + ruc
                + ", mainActivity=" + mainActivity
                + ", description=" + description
                + ", emailAddress=" + emailAddress
                + ", phoneNumber=" + phoneNumber
                + ", formattedAddress=" + formattedAddress
                + ", latitude=" + latitude
                + ", longitude=" + longitude
                + ", placeId=" + placeId
                + ", businessHours=" + businessHours
                + ", supportEmail=" + supportEmail
                + ", emergencyPhone=" + emergencyPhone
                + ", whatsappNum=" + whatsappNum
                + ", slogan=" + slogan
                + ", administratorId=" + administratorId + "]";
    }
}
