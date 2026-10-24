
package com.resolum.alivia.shared.domain.model.valueobjects;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

public record PhoneNumber(String phoneNumber) {

    private static final PhoneNumberUtil UTIL =
            PhoneNumberUtil.getInstance();

    public PhoneNumber {
        if (phoneNumber == null) {
            throw new IllegalArgumentException(
                    "Phone number cannot be null."
            );
        }

        if (!phoneNumber.matches("[1-9]\\d{0,14}")) {
            throw new IllegalArgumentException(
                    "Phone number must contain 1 to 15 digits."
            );
        }

        try {
            var number = UTIL.parse("+" + phoneNumber, null);

            if (!UTIL.isValidNumber(number)
                    || !UTIL.format(
                    number,
                    PhoneNumberUtil.PhoneNumberFormat.E164
            ).substring(1).equals(phoneNumber)) {
                throw new IllegalArgumentException(
                        "Invalid international phone number."
                );
            }
        } catch (NumberParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid international phone number.",
                    exception
            );
        }
    }

    public String countryCallingCode() {
        try {
            return String.valueOf(
                    UTIL.parse("+" + phoneNumber, null)
                            .getCountryCode()
            );
        } catch (NumberParseException exception) {
            throw new IllegalStateException(
                    "Stored phone number is invalid.",
                    exception
            );
        }
    }
}
