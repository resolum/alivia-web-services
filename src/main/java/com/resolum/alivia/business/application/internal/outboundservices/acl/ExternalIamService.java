package com.resolum.alivia.business.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Outbound service acting as an Anti-Corruption Layer (ACL) adapter
 * to communicate with the Identity and Access Management (IAM) bounded context.
 *
 * <p>Allows Company (Business) to complete the sign-up of company members/employees
 * in IAM, receiving their account ID while persisting business-specific data in the Company context.</p>
 */
@Slf4j
@Service("businessExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /**
     * Completes user sign-up in IAM for a business/company user.
     *
     * @param email       the user email
     * @param password    the raw password
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the user phone number (forwarded via IAM to Profiles)
     * @param role        the access role (e.g. "TECHNICIAN" / "EMPLOYEE", "ADMINISTRATOR", "CAREGIVER")
     * @param businessId  the business/company identifier
     * @param dni           the DNI, required for roles with a profile (forwarded to Profiles)
     * @param birthDate     the birth date, required for roles with a profile (forwarded to Profiles)
     * @return the created IAM account identifier
     */
    public Long signUpCompanyUser(String email, String password, String firstName, String lastName, String phoneNumber,
                                  String role, Long businessId,
                                  String dni, LocalDate birthDate) {
        log.info("Requesting IAM sign-up for company user with email: {} and business ID: {}", email, businessId);
        Long accountId = iamContextFacade.registerCompanyUser(email, password, firstName, lastName, phoneNumber, role, businessId,
                dni, birthDate);
        log.info("IAM sign-up completed for company user with account ID: {}", accountId);
        return accountId;
    }

    /**
     * Checks if a user already exists in IAM with the given email.
     *
     * @param email the email to verify
     * @return true if user exists, false otherwise
     */
    public boolean existsUserByEmail(String email) {
        return iamContextFacade.existsUserByEmail(email);
    }
}
