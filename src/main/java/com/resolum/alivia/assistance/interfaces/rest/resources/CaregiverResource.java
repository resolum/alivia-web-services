package com.resolum.alivia.assistance.interfaces.rest.resources;

public record CaregiverResource(
        Long id,
        Long userId,
        Long assistedPersonId,
        String status,
        String fullName,
        String email,
        String phoneNumber,
        String photoUrl
) {
    public CaregiverResource(Long id, Long userId, Long assistedPersonId, String status) {
        this(id, userId, assistedPersonId, status, null, null, null, null);
    }
}
