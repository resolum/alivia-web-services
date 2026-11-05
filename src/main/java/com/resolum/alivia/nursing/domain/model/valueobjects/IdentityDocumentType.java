package com.resolum.alivia.nursing.domain.model.valueobjects;

import java.util.regex.Pattern;

/**
 * Identity document accepted for an assisted person, with the shape of its number.
 */
public enum IdentityDocumentType {
    /** Peruvian national identity document: 8 digits. */
    DNI("\\d{8}"),
    /** Foreigner card (carné de extranjería): 9 to 12 letters or digits. */
    CE("[A-Za-z0-9]{9,12}"),
    /** Passport: 6 to 12 letters or digits. */
    PASSPORT("[A-Za-z0-9]{6,12}");

    private final Pattern numberPattern;

    IdentityDocumentType(String numberPattern) {
        this.numberPattern = Pattern.compile(numberPattern);
    }

    /**
     * Checks whether a document number has the shape of this type.
     *
     * @param number the document number
     * @return {@code true} when the number is valid for this type
     */
    public boolean accepts(String number) {
        return number != null && numberPattern.matcher(number).matches();
    }
}
