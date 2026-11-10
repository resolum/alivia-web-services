package com.resolum.alivia.assistance.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record EmergencyContact(
        @Column(name = "emergency_contact_name", length = 150)
        String name,

        @Column(name = "emergency_contact_phone", length = 15)
        String phone
) {
    public EmergencyContact {
        if (name != null) name = name.trim();
        if (phone != null) phone = phone.trim();
    }
}
