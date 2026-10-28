package com.resolum.alivia.business.domain.model.commands;

import java.util.Arrays;
import java.util.Objects;

public record UpdateBusinessCommand(
        Long businessId,
        Long administratorId,
        byte[] photoData,
        String photoFileName,
        String businessName,
        String legalName,
        String mainActivity,
        String description,
        String phoneNumber,
        String businessHours,
        String supportEmail,
        String emergencyPhone,
        String whatsappNum,
        String slogan
) {
    public UpdateBusinessCommand {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be a positive number");
        }
        if (administratorId == null || administratorId <= 0) {
            throw new IllegalArgumentException("Administrator ID must be a positive number");
        }
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("Business name cannot be null or blank");
        }
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("Legal name cannot be null or blank");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateBusinessCommand that)) return false;
        return Objects.equals(businessId, that.businessId)
                && Objects.equals(administratorId, that.administratorId)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(businessName, that.businessName)
                && Objects.equals(legalName, that.legalName)
                && Objects.equals(mainActivity, that.mainActivity)
                && Objects.equals(description, that.description)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(businessHours, that.businessHours)
                && Objects.equals(supportEmail, that.supportEmail)
                && Objects.equals(emergencyPhone, that.emergencyPhone)
                && Objects.equals(whatsappNum, that.whatsappNum)
                && Objects.equals(slogan, that.slogan);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(businessId, administratorId, photoFileName, businessName, legalName,
                mainActivity, description, phoneNumber, businessHours, supportEmail, emergencyPhone,
                whatsappNum, slogan);
        return 31 * result + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateBusinessCommand["
                + "businessId=" + businessId
                + ", administratorId=" + administratorId
                + ", photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", photoFileName=" + photoFileName
                + ", businessName=" + businessName
                + ", legalName=" + legalName
                + ", mainActivity=" + mainActivity
                + ", description=" + description
                + ", phoneNumber=" + phoneNumber
                + ", businessHours=" + businessHours
                + ", supportEmail=" + supportEmail
                + ", emergencyPhone=" + emergencyPhone
                + ", whatsappNum=" + whatsappNum
                + ", slogan=" + slogan + "]";
    }
}
