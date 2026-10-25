package com.resolum.alivia.nursing.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbound service acting as an Anti-Corruption Layer (ACL) adapter
 * to communicate with the Identity and Access Management (IAM) bounded context.
 *
 * <p>Allows Nursing (Assistance) to complete the sign-up of assisted persons in IAM,
 * while storing care plans, activities and health information in the Nursing context.</p>
 */
@Slf4j
@Service("nursingExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    /**
     * Completes user sign-up in IAM for an assisted person linked to a familiar/caregiver.
     *
     * @param email       the user email
     * @param password    the raw password
     * @param fullName    the user full name (forwarded via IAM to Profiles)
     * @param phoneNumber the user phone number (forwarded via IAM to Profiles)
     * @param familiarId  the familiar/caregiver identifier
     * @return the created IAM account identifier
     */
    public Long signUpAssistedPerson(String email, String password, String fullName, String phoneNumber,
                                     Long familiarId) {
        log.info("Requesting IAM sign-up for assisted person with email: {} and familiar ID: {}", email, familiarId);
        Long accountId = iamContextFacade.registerAssistedPerson(email, password, fullName, phoneNumber, familiarId);
        log.info("IAM sign-up completed for assisted person with account ID: {}", accountId);
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
