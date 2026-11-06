package com.resolum.alivia.iam.interfaces.acl;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Facade for other bounded contexts (e.g. Company/Business, Nursing, Profiles) that need to interact with IAM.
 *
 * <p>It exposes primitive types only, so consumers never depend on IAM aggregates or
 * value objects.</p>
 */
public interface IamContextFacade {

    /**
     * Checks whether a user with the given email address exists.
     *
     * @param email the email address to check
     * @return {@code true} when a user is registered with that email; {@code false} when
     *         the email is missing or invalid
     */
    boolean existsUserByEmail(String email);

    /**
     * Reads the email addresses of the given users in a single batch.
     *
     * @param userIds the internal user identifiers
     * @return the email address by user identifier; unknown users are absent
     */
    Map<Long, String> fetchEmailsByUserIds(Set<Long> userIds);

    /**
     * Reads the person profile identifiers of the given users in a single batch.
     *
     * <p>Only caregivers and assisted persons have a person profile; technicians have a staff
     * profile and are left out.</p>
     *
     * @param userIds the internal user identifiers
     * @return the person profile identifier by user identifier; users without one are absent
     */
    Map<Long, Long> fetchPersonProfileIdsByUserIds(Set<Long> userIds);

    /**
     * Reads the staff profile identifiers of the given users in a single batch.
     *
     * <p>Only technicians have a staff profile.</p>
     *
     * @param userIds the internal user identifiers
     * @return the staff profile identifier by user identifier; users without one are absent
     */
    Map<Long, Long> fetchStaffProfileIdsByUserIds(Set<Long> userIds);

    /**
     * Registers a new user account with a given role and optional contextual identifiers.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the contact phone number (forwarded to Profiles)
     * @param role        the access role (ADMINISTRATOR, CAREGIVER/FAMILIAR, TECHNICIAN/EMPLOYEE, ASSISTED_PERSON)
     * @param businessId  optional business ID associated with the user
     * @param familiarId  optional familiar ID associated with the user
     * @param dni           the DNI, required for roles with a profile (forwarded to Profiles)
     * @param birthDate     the birth date, required for roles with a profile (forwarded to Profiles)
     * @return the created account ID
     */
    Long signUpUser(String email, String password, String firstName, String lastName, String phoneNumber,
                    String role, Long businessId, Long familiarId,
                    String dni, LocalDate birthDate);

    /**
     * Registers a new user account with a given role.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the contact phone number
     * @param role        the access role
     * @return the created account ID
     */
    Long registerUser(String email, String password, String firstName, String lastName, String phoneNumber, String role);

    /**
     * Registers a company/business user (e.g. employee or familiar associated with a business).
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the contact phone number
     * @param role        the access role
     * @param businessId  the business ID
     * @param dni           the DNI, required for roles with a profile (forwarded to Profiles)
     * @param birthDate     the birth date, required for roles with a profile (forwarded to Profiles)
     * @return the created account ID
     */
    Long registerCompanyUser(String email, String password, String firstName, String lastName, String phoneNumber,
                            String role, Long businessId,
                            String dni, LocalDate birthDate);

    /**
     * Registers an assisted person user associated with a familiar.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the contact phone number
     * @param familiarId  the familiar ID
     * @param dni           the DNI, required for roles with a profile (forwarded to Profiles)
     * @param birthDate     the birth date, required for roles with a profile (forwarded to Profiles)
     * @return the created account ID
     */
    /**
     * Creates a staff account without a password and emails its owner an invitation link to
     * activate it and choose the password.
     *
     * @param email     the corporate email address, used to sign in
     * @param fullName  the name shown in the invitation
     * @param roleLabel the role shown in the invitation
     * @param role      the access role name (for example TECHNICIAN or SUBSCRIPTION_MANAGER)
     * @param profileId the staff profile created by the inviting context
     * @return the id of the new account
     */
    Long inviteCompanyUser(String email, String fullName, String roleLabel, String role, Long profileId);

    Long registerAssistedPerson(String email, String password, String firstName, String lastName, String phoneNumber,
                               Long familiarId,
                               String dni, LocalDate birthDate);

    /**
     * Issues the credential with which an edge service identifies itself to the API, or replaces its secret when it
     * already has one (the previous secret stops working at once).
     *
     * @param edgeId   inventory id of the edge
     * @param edgeKind {@code MICROPHONE} or {@code ACTUATOR}
     * @param issuedBy account of the administrator who asks for it
     * @return what the edge sends as bearer token; this is the only time it can be read
     * @throws IllegalArgumentException when an id is not positive or the kind is unknown
     */
    String issueEdgeCredential(Long edgeId, String edgeKind, Long issuedBy);

    /**
     * Revokes the credential of an edge service. Nothing happens when the edge has none or it is already revoked.
     *
     * @param edgeId    inventory id of the edge
     * @param revokedBy account of the administrator who asks for it, or {@code null} when a change of the edge causes it
     * @param reason    why, as a short code of at most 40 characters
     * @throws IllegalArgumentException when the id or the account is not positive, or the reason is missing or too long
     */
    void revokeEdgeCredential(Long edgeId, Long revokedBy, String reason);

    /**
     * Describes the credential of an edge service without its secret.
     *
     * @param edgeId inventory id of the edge
     * @return who issued it, how old it is and whether it was revoked; empty when the edge has none
     */
    java.util.Optional<EdgeCredentialSummary> getEdgeCredentialSummary(Long edgeId);

    /**
     * The state of an edge credential, in primitive types so that other contexts do not depend on iam's model.
     *
     * @param edgeId              inventory id of the edge
     * @param active              whether it can still authenticate the edge
     * @param issuedAt            when the current secret was issued or rotated
     * @param issuedBy            administrator who issued it; {@code null} for credentials that predate that record
     * @param ageDays             whole days since the current secret was issued
     * @param rotationRecommended whether it is active and old enough that it should be replaced
     * @param lastUsedAt          last use, {@code null} if never
     * @param revokedAt           when it was revoked, {@code null} while active
     * @param revokedBy           administrator who revoked it, {@code null} when a change of the edge did
     * @param revokedReason       why it was revoked, {@code null} while active
     */
    record EdgeCredentialSummary(Long edgeId, boolean active, java.time.LocalDateTime issuedAt, Long issuedBy, long ageDays,
                                 boolean rotationRecommended, java.time.LocalDateTime lastUsedAt,
                                 java.time.LocalDateTime revokedAt, Long revokedBy, String revokedReason) {
    }
}
