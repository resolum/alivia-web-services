package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.nursing.domain.model.valueobjects.IdentityDocumentType;
import com.resolum.alivia.nursing.domain.model.valueobjects.KinshipToCaregiver;
import com.resolum.alivia.nursing.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Command to create or replace the care profile of the assisted person of a responsible client.
 *
 * @param requesterUserId       the responsible client
 * @param householdId           the household, when it already exists; may be {@code null}
 * @param firstName             the first names
 * @param lastName              the last names
 * @param birthDate             the birth date, in the past
 * @param documentType          the identity document type; {@code null} when no document is given
 * @param documentNumber        the identity document number; {@code null} when no document is given
 * @param kinship               what the assisted person is to the principal caregiver
 * @param phoneNumber           the contact phone as E.164 digits without plus; may be {@code null}
 * @param mobilityLevel         the general mobility
 * @param emergencyContactName  the secondary emergency contact name; may be {@code null}
 * @param emergencyContactPhone the secondary emergency contact phone; required with the name
 * @param consentGiven          whether the client confirmed they are authorized to share the data
 */
public record SaveAssistedPersonCareProfileCommand(UserId requesterUserId, Long householdId, String firstName,
                                                   String lastName, LocalDate birthDate,
                                                   IdentityDocumentType documentType, String documentNumber,
                                                   KinshipToCaregiver kinship, String phoneNumber,
                                                   MobilityLevel mobilityLevel,
                                                   String emergencyContactName, String emergencyContactPhone,
                                                   boolean consentGiven) {

    private static final int NAME_MAX_LENGTH = 75;
    private static final int CONTACT_NAME_MAX_LENGTH = 150;

    /**
     * Validates and normalizes the command.
     *
     * @throws IllegalArgumentException when a value is missing or invalid
     */
    public SaveAssistedPersonCareProfileCommand {
        Objects.requireNonNull(requesterUserId, "Requester cannot be null.");
        if (householdId != null && householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0.");
        }
        firstName = requireName(firstName, "first name", NAME_MAX_LENGTH);
        lastName = requireName(lastName, "last name", NAME_MAX_LENGTH);
        if (birthDate == null || !birthDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The birth date is required and must be in the past.");
        }

        documentNumber = blankToNull(documentNumber);
        if ((documentType == null) != (documentNumber == null)) {
            throw new IllegalArgumentException("The document type and number go together.");
        }
        if (documentType != null && !documentType.accepts(documentNumber)) {
            throw new IllegalArgumentException("The document number is not valid for " + documentType + ".");
        }

        Objects.requireNonNull(kinship, "The kinship is required.");
        Objects.requireNonNull(mobilityLevel, "The mobility level is required.");
        phoneNumber = optionalPhone(phoneNumber);

        emergencyContactName = blankToNull(emergencyContactName);
        emergencyContactPhone = optionalPhone(emergencyContactPhone);
        if ((emergencyContactName == null) != (emergencyContactPhone == null)) {
            throw new IllegalArgumentException("The emergency contact needs both a name and a phone.");
        }
        if (emergencyContactName != null && emergencyContactName.length() > CONTACT_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("The emergency contact name is too long.");
        }

        if (!consentGiven) {
            throw new IllegalArgumentException("The client must confirm they are authorized to share the data.");
        }
    }

    private static String requireName(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + name + " is required.");
        }
        var stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException("The " + name + " is too long.");
        }
        return stripped;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Validates an optional phone with the shared rules; the value object throws when invalid. */
    private static String optionalPhone(String value) {
        var phone = blankToNull(value);
        return phone == null ? null : new PhoneNumber(phone).phoneNumber();
    }

    /**
     * Returns a representation without personal data, so the command is safe to log.
     *
     * @return the requester and household identifiers
     */
    @Override
    public String toString() {
        return "SaveAssistedPersonCareProfileCommand[requesterUserId=" + requesterUserId.userId()
                + ", householdId=" + householdId + "]";
    }
}
