package com.resolum.alivia.business.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * @param fullName    the user full name (forwarded via IAM to Profiles)
     * @param phoneNumber the user phone number (forwarded via IAM to Profiles)
     * @param role        the access role (e.g. "TECHNICIAN" / "EMPLOYEE", "ADMINISTRATOR", "CAREGIVER")
     * @param businessId  the business/company identifier
     * @return the created IAM account identifier
     */
    public Long signUpCompanyUser(String email, String password, String fullName, String phoneNumber,
                                  String role, Long businessId) {
        log.info("Requesting IAM sign-up for company user with email: {} and business ID: {}", email, businessId);
        Long accountId = iamContextFacade.registerCompanyUser(email, password, fullName, phoneNumber, role, businessId);
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
