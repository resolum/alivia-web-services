package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body to register an assisted person in a household.
 *
 * @param email       the email of the assisted person account
 * @param password    the plain-text password of the account
 * @param firstName   the first names
 * @param lastName    the last names
 * @param phoneNumber the contact phone number
 * @param dni         the national identity document number
 * @param birthDate   the birth date
 */
@Schema(name = "RegisterAssistedPersonRequest", description = "Datos de la persona asistida que se registra en el hogar")
public record RegisterAssistedPersonResource(
        @Schema(description = "Correo electrónico de la cuenta de la persona asistida",
                example = "maria.lopez@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 254)
        String email,

        @Schema(description = "Contraseña que cumple la política de seguridad", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 72)
        String password,

        @Schema(description = "Nombres", example = "Maria Elena", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 75)
        String firstName,

        @Schema(description = "Apellidos", example = "Lopez Garcia", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 75)
        String lastName,

        @Schema(description = "Teléfono de contacto", example = "+51987654321",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @Schema(description = "DNI de 8 dígitos", example = "12345678", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "\\d{8}")
        String dni,

        @Schema(description = "Fecha de nacimiento", example = "1950-03-04", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Past
        LocalDate birthDate
) {

    /**
     * Returns a representation without the password or personal data, so the body is safe to log.
     *
     * @return a fixed description
     */
    @Override
    public String toString() {
        return "RegisterAssistedPersonResource[redacted]";
    }
}
