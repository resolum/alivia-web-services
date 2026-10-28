package com.resolum.alivia.business.interfaces.rest.resources;

import java.util.Arrays;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Recurso para la actualización de un negocio existente")
public record UpdateBusinessResource(
        @NotNull(message = "Administrator ID is required")
        @Positive(message = "Administrator ID must be positive")
        @Schema(description = "Identificador del administrador asignado al negocio", example = "1")
        Long administratorId,

        @Schema(description = "Bytes del archivo de imagen para actualizar logo (opcional, base64)", nullable = true)
        byte[] photoData,

        @Schema(description = "Nombre original del archivo de imagen del logo", example = "logo-clinica-updated.png", nullable = true)
        String photoFileName,

        @NotBlank(message = "Business name cannot be blank")
        @Schema(description = "Nombre comercial o de marca de la empresa", example = "Clínica San Pablo Sur")
        String businessName,

        @NotBlank(message = "Legal name cannot be blank")
        @Schema(description = "Razón social registrada ante la entidad tributaria", example = "San Pablo Servicios Médicos S.A.C.")
        String legalName,

        @Schema(description = "Actividad comercial o rubro principal", example = "Servicios de atención médica ambulatoria y bienestar")
        String mainActivity,

        @Schema(description = "Descripción corporativa de la empresa", example = "Centro médico especializado en atención integral y preventiva.")
        String description,

        @Schema(description = "Número telefónico comercial en formato E.164 sin prefijo +", example = "5112345678")
        String phoneNumber,

        @Schema(description = "Horario comercial en formato JSON string o texto descriptivo", example = "{\"periods\":[{\"days\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],\"range\":{\"start\":\"08:00:00\",\"end\":\"18:00:00\"}}]}")
        String businessHours,

        @Schema(description = "Correo electrónico para soporte y atención al cliente", example = "soporte@clinicasanpablo.com")
        String supportEmail,

        @Schema(description = "Teléfono de emergencias o guardia en formato E.164 sin prefijo +", example = "5112345678")
        String emergencyPhone,

        @Schema(description = "Número de WhatsApp corporativo en formato E.164 sin prefijo +", example = "51999888777")
        String whatsappNum,

        @Schema(description = "Lema o eslogan comercial", example = "Cuidando tu bienestar cada día")
        String slogan
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateBusinessResource that)) return false;
        return Objects.equals(administratorId, that.administratorId)
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
        return 31 * Objects.hash(administratorId, photoFileName, businessName, legalName, mainActivity, description, phoneNumber, businessHours, supportEmail, emergencyPhone, whatsappNum, slogan) + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "UpdateBusinessResource[" + "administratorId=" + administratorId
                + ", " + "photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", " + "photoFileName=" + photoFileName
                + ", " + "businessName=" + businessName
                + ", " + "legalName=" + legalName
                + ", " + "mainActivity=" + mainActivity
                + ", " + "description=" + description
                + ", " + "phoneNumber=" + phoneNumber
                + ", " + "businessHours=" + businessHours
                + ", " + "supportEmail=" + supportEmail
                + ", " + "emergencyPhone=" + emergencyPhone
                + ", " + "whatsappNum=" + whatsappNum
                + ", " + "slogan=" + slogan + "]";
    }
}
