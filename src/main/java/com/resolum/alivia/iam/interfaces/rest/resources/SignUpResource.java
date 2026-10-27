package com.resolum.alivia.iam.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Multipart form of the sign-up endpoint.
 *
 * <p>{@code firstName}, {@code lastName} and {@code phoneNumber} are the profile data of the new user.
 * The application service forwards them to the Profiles bounded context through the
 * IAM anti-corruption layer after the account is persisted.</p>
 *
 * @param firstName     the first names forwarded to the Profiles context
 * @param lastName      the last names forwarded to the Profiles context
 * @param email         the account email address
 * @param phoneNumber   the phone number forwarded to the Profiles context
 * @param password      the plain-text password
 * @param acceptedTerms whether the user accepted the terms and conditions
 * @param role          the access role
 * @param businessId    the optional business identifier
 * @param familiarId    the optional familiar identifier
 * @param dni           the DNI, required for caregivers, technicians and assisted persons
 * @param birthDate     the birth date, required for caregivers, technicians and assisted persons
 * @param photo         the optional profile photo, uploaded to Cloudinary; a default photo is used when absent
 */
@Schema(name = "SignUpRequest", description = "Datos necesarios para registrar una cuenta")
public record SignUpResource(
        @Schema(description = "Nombres del usuario", example = "Maria Elena",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 75)
        String firstName,

        @Schema(description = "Apellidos del usuario", example = "Lopez Garcia",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 75)
        String lastName,

        @Schema(description = "Correo electrónico de la cuenta", example = "maria.lopez@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 254)
        String email,

        @Schema(description = "Teléfono de contacto", example = "+51987654321",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @Schema(description = "Contraseña que cumple la política de seguridad",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 72)
        String password,

        @Schema(description = "Aceptación de los términos y condiciones", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @AssertTrue(message = "terms_not_accepted")
        Boolean acceptedTerms,

        @Schema(description = "Rol de acceso del usuario (ADMINISTRATOR, CAREGIVER/FAMILIAR, TECHNICIAN/EMPLOYEE, ASSISTED_PERSON)", example = "CAREGIVER")
        String role,

        @Schema(description = "Identificador del negocio (para empleados o familiares asociados)", example = "1")
        Long businessId,

        @Schema(description = "Identificador del familiar (para persona asistida)", example = "1")
        Long familiarId,

        @Schema(description = "DNI de 8 dígitos (requerido para CAREGIVER, TECHNICIAN y ASSISTED_PERSON)",
                example = "12345678")
        @Pattern(regexp = "\\d{8}")
        String dni,

        @Schema(description = "Fecha de nacimiento (requerida para CAREGIVER, TECHNICIAN y ASSISTED_PERSON)",
                example = "1990-01-15")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDate,

        @Schema(description = "Foto de perfil (imagen); sin ella se usa la foto por defecto",
                type = "string", format = "binary")
        MultipartFile photo
) {
}
