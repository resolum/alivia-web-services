package com.resolum.alivia.iam.domain.model.valueobjects;

import com.resolum.alivia.iam.domain.model.aggregates.User;

/**
 * A user with the full name of its profile, as an administrator sees it.
 *
 * @param user     the account
 * @param fullName the profile's full name; {@code null} when the account has no profile
 */
public record UserAccount(User user, String fullName) {
}
