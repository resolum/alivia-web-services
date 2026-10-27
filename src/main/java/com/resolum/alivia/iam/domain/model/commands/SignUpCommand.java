package com.resolum.alivia.iam.domain.model.commands;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

/**
 * Command that requests the registration of a new account with a specific
 * access role.
 *
 * @param email         the account email address
 * @param password      the plain-text password, used only to produce its hash
 * @param firstName     the first name forwarded to the Profiles context
 * @param lastName      the last name forwarded to the Profiles context
 * @param phoneNumber   the phone number forwarded to the Profiles context
 * @param termsAccepted whether the user accepted the terms and conditions
 * @param role          the access role assigned to the user
 * @param businessId    the optional business identifier forwarded to Profiles
 * @param familiarId    the optional familiar identifier forwarded to Profiles
 * @param dni           the national identity document number forwarded to
 *                      Profiles
 * @param birthDate     the birth date forwarded to Profiles
 * @param photo         the optional photo content, uploaded to storage for the
 *                      profile
 * @param photoFileName the original file name of the photo
 */
public record SignUpCommand(
                String email,
                String password,
                String firstName,
                String lastName,
                String phoneNumber,
                boolean termsAccepted,
                AccessRole role,
                Long businessId,
                Long familiarId,
                String dni,
                LocalDate birthDate,
                byte[] photo,
                String photoFileName) {
        public SignUpCommand(
                        String email,
                        String password,
                        String firstName,
                        String lastName,
                        String phoneNumber,
                        boolean termsAccepted,
                        AccessRole role,
                        Long businessId,
                        Long familiarId) {
                this(email, password, firstName, lastName, phoneNumber, termsAccepted, role, businessId, familiarId,
                                null, null, null, null);
        }

        public SignUpCommand(
                        String email,
                        String password,
                        String firstName,
                        String lastName,
                        String phoneNumber,
                        boolean termsAccepted) {
                this(email, password, firstName, lastName, phoneNumber, termsAccepted, AccessRole.CAREGIVER, null,
                                null);
        }

        public SignUpCommand(
                        String email,
                        String password,
                        String firstName,
                        String lastName,
                        String phoneNumber,
                        boolean termsAccepted,
                        AccessRole role) {
                this(email, password, firstName, lastName, phoneNumber, termsAccepted, role, null, null);
        }

        public ProfileData toProfileData() {
                return new ProfileData(firstName, lastName, phoneNumber, businessId, familiarId,
                                dni, birthDate, photo, photoFileName);
        }

        @Override
        public boolean equals(Object o) {
                if (this == o)
                        return true;
                if (!(o instanceof SignUpCommand that))
                        return false;
                return termsAccepted == that.termsAccepted
                                && Objects.equals(email, that.email)
                                && Objects.equals(password, that.password)
                                && Objects.equals(firstName, that.firstName)
                                && Objects.equals(lastName, that.lastName)
                                && Objects.equals(phoneNumber, that.phoneNumber)
                                && role == that.role
                                && Objects.equals(businessId, that.businessId)
                                && Objects.equals(familiarId, that.familiarId)
                                && Objects.equals(dni, that.dni)
                                && Objects.equals(birthDate, that.birthDate)
                                && Arrays.equals(photo, that.photo)
                                && Objects.equals(photoFileName, that.photoFileName);
        }

        @Override
        public int hashCode() {
                int result = Objects.hash(email, password, firstName, lastName, phoneNumber, termsAccepted, role,
                                businessId, familiarId, dni, birthDate, photoFileName);
                return 31 * result + Arrays.hashCode(photo);
        }

        // Password masked and photo shown by size so logs never leak secrets or dump
        // binary content
        @Override
        public String toString() {
                return "SignUpCommand[email=" + email
                                + ", password=****"
                                + ", firstName=" + firstName
                                + ", lastName=" + lastName
                                + ", phoneNumber=" + phoneNumber
                                + ", termsAccepted=" + termsAccepted
                                + ", role=" + role
                                + ", businessId=" + businessId
                                + ", familiarId=" + familiarId
                                + ", dni=" + dni
                                + ", birthDate=" + birthDate
                                + ", photo=" + (photo == null ? "null" : photo.length + " bytes")
                                + ", photoFileName=" + photoFileName + "]";
        }
}
