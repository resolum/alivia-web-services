package com.resolum.alivia.assistance.application.internal.outboundservices.acl;

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
 */
@Slf4j
@Service("assistanceExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    public Long createRelativeUser(String email, String password, Long businessId,
            String firstName, String lastName, String phoneNumber,
            String dni, LocalDate birthDate) {
        log.info("Requesting IAM sign-up for Relative user: {}", email);
        return iamContextFacade.registerCompanyUser(email, password, firstName, lastName, phoneNumber,
                "CAREGIVER", businessId, dni, birthDate);
    }

    public Long registerCaregiverAccount(String email, String password, String firstName, String lastName,
            String phoneNumber) {
        log.info("Requesting IAM user creation for caregiver: {}", email);
        return iamContextFacade.registerUser(email, password, firstName, lastName, phoneNumber, "CAREGIVER");
    }

    public boolean existsUserByEmail(String email) {
        log.debug("Checking in IAM whether a user exists by email");
        return iamContextFacade.existsUserByEmail(email);
    }

    public java.util.Optional<Long> findUserIdByEmail(String email) {
        log.debug("Finding user ID in IAM by email");
        return iamContextFacade.findUserIdByEmail(email);
    }

    public Map<UserId, String> fetchEmails(Set<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        log.debug("Fetching emails from IAM for {} users", userIds.size());
        var ids = userIds.stream().map(UserId::userId).collect(Collectors.toSet());
        return iamContextFacade.fetchEmailsByUserIds(ids).entrySet().stream()
                .collect(Collectors.toMap(entry -> new UserId(entry.getKey()), Map.Entry::getValue));
    }

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
}
