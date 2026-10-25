package com.resolum.alivia.iam.interfaces.acl;

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
     * Registers a new user account with a given role and optional contextual identifiers.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param fullName    the user full name (forwarded to Profiles)
     * @param phoneNumber the contact phone number (forwarded to Profiles)
     * @param role        the access role (ADMINISTRATOR, CAREGIVER/FAMILIAR, TECHNICIAN/EMPLOYEE, ASSISTED_PERSON)
     * @param businessId  optional business ID associated with the user
     * @param familiarId  optional familiar ID associated with the user
     * @return the created account ID
     */
    Long signUpUser(String email, String password, String fullName, String phoneNumber,
                    String role, Long businessId, Long familiarId);

    /**
     * Registers a new user account with a given role.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param fullName    the user full name
     * @param phoneNumber the contact phone number
     * @param role        the access role
     * @return the created account ID
     */
    Long registerUser(String email, String password, String fullName, String phoneNumber, String role);

    /**
     * Registers a company/business user (e.g. employee or familiar associated with a business).
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param fullName    the user full name
     * @param phoneNumber the contact phone number
     * @param role        the access role
     * @param businessId  the business ID
     * @return the created account ID
     */
    Long registerCompanyUser(String email, String password, String fullName, String phoneNumber,
                            String role, Long businessId);

    /**
     * Registers an assisted person user associated with a familiar.
     *
     * @param email       the user email address
     * @param password    the raw password
     * @param fullName    the user full name
     * @param phoneNumber the contact phone number
     * @param familiarId  the familiar ID
     * @return the created account ID
     */
    Long registerAssistedPerson(String email, String password, String fullName, String phoneNumber,
                               Long familiarId);
}
