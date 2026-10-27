package com.resolum.alivia.nursing.interfaces.rest;

import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Nursing domain exceptions that the shared handler does not cover.
 */
@RestControllerAdvice(basePackages = "com.resolum.alivia.nursing")
public class NursingExceptionHandler {

    /**
     * Answers 403 when the user is not linked to the requested assisted person.
     *
     * @param exception the access exception
     * @return the problem detail
     */
    @ExceptionHandler(AssistedPersonAccessDeniedException.class)
    public ProblemDetail handleAssistedPersonAccessDenied(AssistedPersonAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * Answers 403 when the user cannot access the medication administration.
     *
     * @param exception the access exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationAdministrationAccessDeniedException.class)
    public ProblemDetail handleMedicationAdministrationAccessDenied(MedicationAdministrationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * Answers 404 when the medication administration does not exist.
     *
     * @param exception the not found exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationAdministrationNotFoundException.class)
    public ProblemDetail handleMedicationAdministrationNotFound(MedicationAdministrationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * Answers 409 when the medication administration already has an outcome.
     *
     * @param exception the conflict exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationOutcomeAlreadyRecordedException.class)
    public ProblemDetail handleMedicationOutcomeAlreadyRecorded(MedicationOutcomeAlreadyRecordedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
