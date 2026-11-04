package com.resolum.alivia.business.interfaces.rest.resources;

import java.util.Arrays;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Recurso para el registro de un nuevo administrador de negocio")
public record CreateAdministratorResource(
        @NotBlank(message = "Corporate email cannot be blank")
        @Email(message = "Corporate email must be a valid email address")
        @Schema(description = "Correo electrónico corporativo para inicio de sesión en el sistema", example = "admin@clinicasanpablo.com")
        String corporateEmail,

        @NotBlank(message = "Password cannot be blank")
        @Schema(description = "Contraseña de acceso para la cuenta del administrador")
        String password,

        @Schema(description = "Bytes del archivo de imagen para foto de perfil (opcional, codificado en base64 en JSON)", nullable = true)
        byte[] photoData,

        @Schema(description = "Nombre original del archivo de foto de perfil", example = "admin-avatar.png", nullable = true)
        String photoFileName,

        @NotBlank(message = "First name cannot be blank")
        @Schema(description = "Nombres del administrador", example = "Carlos Alberto")
        String firstName,

        @NotBlank(message = "Last name cannot be blank")
        @Schema(description = "Apellidos del administrador", example = "Mendoza Ruiz")
        String lastName,

        @NotBlank(message = "DNI cannot be blank")
        @Schema(description = "Documento Nacional de Identidad (8 dígitos numéricos)", example = "45678912")
        String dni,

        @NotNull(message = "Birth date is required")
        @Schema(description = "Fecha de nacimiento (formato ISO-8601: YYYY-MM-DD)", example = "1988-04-12")
        LocalDate birthDate,

        @Schema(description = "Número telefónico de contacto en formato E.164 sin prefijo +", example = "51976543210")
        String phoneNumber,

        @Schema(description = "Correo electrónico personal de respaldo o recuperación", example = "carlos.mendoza.personal@gmail.com")
        String backupPersonalEmail
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateAdministratorResource that)) return false;
        return Objects.equals(corporateEmail, that.corporateEmail)
                && Objects.equals(password, that.password)
                && Arrays.equals(photoData, that.photoData)
                && Objects.equals(photoFileName, that.photoFileName)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(dni, that.dni)
                && Objects.equals(birthDate, that.birthDate)
                && Objects.equals(phoneNumber, that.phoneNumber)
                && Objects.equals(backupPersonalEmail, that.backupPersonalEmail);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(corporateEmail, password, photoFileName, firstName, lastName, dni, birthDate, phoneNumber, backupPersonalEmail) + Arrays.hashCode(photoData);
    }

    @Override
    public String toString() {
        return "CreateAdministratorResource[" + "corporateEmail=" + corporateEmail
                + ", " + "photoData=" + (photoData == null ? "null" : photoData.length + " bytes")
                + ", " + "photoFileName=" + photoFileName
                + ", " + "firstName=" + firstName
                + ", " + "lastName=" + lastName
                + ", " + "dni=" + dni
                + ", " + "birthDate=" + birthDate
                + ", " + "phoneNumber=" + phoneNumber
                + ", " + "backupPersonalEmail=" + backupPersonalEmail + "]";
    }
}
