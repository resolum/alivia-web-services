package com.resolum.alivia.business.domain.exceptions;

public class BackupPersonalEmailAlreadyExists extends RuntimeException {
    public BackupPersonalEmailAlreadyExists() {
        super("Backup personal email already exists");
    }

    public BackupPersonalEmailAlreadyExists(String email) {
        super("Backup personal email " + email + " already exists");
    }
}
