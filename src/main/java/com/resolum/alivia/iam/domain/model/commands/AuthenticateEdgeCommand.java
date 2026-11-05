package com.resolum.alivia.iam.domain.model.commands;

/**
 * Asks who owns a credential presented by an edge.
 *
 * @param presentedCredential the credential exactly as the edge sent it (never logged)
 */
public record AuthenticateEdgeCommand(String presentedCredential) {

    /** Hides the credential: a record would otherwise print it. */
    @Override
    public String toString() {
        return "AuthenticateEdgeCommand[presentedCredential=***]";
    }
}
