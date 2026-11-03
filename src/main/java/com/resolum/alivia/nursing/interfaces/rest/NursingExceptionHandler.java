package com.resolum.alivia.nursing.interfaces.rest;

import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonNotInHouseholdException;
import com.resolum.alivia.nursing.domain.exceptions.CareMemberProfileNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.CareNetworkHouseholdNotFoundException;
import com.resolum.alivia.nursing.domain.exceptions.CaregiverAlreadyLinkedException;
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

    /**
     * Answers 404 when the household of the care network does not exist. It reuses the code of
     * Profiles so clients handle a missing household the same way on every household endpoint.
     *
     * @param exception the not found exception
     * @return the problem detail
     */
    @ExceptionHandler(CareNetworkHouseholdNotFoundException.class)
    public ProblemDetail handleCareNetworkHouseholdNotFound(CareNetworkHouseholdNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "household_not_found", "The household does not exist.");
    }

    /**
     * Answers 403 when the user cannot read or manage the household. It reuses the code of Profiles.
     *
     * @param exception the access exception
     * @return the problem detail
     */
    @ExceptionHandler(CareNetworkAccessDeniedException.class)
    public ProblemDetail handleCareNetworkAccessDenied(CareNetworkAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "household_access_denied",
                "The authenticated user is not allowed to access this household.");
    }

    /**
     * Answers 404 when the assisted person does not belong to the household.
     *
     * @param exception the not found exception
     * @return the problem detail
     */
    @ExceptionHandler(AssistedPersonNotInHouseholdException.class)
    public ProblemDetail handleAssistedPersonNotInHousehold(AssistedPersonNotInHouseholdException exception) {
        return problem(HttpStatus.NOT_FOUND, "assisted_person_not_found",
                "The assisted person does not exist in this household.");
    }

    /**
     * Answers 404 when the user to link has no person profile, which includes users that do not exist.
     *
     * @param exception the not found exception
     * @return the problem detail
     */
    @ExceptionHandler(CareMemberProfileNotFoundException.class)
    public ProblemDetail handleCareMemberProfileNotFound(CareMemberProfileNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "person_profile_not_found",
                "The user does not exist or has no person profile.");
    }

    /**
     * Answers 409 when the caregiver is already linked to the assisted person.
     *
     * @param exception the conflict exception
     * @return the problem detail
     */
    @ExceptionHandler(CaregiverAlreadyLinkedException.class)
    public ProblemDetail handleCaregiverAlreadyLinked(CaregiverAlreadyLinkedException exception) {
        return problem(HttpStatus.CONFLICT, "caregiver_already_linked",
                "The caregiver is already linked to this assisted person.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        problem.setProperty(ERRORS_PROPERTY, List.of());
        return problem;
    }
}
