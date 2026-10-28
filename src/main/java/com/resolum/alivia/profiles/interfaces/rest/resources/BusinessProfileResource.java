package com.resolum.alivia.profiles.interfaces.rest.resources;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Información del perfil comercial")
public record BusinessProfileResource(
        @Schema(description = "Identificador único del perfil comercial", example = "1")
        Long id,

        @Schema(description = "Correo electrónico principal del negocio", example = "contacto@clinicasanpablo.com")
        String emailAddress,

        @Schema(description = "URL pública del logo o imagen comercial", example = "https://res.cloudinary.com/alivia/image/upload/v12345/business_logo.jpg")
        String photoUrl,

        @Schema(description = "ID público del archivo en Cloudinary", example = "profiles/business/logo123")
        String photoPublicId,

        @Schema(description = "Número de WhatsApp corporativo", example = "51999888777")
        String whatsappNum,

        @Schema(description = "RUC de 11 dígitos", example = "20601234567")
        String ruc,

        @Schema(description = "Nombre comercial", example = "Clínica San Pablo Sur")
        String businessName,

        @Schema(description = "Razón social", example = "San Pablo Servicios Médicos S.A.C.")
        String legalName,

        @Schema(description = "Descripción corporativa", example = "Centro de salud especializado en atención ambulatoria y bienestar integral.")
        String description,

        @Schema(description = "Actividad comercial principal", example = "Servicios de atención médica ambulatoria y diagnóstico")
        String mainActivity,

        @Schema(description = "Teléfono de emergencias o atención comercial", example = "5112345678")
        String businessPhone,

        @Schema(description = "Horario semanal de atención comercial")
        BusinessHours businessHours,

        @Schema(description = "Eslogan corporativo", example = "Cuidando tu bienestar cada día")
        String slogan,

        @Schema(description = "Correo electrónico de soporte", example = "soporte@clinicasanpablo.com")
        String supportEmail,

        @Schema(description = "Dirección y geolocalización del establecimiento")
        Address address
) {
}
