package com.resolum.alivia.iam.application.acl;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.InviteCompanyUserCommand;
import com.resolum.alivia.iam.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.iam.domain.model.queries.GetEdgeCredentialByEdgeIdQuery;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
import com.resolum.alivia.iam.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.iam.domain.services.EdgeCredentialQueryService;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the IAM anti-corruption layer facade.
 *
 * <p>
 * Delegates to domain services and returns primitive types only, so external
 * bounded contexts (e.g. Company/Business, Nursing, Profiles) do not depend on
 * IAM internals.
 * </p>
 */
@Slf4j
@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserRepository userRepository;
    private final UserCommandService userCommandService;
    private final AccountActivationService accountActivationService;
    private final EdgeCredentialCommandService edgeCredentialCommandService;
    private final EdgeCredentialQueryService edgeCredentialQueryService;

    /**
     * Creates the facade.
     *
     * @param userRepository               the user persistence port
     * @param userCommandService           the user command service
     * @param accountActivationService     the staff invitation service
     * @param edgeCredentialCommandService the credentials of the edge services
     * @param edgeCredentialQueryService   reads the state of those credentials
     */
    public IamContextFacadeImpl(UserRepository userRepository, UserCommandService userCommandService,
            AccountActivationService accountActivationService,
            EdgeCredentialCommandService edgeCredentialCommandService,
            EdgeCredentialQueryService edgeCredentialQueryService) {
        this.edgeCredentialCommandService = edgeCredentialCommandService;
        this.edgeCredentialQueryService = edgeCredentialQueryService;
        this.accountActivationService = accountActivationService;
        this.userRepository = userRepository;
        this.userCommandService = userCommandService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            log.debug("User existence check ignored: email is empty");
            return false;
        }
        log.info("Checking whether a user exists by email");
        try {
            var exists = userRepository.existsByEmail(new EmailAddress(email));
            log.info("User existence check completed with result {}", exists);
            return exists;
        } catch (IllegalArgumentException exception) {
            log.warn("User existence check rejected: email address is invalid");
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Long> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return userRepository.findByEmail(new EmailAddress(email)).map(User::getId);
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> fetchEmailsByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user.getEmail().email()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> fetchPersonProfileIdsByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIds(userIds).stream()
                .filter(user -> user.getProfileId() != null)
                .filter(user -> user.getAccessRole() == AccessRole.CAREGIVER
                        || user.getAccessRole() == AccessRole.ASSISTED_PERSON)
                .collect(Collectors.toMap(User::getId, User::getProfileId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> fetchStaffProfileIdsByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIds(userIds).stream()
                .filter(user -> user.getProfileId() != null)
                .filter(user -> user.getAccessRole() == AccessRole.TECHNICIAN)
                .collect(Collectors.toMap(User::getId, User::getProfileId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long signUpUser(String email, String password, String firstName, String lastName, String phoneNumber,
            String roleStr, Long businessId, Long familiarId,
            String dni, LocalDate birthDate) {
        log.info("Signing up user via IAM ACL facade with role: {}", roleStr);
        AccessRole role = parseRole(roleStr);
        var command = new SignUpCommand(
                email,
                password,
                firstName,
                lastName,
                phoneNumber,
                true,
                role,
                businessId,
                familiarId,
                dni,
                birthDate,
                null,
                null);
        var user = userCommandService.handle(command);
        log.info("User signed up via IAM ACL facade with account ID: {}", user.getId());
        return user.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerUser(String email, String password, String firstName, String lastName, String phoneNumber,
            String role) {
        String dni = null;
        LocalDate birthDate = null;
        if ("ROLE_CAREGIVER".equalsIgnoreCase(role) || "CAREGIVER".equalsIgnoreCase(role)) {
            int hash = Math.abs((email != null ? email : "").hashCode());
            dni = String.format("%08d", (hash % 90000000) + 10000000);
            birthDate = LocalDate.of(1995, 1, 1);
        }
        return signUpUser(email, password, firstName, lastName, phoneNumber, role, null, null, dni, birthDate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerCompanyUser(String email, String password, String firstName, String lastName,
            String phoneNumber,
            String role, Long businessId,
            String dni, LocalDate birthDate) {
        return signUpUser(email, password, firstName, lastName, phoneNumber, role, businessId, null,
                dni, birthDate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long inviteCompanyUser(String email, String fullName, String roleLabel, String role, Long profileId) {
        return accountActivationService.handle(new InviteCompanyUserCommand(email, fullName, roleLabel,
                parseRole(role), profileId)).getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerAssistedPerson(String email, String password, String firstName, String lastName,
            String phoneNumber,
            Long familiarId,
            String dni, LocalDate birthDate) {
        return signUpUser(email, password, firstName, lastName, phoneNumber, "ASSISTED_PERSON", null, familiarId,
                dni, birthDate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public String issueEdgeCredential(Long edgeId, String edgeKind, Long issuedBy) {
        return edgeCredentialCommandService
                .handle(new IssueEdgeCredentialCommand(edgeId, EdgeKind.parse(edgeKind), issuedBy)).credential();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void revokeEdgeCredential(Long edgeId, Long revokedBy, String reason) {
        edgeCredentialCommandService.handle(new RevokeEdgeCredentialCommand(edgeId, revokedBy, reason));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<EdgeCredentialSummary> getEdgeCredentialSummary(Long edgeId) {
        return edgeCredentialQueryService.handle(new GetEdgeCredentialByEdgeIdQuery(edgeId))
                .map(view -> new EdgeCredentialSummary(view.edgeId(), view.active(), view.issuedAt(), view.issuedBy(),
                        view.ageDays(), view.rotationRecommended(), view.lastUsedAt(), view.revokedAt(),
                        view.revokedBy(),
                        view.revokedReason()));
    }

    private static AccessRole parseRole(String roleStr) {
        try {
            return com.resolum.alivia.iam.domain.services.RoleParser.parse(roleStr);
        } catch (IllegalArgumentException e) {
            throw new SignUpValidationException("role", "invalid_role");
        }
    }
}
