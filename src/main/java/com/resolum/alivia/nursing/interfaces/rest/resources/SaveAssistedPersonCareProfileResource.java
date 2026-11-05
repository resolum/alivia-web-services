package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body to save the care profile of the assisted person during contracting.
 *
 * @param householdId           the household, when it already exists
 * @param firstName             the first names
 * @param lastName              the last names
 * @param birthDate             the birth date
 * @param documentType          DNI, CE or PASSPORT; optional
 * @param documentNumber        the document number; required with the type
 * @param kinship               what the assisted person is to the principal caregiver
 * @param phoneNumber           the contact phone as E.164 digits without plus; optional
 * @param mobilityLevel         VERY_LIMITED, NO_LIMB_MOBILITY or PARTIAL
 * @param emergencyContactName  the secondary emergency contact name; optional
 * @param emergencyContactPhone the secondary emergency contact phone; required with the name
 * @param consentGiven          the client confirms they are authorized to share the data
 */
@Schema(name = "SaveAssistedPersonCareProfileRequest",
        description = "Datos de la persona asistida que se registran al contratar, sin cuenta propia")
public record SaveAssistedPersonCareProfileResource(
        @Schema(description = "Hogar, si ya existe", example = "1", nullable = true)
        Long householdId,

        @Schema(description = "Nombres", example = "Alberto", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 75)
        String firstName,

        @Schema(description = "Apellidos", example = "Villanueva Ríos", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 75)
        String lastName,

        @Schema(description = "Fecha de nacimiento", example = "1942-08-14", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Past
        LocalDate birthDate,

        @Schema(description = "Tipo de documento", example = "DNI", allowableValues = {"DNI", "CE", "PASSPORT"},
                nullable = true)
        String documentType,

        @Schema(description = "Número de documento", example = "09482154", nullable = true)
        @Size(max = 12)
        String documentNumber,

        @Schema(description = "Relación con el cuidador principal", example = "PARENT",
                allowableValues = {"PARENT", "GRANDPARENT", "AUNT_UNCLE", "SPOUSE", "SIBLING", "OTHER"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String kinship,

        @Schema(description = "Teléfono de contacto en dígitos E.164 sin +", example = "51987654321", nullable = true)
        @Size(max = 15)
        String phoneNumber,

        @Schema(description = "Nivel de movilidad general", example = "PARTIAL",
                allowableValues = {"VERY_LIMITED", "NO_LIMB_MOBILITY", "PARTIAL"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String mobilityLevel,

        @Schema(description = "Nombre del contacto secundario de emergencia", nullable = true)
        @Size(max = 150)
        String emergencyContactName,

        @Schema(description = "Teléfono del contacto secundario de emergencia", example = "51912345678",
                nullable = true)
        @Size(max = 15)
        String emergencyContactPhone,

        @Schema(description = "Confirma la autorización de la persona asistida o su representante",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean consentGiven
) {

    /**
     * Returns a representation without personal data, so the body is safe to log.
     *
     * @return a fixed description
     */
    @Override
    public String toString() {
        return "SaveAssistedPersonCareProfileResource[redacted]";
    }
}
