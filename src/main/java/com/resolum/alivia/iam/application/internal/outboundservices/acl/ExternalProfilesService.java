package com.resolum.alivia.iam.application.internal.outboundservices.acl;

import com.resolum.alivia.iam.domain.model.valueobjects.ProfileData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbound service that creates the profile of a newly registered user in the
 * Profiles
 * bounded context.
 *
 * <p>
 * PENDING(profiles): delegate to {@code ProfilesContextFacade} once that
 * context
 * exposes it. Until then this adapter is a no-op so registration can be
 * exercised
 * locally.
 * </p>
 */
@Slf4j
@Service
public class ExternalProfilesService {

    /**
     * Creates the profile associated with a newly registered user.
     *
     * @param userId      the identifier of the new user
     * @param profileData the profile data provided during registration
     * @throws IllegalArgumentException if the user identifier or the profile data
     *                                  is missing
     */
    public void createProfile(Long userId, ProfileData profileData) {
        if (userId == null) {
            throw new IllegalArgumentException("User identifier is required.");
        }
        if (profileData == null) {
            throw new IllegalArgumentException("Profile data is required.");
        }
        log.info("Requesting profile creation for account id {}", userId);
        // PENDING(profiles): call ProfilesContextFacade.createProfile when it is
        // available.
        log.info("Profile creation is pending for account id {}", userId);
    }
}
