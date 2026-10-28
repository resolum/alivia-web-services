package com.resolum.alivia.business.domain.model.commands;

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
}
