package com.resolum.alivia.assets.domain.exceptions;

/** The account neither manages the household of the installation nor administers the business. */
public class InstallationAccessDenied extends RuntimeException {
    public InstallationAccessDenied() {
        super("The authenticated user is not allowed to manage the devices of this installation.");
    }
}
