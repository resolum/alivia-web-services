package com.resolum.alivia.iam.application.acl;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.InviteCompanyUserCommand;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
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
 * <p>Delegates to domain services and returns primitive types only, so external
 * bounded contexts (e.g. Company/Business, Nursing, Profiles) do not depend on IAM internals.</p>
 */
@Slf4j
@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserRepository userRepository;
    private final UserCommandService userCommandService;
    private final AccountActivationService accountActivationService;

    /**
     * Creates the facade.
     *
     * @param userRepository     the user persistence port
     * @param userCommandService the user command service
     */
    public IamContextFacadeImpl(UserRepository userRepository, UserCommandService userCommandService,
                                AccountActivationService accountActivationService) {
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
                null
        );
        var user = userCommandService.handle(command);
        log.info("User signed up via IAM ACL facade with account ID: {}", user.getId());
        return user.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerUser(String email, String password, String firstName, String lastName, String phoneNumber, String role) {
        return signUpUser(email, password, firstName, lastName, phoneNumber, role, null, null, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerCompanyUser(String email, String password, String firstName, String lastName, String phoneNumber,
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
    public Long registerAssistedPerson(String email, String password, String firstName, String lastName, String phoneNumber,
                                      Long familiarId,
                                      String dni, LocalDate birthDate) {
        return signUpUser(email, password, firstName, lastName, phoneNumber, "ASSISTED_PERSON", null, familiarId,
                dni, birthDate);
    }

    private static AccessRole parseRole(String roleStr) {
        try {
            return com.resolum.alivia.iam.domain.services.RoleParser.parse(roleStr);
        } catch (IllegalArgumentException e) {
            throw new SignUpValidationException("role", "invalid_role");
        }
    }
}
