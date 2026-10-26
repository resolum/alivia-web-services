package com.resolum.alivia.profiles.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

public record ExistsPersonProfileByEmailAddressQuery(String emailAddress) {
    public ExistsPersonProfileByEmailAddressQuery {
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
    }

    public ExistsPersonProfileByEmailAddressQuery(EmailAddress emailAddress) {
        this(emailAddress != null ? emailAddress.email() : null);
    }
}
