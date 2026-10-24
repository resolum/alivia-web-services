package com.resolum.alivia.iam.application.acl;

import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the IAM anti-corruption layer facade.
 *
 * <p>Delegates to the domain repository and returns primitive types only, so external
 * bounded contexts do not depend on IAM internals.</p>
 */
@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserRepository userRepository;

    /**
     * Creates the facade.
     *
     * @param userRepository the user persistence port
     */
    public IamContextFacadeImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository.existsByEmail(new EmailAddress(email));
    }
}
