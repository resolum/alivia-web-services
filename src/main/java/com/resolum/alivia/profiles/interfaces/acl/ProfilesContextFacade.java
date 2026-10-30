package com.resolum.alivia.profiles.interfaces.acl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;

/**
 * Facade for other bounded contexts (e.g. IAM, Nursing, Business) that need to interact with Profiles.
 *
 * <p>It exposes primitive types and standard Java types only, so consumers never depend on Profiles
 * internal aggregates or domain implementations.</p>
 */
public interface ProfilesContextFacade {

    record StaffProfileData(Long id, String emailAddress, String photoUrl, String photoPublicId,
                            String phoneNumber, String firstName, String lastName, String dni,
                            LocalDate birthDate) {
    }

    /**
     * Creates a new person profile.
     *
     * @param emailAddress  the person's email address
     * @param photoUrl      the photo URL
     * @param photoPublicId the photo public identifier
     * @param phoneNumber   the contact phone number
     * @param firstName     the first name
     * @param lastName      the last name
     * @param dni           the national identity document number
     * @param birthDate     the birth date
     * @return the identifier of the created person profile
     */
    Long createPersonProfile(String emailAddress, String photoUrl, String photoPublicId,
                             String phoneNumber, String firstName, String lastName,
                             String dni, LocalDate birthDate);

