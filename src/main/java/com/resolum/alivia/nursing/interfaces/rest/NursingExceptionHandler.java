package com.resolum.alivia.nursing.interfaces.rest;

import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationAdministrationNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps Nursing domain exceptions that the shared handler does not cover.
 */
@RestControllerAdvice(basePackages = "com.resolum.alivia.nursing")
public class NursingExceptionHandler {

    private static final String CODE_PROPERTY = "code";
    private static final String ERRORS_PROPERTY = "errors";

    /**
     * Answers 403 when the user is not linked to the requested assisted person.
     *
     * @param exception the access exception
     * @return the problem detail
     */
    @ExceptionHandler(AssistedPersonAccessDeniedException.class)
    public ProblemDetail handleAssistedPersonAccessDenied(AssistedPersonAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "assisted_person_access_denied",
                "The authenticated user is not allowed to access this assisted person.");
    }

    /**
     * Answers 403 when the user cannot access the medication administration.
     *
     * @param exception the access exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationAdministrationAccessDeniedException.class)
    public ProblemDetail handleMedicationAdministrationAccessDenied(MedicationAdministrationAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "medication_administration_access_denied",
                "The authenticated user is not allowed to access this medication administration.");
    }

    /**
     * Answers 404 when the medication administration does not exist.
     *
     * @param exception the not found exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationAdministrationNotFoundException.class)
    public ProblemDetail handleMedicationAdministrationNotFound(MedicationAdministrationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "medication_administration_not_found",
                "The medication administration does not exist.");
    }

    /**
     * Answers 409 when the medication administration already has an outcome.
     *
     * @param exception the conflict exception
     * @return the problem detail
     */
    @ExceptionHandler(MedicationOutcomeAlreadyRecordedException.class)
    public ProblemDetail handleMedicationOutcomeAlreadyRecorded(MedicationOutcomeAlreadyRecordedException exception) {
        return problem(HttpStatus.CONFLICT, "medication_outcome_already_recorded",
                "The medication outcome has already been recorded.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        problem.setProperty(ERRORS_PROPERTY, List.of());
        return problem;
    }
}
