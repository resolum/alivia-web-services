package com.resolum.alivia.iam.infrastructure.authorization.sfs.services;

import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for Spring Security. The username is the internal user identifier taken
 * from the access token subject.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Creates the service.
     *
     * @param userRepository the user repository
     */
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by its internal identifier.
     *
     * @param userId the user identifier
     * @return the user details
     * @throws UsernameNotFoundException if the identifier is not numeric or no user matches
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            return userRepository.findById(Long.valueOf(userId))
                    .map(UserDetailsImpl::new)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        } catch (NumberFormatException exception) {
            throw new UsernameNotFoundException("Invalid user identifier: " + userId);
        }
    }
}
