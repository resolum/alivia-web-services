package com.resolum.alivia.assistance.interfaces.rest.resources;

public record AcceptInvitationResource(
        Long caregiverUserId,
        String password,
        String firstName,
        String lastName,
        String phoneNumber
) {
    public AcceptInvitationResource(Long caregiverUserId) {
        this(caregiverUserId, null, null, null, null);
    }
}
