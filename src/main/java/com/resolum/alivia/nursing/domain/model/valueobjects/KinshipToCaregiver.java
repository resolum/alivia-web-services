package com.resolum.alivia.nursing.domain.model.valueobjects;

/**
 * What the assisted person is to the principal caregiver who registers them.
 */
public enum KinshipToCaregiver {
    /** Mother or father. */
    PARENT,
    /** Grandmother or grandfather. */
    GRANDPARENT,
    /** Aunt or uncle. */
    AUNT_UNCLE,
    /** Wife or husband. */
    SPOUSE,
    /** Sister or brother. */
    SIBLING,
    /** Any other relationship. */
    OTHER
}
