package com.resolum.alivia.business.domain.model.commands;

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
}
