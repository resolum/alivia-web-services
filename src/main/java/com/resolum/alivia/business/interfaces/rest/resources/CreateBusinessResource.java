package com.resolum.alivia.business.interfaces.rest.resources;

import java.util.Arrays;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Recurso para la creación de una empresa o centro de salud")
public record CreateBusinessResource(
        @Schema(description = "Bytes del archivo de imagen del logo (opcional, codificado en base64)", nullable = true)
        byte[] photoData,

        @Schema(description = "Nombre original del archivo de imagen del logo", example = "logo-clinica.png", nullable = true)
        String photoFileName,

        @NotBlank(message = "Business name cannot be blank")
        @Schema(description = "Nombre comercial o de marca de la empresa", example = "Clínica San Pablo Sur")
        String businessName,

        @NotBlank(message = "Legal name cannot be blank")
        @Schema(description = "Razón social registrada ante la entidad tributaria", example = "San Pablo Servicios Médicos S.A.C.")
        String legalName,

        @NotNull(message = "RUC is required")
        @Positive(message = "RUC must be positive")
        @Schema(description = "RUC de la empresa (11 dígitos numéricos)", example = "20601234567")
        Long ruc,

        @Schema(description = "Actividad comercial o rubro principal", example = "Servicios de atención médica ambulatoria y bienestar")
        String mainActivity,

        @Schema(description = "Descripción corporativa de la empresa", example = "Centro de salud especializado en atención médica integral y diagnóstico preventivo.")
        String description,

        @Schema(description = "Correo electrónico principal del negocio", example = "contacto@clinicasanpablo.com")
        String emailAddress,

        @Schema(description = "Número telefónico comercial en formato E.164 sin prefijo +", example = "5112345678")
        String phoneNumber,

        @Schema(description = "Dirección física detallada del establecimiento", example = "Av. El Derby 250, Santiago de Surco, Lima")
        String formattedAddress,

        @Schema(description = "Latitud geográfica en coordenadas decimales (-90 a 90)", example = "-12.109722")
        Double latitude,

        @Schema(description = "Longitud geográfica en coordenadas decimales (-180 a 180)", example = "-76.974444")
        Double longitude,

        @Schema(description = "Identificador único de Google Maps / Google Places", example = "ChIJw8gA1j_JBZERSF7Yf_Q8lJc")
        String placeId,

        @Schema(description = "Horario comercial en formato JSON string o texto descriptivo", example = "{\"periods\":[{\"days\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],\"range\":{\"start\":\"08:00:00\",\"end\":\"18:00:00\"}}]}")
        String businessHours,

        @Schema(description = "Correo electrónico para soporte y atención al cliente", example = "soporte@clinicasanpablo.com")
        String supportEmail,

        @Schema(description = "Teléfono de emergencias o guardia en formato E.164 sin prefijo +", example = "5112345678")
        String emergencyPhone,

        @Schema(description = "Número de WhatsApp corporativo en formato E.164 sin prefijo +", example = "51999888777")
        String whatsappNum,

        @Schema(description = "Lema o eslogan comercial", example = "Cuidando tu bienestar cada día")
        String slogan,

        @NotNull(message = "Administrator ID is required")
        @Positive(message = "Administrator ID must be positive")
        @Schema(description = "Identificador del administrador previamente registrado para este negocio", example = "1")
        Long administratorId
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateBusinessResource that)) return false;
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
        return 31 * Objects.hash(photoFileName, businessName, legalName, ruc, mainActivity, description, emailAddress, phoneNumber, formattedAddress, latitude, longitude, placeId, businessHours, supportEmail, emergencyPhone, whatsappNum, slogan, administratorId) + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateBusinessResource[" + "photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", " + "photoFileName=" + photoFileName
                + ", " + "businessName=" + businessName
                + ", " + "legalName=" + legalName
                + ", " + "ruc=" + ruc
                + ", " + "mainActivity=" + mainActivity
                + ", " + "description=" + description
                + ", " + "emailAddress=" + emailAddress
                + ", " + "phoneNumber=" + phoneNumber
                + ", " + "formattedAddress=" + formattedAddress
                + ", " + "latitude=" + latitude
                + ", " + "longitude=" + longitude
                + ", " + "placeId=" + placeId
                + ", " + "businessHours=" + businessHours
                + ", " + "supportEmail=" + supportEmail
                + ", " + "emergencyPhone=" + emergencyPhone
                + ", " + "whatsappNum=" + whatsappNum
                + ", " + "slogan=" + slogan
                + ", " + "administratorId=" + administratorId + "]";
    }
}
