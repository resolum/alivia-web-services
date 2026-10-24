package com.resolum.alivia.iam.application.internal.outboundservices.acl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbound service that creates the profile of a newly registered user in the Profiles
 * bounded context.
 *
 * <p>PENDING(profiles): delegate to {@code ProfilesContextFacade} once that context
 * exposes it. Until then this adapter is a no-op so registration can be exercised
 * locally.</p>
 */
@Slf4j
@Service
public class ExternalProfilesService {

    /**
     * Creates the profile associated with a newly registered user.
     *
     * @param userId      the identifier of the new user
     * @param fullName    the full name provided during registration
     * @param phoneNumber the phone number provided during registration
     */
    public void createProfile(Long userId, String fullName, String phoneNumber) {
        log.info("Profile creation is pending for user id {}", userId);
    }
}
