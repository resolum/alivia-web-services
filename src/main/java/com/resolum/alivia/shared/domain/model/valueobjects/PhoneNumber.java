package com.resolum.alivia.shared.domain.model.valueobjects;

public record PhoneNumber(String number) {

    public PhoneNumber {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Number cannot be null or blank");
        }
    }

}
