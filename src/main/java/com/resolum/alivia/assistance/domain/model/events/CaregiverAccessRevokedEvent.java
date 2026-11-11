package com.resolum.alivia.assistance.domain.model.events;

public record CaregiverAccessRevokedEvent(Long caregiverId, Long assistedPersonId) {
}
