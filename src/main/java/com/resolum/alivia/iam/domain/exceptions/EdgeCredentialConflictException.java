package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when two requests issued the first credential of the same edge at the same time. The loser can try again,
 * which then replaces the secret of the winner.
 */
public class EdgeCredentialConflictException extends RuntimeException {

    public EdgeCredentialConflictException() {
        super("The credential of this edge was issued by another request at the same time.");
    }
}
