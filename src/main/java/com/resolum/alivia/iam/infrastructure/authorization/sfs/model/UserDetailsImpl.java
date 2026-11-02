package com.resolum.alivia.iam.infrastructure.authorization.sfs.model;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts the {@link User} aggregate to Spring Security's {@link UserDetails}.
 *
 * <p>The username is the internal user identifier, the same value stored in the access
 * token subject, so {@code Principal#getName()} returns the authenticated user id.</p>
 */
public class UserDetailsImpl implements UserDetails {

    private final User user;

    /**
     * Creates the adapter.
     *
     * @param user the user aggregate
     */
    public UserDetailsImpl(User user) {
        this.user = user;
    }

    /**
     * Returns the internal user identifier.
     *
     * @return the user identifier as a string
     */
    @Override
    public String getUsername() {
        return String.valueOf(user.getId());
    }

    /**
     * Returns the password hash.
     *
     * @return the password hash
     */
    @Override
    public String getPassword() {
        return user.getPassword().value();
    }

    /**
     * Returns the access role as the only authority.
     *
     * @return the granted authorities
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getAccessRole().name()));
    }

    /**
     * Indicates whether the account may use the API. Matches {@link User#ensureCanSignIn()}:
     * accounts pending verification are allowed.
     *
     * @return {@code false} when the account is blocked or inactive
     */
    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.PENDING_VERIFICATION;
    }
}
