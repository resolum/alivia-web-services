package com.resolum.alivia.profiles.domain.services;

/**
 * Decides who may read or change a person or staff profile by its id.
 *
 * <p>An administrator may do both. The owner of a profile may do both on it. The care network of an assisted person
 * (who has no account of their own) may read the person's profile and, when it takes care of the person, change it.
 * Every check is made before the profile is looked up, so a missing profile and one of someone else answer the
 * same to who has no access.</p>
 */
public interface ProfileAccessService {

    void assertCanReadPersonProfile(Long profileId, Long userId, boolean administrator);

    void assertCanChangePersonProfile(Long profileId, Long userId, boolean administrator);

    /** A staff profile is read or changed by an administrator or by the employee it belongs to. */
    void assertCanAccessStaffProfile(Long profileId, Long userId, boolean administrator);
}
