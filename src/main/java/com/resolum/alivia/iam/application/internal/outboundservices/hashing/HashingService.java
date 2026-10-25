package com.resolum.alivia.iam.application.internal.outboundservices.hashing;

/**
 * Outbound port for password hashing.
 */
public interface HashingService {

    /**
     * Encodes a plain-text password.
     *
     * @param rawPassword the plain-text password
     * @return the encoded password
     */
    String encode(String rawPassword);
    /**
     * Match a raw password with an encoded password
     * @param rawPassword the raw password
     * @param encodedPassword the encoded password
     * @return boolean true if the raw password matches the encoded password, false otherwise
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
