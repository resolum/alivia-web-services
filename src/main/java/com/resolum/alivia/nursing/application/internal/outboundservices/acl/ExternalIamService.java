package com.resolum.alivia.nursing.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
     * @param firstName   the user first names
     * @param lastName    the user last names
     * @param phoneNumber the user phone number (forwarded via IAM to Profiles)
     * @param familiarId  the familiar/caregiver identifier
     * @param dni           the DNI, required for roles with a profile (forwarded to Profiles)
     * @param birthDate     the birth date, required for roles with a profile (forwarded to Profiles)
     * @return the created IAM account identifier
     */
    public Long signUpAssistedPerson(String email, String password, String firstName, String lastName, String phoneNumber,
                                     Long familiarId,
                                     String dni, LocalDate birthDate) {
        log.info("Requesting IAM sign-up for assisted person with familiar ID: {}", familiarId);
        Long accountId = iamContextFacade.registerAssistedPerson(email, password, firstName, lastName, phoneNumber, familiarId,
                dni, birthDate);
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
        log.debug("Checking in IAM whether a user exists by email");
        return iamContextFacade.existsUserByEmail(email);
    }

    /**
     * Reads the email addresses of the given users from IAM in a single batch.
     *
     * @param userIds the users
     * @return the email address by user; unknown users are absent
     */
    public Map<UserId, String> fetchEmails(Set<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        log.debug("Fetching emails from IAM for {} users", userIds.size());
        var ids = userIds.stream().map(UserId::userId).collect(Collectors.toSet());
        return iamContextFacade.fetchEmailsByUserIds(ids).entrySet().stream()
                .collect(Collectors.toMap(entry -> new UserId(entry.getKey()), Map.Entry::getValue));
    }

    /**
     * Reads the person profiles of the given users from IAM in a single batch.
     *
     * @param userIds the users
     * @return the person profile by user; users without a person profile are absent
     */
    public Map<UserId, PersonProfileId> fetchPersonProfileIds(Set<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        log.debug("Fetching person profile ids from IAM for {} users", userIds.size());
        var ids = userIds.stream().map(UserId::userId).collect(Collectors.toSet());
        return iamContextFacade.fetchPersonProfileIdsByUserIds(ids).entrySet().stream()
                .collect(Collectors.toMap(entry -> new UserId(entry.getKey()),
                        entry -> new PersonProfileId(entry.getValue())));
    }

    /**
     * Reads the staff profiles of the given users from IAM in a single batch.
     *
     * @param userIds the users
     * @return the staff profile identifier by user; users without a staff profile are absent
     */
    public Map<UserId, Long> fetchStaffProfileIds(Set<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        log.debug("Fetching staff profile ids from IAM for {} users", userIds.size());
        var ids = userIds.stream().map(UserId::userId).collect(Collectors.toSet());
        return iamContextFacade.fetchStaffProfileIdsByUserIds(ids).entrySet().stream()
                .collect(Collectors.toMap(entry -> new UserId(entry.getKey()), Map.Entry::getValue));
    }
}
