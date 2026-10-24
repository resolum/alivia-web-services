package com.resolum.alivia.iam.interfaces.acl;

/**
 * Facade for other bounded contexts that need to query IAM data.
 *
 * <p>It exposes primitive types only, so consumers never depend on IAM aggregates or
 * value objects.</p>
 */
public interface IamContextFacade {

    /**
     * Checks whether a user with the given email address exists.
     *
     * @param email the email address to check
     * @return {@code true} when a user is registered with that email; {@code false} when
     *         the email is missing or invalid
     */
    boolean existsUserByEmail(String email);
}
