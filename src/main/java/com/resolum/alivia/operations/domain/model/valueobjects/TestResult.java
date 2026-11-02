package com.resolum.alivia.operations.domain.model.valueobjects;

/** Result of the functional test the technician runs after the maintenance. */
public enum TestResult {
    /** The device works again: the incident is resolved. */
    PASSED,
    /** The device still fails: the incident opens again for another visit. */
    FAILED
}
