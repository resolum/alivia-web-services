package com.resolum.alivia.profiles.interfaces.rest.resources;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Recurso para la creación de un perfil comercial (empresa / clínica / centro)")
public record CreateBusinessProfileResource(
        @Schema(description = "Correo electrónico principal del negocio", example = "contacto@clinicasanpablo.com")
        String emailAddress,

        @Schema(description = "Logo o foto comercial (archivo de imagen)", type = "string", format = "binary")
        MultipartFile photo,

        @Schema(description = "Número de WhatsApp corporativo en formato E.164 sin prefijo +", example = "51999888777")
        String whatsappNum,

        @Schema(description = "Registro Único de Contribuyentes (RUC de 11 dígitos numéricos)", example = "20601234567")
        String ruc,

        @Schema(description = "Nombre comercial o de marca del establecimiento", example = "Clínica San Pablo Sur")
        String businessName,

        @Schema(description = "Razón social registrada ante la entidad tributaria", example = "San Pablo Servicios Médicos S.A.C.")
        String legalName,

        @Schema(description = "Descripción corporativa de la empresa y sus servicios", example = "Centro médico especializado en atención ambulatoria, medicina preventiva y bienestar integral.")
        String description,

        @Schema(description = "Actividad comercial o rubro principal", example = "Servicios de atención médica ambulatoria y diagnóstico")
        String mainActivity,

        @Schema(description = "Teléfono de emergencias o guardia en formato E.164 sin prefijo +", example = "5112345678")
        String emergencyPhone,

        @Schema(description = "Horario semanal en formato JSON string o representación de BusinessHours", example = "{\"periods\":[{\"days\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],\"range\":{\"start\":\"08:00:00\",\"end\":\"18:00:00\"}}]}")
        BusinessHours businessHours,

        @Schema(description = "Lema o eslogan comercial", example = "Cuidando tu bienestar cada día")
        String slogan,

        @Schema(description = "Correo electrónico para atención al cliente y soporte", example = "soporte@clinicasanpablo.com")
        String supportEmail,

        @Schema(description = "Dirección física detallada del establecimiento", example = "Av. El Derby 250, Santiago de Surco, Lima")
        String streetAddress,

        @Schema(description = "Latitud geográfica en grados decimales (-90 a 90)", example = "-12.109722")
        Double latitude,

        @Schema(description = "Longitud geográfica en grados decimales (-180 a 180)", example = "-76.974444")
        Double longitude,

        @Schema(description = "Identificador único de Google Maps / Google Places", example = "ChIJw8gA1j_JBZERSF7Yf_Q8lJc")
        String placeId
) {
}