    Long createPersonProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                     String phoneNumber, String firstName, String lastName,
                                     String dni, LocalDate birthDate);

    /**
     * Creates a new staff profile.
     *
     * @param emailAddress  the staff member's email address
     * @param photoUrl      the photo URL
     * @param photoPublicId the photo public identifier
     * @param phoneNumber   the contact phone number
     * @param firstName     the first name
     * @param lastName      the last name
     * @param dni           the national identity document number
     * @param birthDate     the birth date
     * @return the identifier of the created staff profile
     */
    Long createStaffProfile(String emailAddress, String photoUrl, String photoPublicId,
                            String phoneNumber, String firstName, String lastName,
                            String dni, LocalDate birthDate);

    Long createStaffProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                    String phoneNumber, String firstName, String lastName,
                                    String dni, LocalDate birthDate);

    Optional<StaffProfileData> fetchStaffProfileById(Long profileId);

    Long updateStaffProfile(Long profileId, String firstName, String lastName,
                            LocalDate birthDate, String phoneNumber, String photoUrl,
                            String photoPublicId);

    Long updateStaffProfileWithPhoto(Long profileId, String firstName, String lastName,
                                    LocalDate birthDate, String phoneNumber, byte[] photoData,
                                    String photoFileName);

    /**
     * Creates a new business profile.
     *
     * @param emailAddress   the business contact email address
     * @param photoUrl       the photo URL
     * @param photoPublicId  the photo public identifier
     * @param whatsappNum    the WhatsApp contact number
     * @param ruc            the tax identifier (RUC)
     * @param businessName   the business trade name
     * @param legalName      the legal entity name
     * @param description    the description of the company
     * @param mainActivity   the main economic activity
     * @param emergencyPhone the emergency contact phone number
     * @param businessHours  the weekly periods representing business operating hours
     * @param slogan         the company slogan
     * @param supportEmail   the customer support email address
     * @param streetAddress  the formatted physical address
     * @param latitude       the geographical latitude
     * @param longitude      the geographical longitude
     * @param placeId        the Google Places or provider place identifier
     * @return the identifier of the created business profile
     */
    Long createBusinessProfile(String emailAddress, String photoUrl, String photoPublicId,
                               String whatsappNum, String ruc, String businessName,
                               String legalName, String description, String mainActivity,
                               String emergencyPhone, List<WeeklyPeriod> businessHours,
                               String slogan, String supportEmail, String streetAddress,
                               Double latitude, Double longitude, String placeId);

    Long createBusinessProfileWithPhoto(String emailAddress, byte[] photoData, String photoFileName,
                                       String whatsappNum, String ruc, String businessName,
                                       String legalName, String description, String mainActivity,
                                       String emergencyPhone, List<WeeklyPeriod> businessHours,
                                       String slogan, String supportEmail, String streetAddress,
                                       Double latitude, Double longitude, String placeId);

    /**
     * Updates an existing business profile.
     *
     * @param profileId      the business profile identifier
     * @param photoUrl       the photo URL
     * @param photoPublicId  the photo public identifier
     * @param whatsappNum    the WhatsApp contact number
     * @param businessName   the business trade name
     * @param legalName      the legal entity name
     * @param description    the description of the company
     * @param mainActivity   the main economic activity
     * @param emergencyPhone the emergency contact phone number
     * @param businessHours  the weekly periods representing business operating hours
     * @param slogan         the company slogan
     * @param supportEmail   the customer support email address
     * @return the identifier of the updated business profile
     */
    Long updateBusinessProfile(Long profileId, String photoUrl, String photoPublicId,
                               String whatsappNum, String businessName, String legalName,
                               String description, String mainActivity, String emergencyPhone,
                               List<WeeklyPeriod> businessHours, String slogan, String supportEmail);

    Long updateBusinessProfileWithPhoto(Long profileId, byte[] photoData, String photoFileName,
                                       String whatsappNum, String businessName, String legalName,
                                       String description, String mainActivity, String emergencyPhone,
                                       List<WeeklyPeriod> businessHours, String slogan, String supportEmail);

    boolean existsBusinessProfileByRuc(String ruc);

    boolean existsBusinessProfileByBusinessName(String businessName);

    boolean existsBusinessProfileByLegalName(String legalName);

    /**
     * Checks whether a person profile exists with the given email address.
     *
     * @param emailAddress the email address to check
     * @return {@code true} if a person profile exists; {@code false} otherwise
     */
    boolean existsPersonProfileByEmailAddress(String emailAddress);

    /**
     * Checks whether a person profile exists with the given DNI.
     *
     * @param dni the DNI to check
     * @return {@code true} if a person profile exists; {@code false} otherwise
     */
    boolean existsPersonProfileByDni(String dni);

    /**
     * Checks whether a staff profile exists with the given email address.
     *
     * @param emailAddress the email address to check
     * @return {@code true} if a staff profile exists; {@code false} otherwise
     */
    boolean existsStaffProfileByEmailAddress(String emailAddress);

    /**
     * Checks whether a staff profile exists with the given DNI.
     *
     * @param dni the DNI to check
     * @return {@code true} if a staff profile exists; {@code false} otherwise
     */
    boolean existsStaffProfileByDni(String dni);

    /**
     * Checks whether a business profile exists with the given email address.
     *
     * @param emailAddress the email address to check
     * @return {@code true} if a business profile exists; {@code false} otherwise
     */
    boolean existsBusinessProfileByEmailAddress(String emailAddress);

    /**
     * Checks whether any profile (person, staff, or business) exists by its identifier.
     *
     * @param profileId the profile identifier
     * @return {@code true} if a profile exists; {@code false} otherwise
     */
    boolean existsProfileById(Long profileId);

    /**
     * Fetches the full name of a person profile by its identifier, if present.
     *
     * @param profileId the person profile identifier
     * @return an {@link Optional} containing the full name, or empty if not found
     */
    Optional<String> fetchPersonProfileFullNameById(Long profileId);

    /**
     * Fetches the full name of a staff profile by its identifier, if present.
     *
     * @param profileId the staff profile identifier
     * @return an {@link Optional} containing the full name, or empty if not found
     */
    Optional<String> fetchStaffProfileFullNameById(Long profileId);

    /**
     * Fetches the trade name of a business profile by its identifier, if present.
     *
     * @param profileId the business profile identifier
     * @return an {@link Optional} containing the business name, or empty if not found
     */
    Optional<String> fetchBusinessProfileNameById(Long profileId);
}
