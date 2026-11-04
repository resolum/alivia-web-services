package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Command to register an assisted person in a household.
 *
 * <p>The requester becomes a relative of the new assisted person. Format rules for the email,
 * phone and DNI are enforced by IAM and Profiles when the account is created.</p>
 *
 * @param requesterUserId the authenticated user registering the person
 * @param householdId     the household that receives the assisted person
 * @param email           the email of the assisted person account
 * @param password        the raw password of the account
 * @param firstName       the first names
 * @param lastName        the last names
 * @param phoneNumber     the contact phone number
 * @param dni             the national identity document number
 * @param birthDate       the birth date
 */
public record RegisterAssistedPersonCommand(UserId requesterUserId, Long householdId, String email, String password,
                                            String firstName, String lastName, String phoneNumber,
                                            String dni, LocalDate birthDate) {

    /**
     * Validates the command.
     *
     * @throws IllegalArgumentException when the household is not positive or a required value is
     *                                  missing or blank
     */
    public RegisterAssistedPersonCommand {
        Objects.requireNonNull(requesterUserId, "Requester cannot be null.");
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        requireText(email, "email");
        requireText(password, "password");
        requireText(firstName, "first name");
        requireText(lastName, "last name");
        requireText(phoneNumber, "phone number");
        requireText(dni, "DNI");
        if (birthDate == null) {
            throw new IllegalArgumentException("The birth date is required.");
        }
        email = email.strip();
        firstName = firstName.strip();
        lastName = lastName.strip();
        phoneNumber = phoneNumber.strip();
        dni = dni.strip();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + name + " is required.");
        }
    }

    /**
     * Returns a representation without the password or personal data, so the command is safe to log.
     *
     * @return the requester and household identifiers
     */
    @Override
    public String toString() {
        return "RegisterAssistedPersonCommand[requesterUserId=" + requesterUserId.userId()
                + ", householdId=" + householdId + "]";
    }
}
