package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

public record ExistsStaffProfileByEmailAddressQuery(String emailAddress) {
    public ExistsStaffProfileByEmailAddressQuery {
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
    }

    public ExistsStaffProfileByEmailAddressQuery(EmailAddress emailAddress) {
        this(emailAddress != null ? emailAddress.email() : null);
    }
}
