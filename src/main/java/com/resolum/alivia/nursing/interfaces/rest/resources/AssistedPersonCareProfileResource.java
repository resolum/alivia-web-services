package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Care profile of the assisted person registered by the authenticated client.
 *
 * @param id                    the profile identifier
 * @param householdId           the household, if linked
 * @param firstName             the first names
 * @param lastName              the last names
 * @param birthDate             the birth date
 * @param documentType          the document type, if any
 * @param documentNumber        the document number, if any
 * @param kinship               what the assisted person is to the principal caregiver
 * @param phoneNumber           the contact phone, if any
 * @param mobilityLevel         the general mobility
 * @param emergencyContactName  the secondary emergency contact name, if any
 * @param emergencyContactPhone the secondary emergency contact phone, if any
 * @param consentGivenAt        when the client last confirmed the consent
 */
@Schema(name = "AssistedPersonCareProfile", description = "Perfil de la persona asistida registrado al contratar")
public record AssistedPersonCareProfileResource(Long id, Long householdId, String firstName, String lastName,
                                                LocalDate birthDate, String documentType, String documentNumber,
                                                String kinship, String phoneNumber, String mobilityLevel,
                                                String emergencyContactName, String emergencyContactPhone,
                                                Instant consentGivenAt) {
}
