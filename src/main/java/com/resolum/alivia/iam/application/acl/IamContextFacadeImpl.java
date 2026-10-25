package com.resolum.alivia.iam.application.acl;

import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Creates the facade.
     *
     * @param userRepository     the user persistence port
     * @param userCommandService the user command service
     */
    public IamContextFacadeImpl(UserRepository userRepository, UserCommandService userCommandService) {
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
    @Transactional
    public Long signUpUser(String email, String password, String fullName, String phoneNumber,
                           String roleStr, Long businessId, Long familiarId) {
        log.info("Signing up user via IAM ACL facade with role: {}", roleStr);
        AccessRole role = parseRole(roleStr);
        var command = new SignUpCommand(
                email,
                password,
                fullName,
                phoneNumber,
                true,
                role,
                businessId,
                familiarId
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
    public Long registerUser(String email, String password, String fullName, String phoneNumber, String role) {
        return signUpUser(email, password, fullName, phoneNumber, role, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerCompanyUser(String email, String password, String fullName, String phoneNumber,
                                   String role, Long businessId) {
        return signUpUser(email, password, fullName, phoneNumber, role, businessId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long registerAssistedPerson(String email, String password, String fullName, String phoneNumber,
                                      Long familiarId) {
        return signUpUser(email, password, fullName, phoneNumber, "ASSISTED_PERSON", null, familiarId);
    }

    private static AccessRole parseRole(String roleStr) {
        try {
            return com.resolum.alivia.iam.domain.services.RoleParser.parse(roleStr);
        } catch (IllegalArgumentException e) {
            return AccessRole.CAREGIVER;
        }
    }
}
