package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessName;

public class BusinessNameAlreadyExistsException extends RuntimeException {
    public BusinessNameAlreadyExistsException(BusinessName businessName) {
        this(businessName.businessName());
    }

    public BusinessNameAlreadyExistsException(String businessName) {
        super("Business name " + businessName + " already exists");
    }
}
