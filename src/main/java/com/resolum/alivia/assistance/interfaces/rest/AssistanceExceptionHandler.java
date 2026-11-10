package com.resolum.alivia.assistance.interfaces.rest;

import com.resolum.alivia.assistance.domain.exceptions.ActivityAssignmentNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.ActivityAssignmentNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.AssistedPersonNotManagedByRelativeException;
import com.resolum.alivia.assistance.domain.exceptions.BusinessNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CareActivityNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAccountNotAssignedException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverAlreadyRevokedException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotActiveException;
import com.resolum.alivia.assistance.domain.exceptions.CaregiverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.DayNotClosedYetException;
import com.resolum.alivia.assistance.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.InvalidScheduleException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationAlreadyExistsException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationExpiredException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotForUserException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.InvitationNotPendingException;
import com.resolum.alivia.assistance.domain.exceptions.NotResponsibleForActivityException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceClosedException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceEndedException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotScheduledException;
import com.resolum.alivia.assistance.domain.exceptions.OccurrenceNotStartedException;
import com.resolum.alivia.assistance.domain.exceptions.RelativeNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.ScheduleConflictException;
import com.resolum.alivia.assistance.domain.exceptions.ScheduleNotEditableException;
import com.resolum.alivia.assistance.domain.exceptions.CareShiftNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotFoundException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftConflictException;
import com.resolum.alivia.assistance.domain.exceptions.ShiftHasPendingHandoverException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotPendingException;
import com.resolum.alivia.assistance.domain.exceptions.HandoverNotForCaregiverException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(basePackages = "com.resolum.alivia.assistance")
public class AssistanceExceptionHandler {

    private static final String CODE_PROPERTY = "code";
    private static final String ERRORS_PROPERTY = "errors";

    // 404 Not Found
    @ExceptionHandler({
            InvitationNotFoundException.class,
            RelativeNotFoundException.class,
            BusinessNotFoundException.class,
            AssistedPersonNotFoundException.class,
            CaregiverNotFoundException.class,
            CareActivityNotFoundException.class,
            ActivityAssignmentNotFoundException.class,
            CareShiftNotFoundException.class,
            HandoverNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "resource_not_found", ex.getMessage());
    }

    // 403 Forbidden
    @ExceptionHandler({
            AssistedPersonNotManagedByRelativeException.class,
            InvitationNotForUserException.class,
            NotResponsibleForActivityException.class,
            HandoverNotForCaregiverException.class
    })
    public ProblemDetail handleForbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, "access_denied", ex.getMessage());
    }

    // 409 Conflict
    @ExceptionHandler({
            InvitationNotPendingException.class,
            InvitationExpiredException.class,
            InvitationAlreadyExistsException.class,
            CaregiverAccountNotAssignedException.class,
            CaregiverAlreadyExistsException.class,
            CaregiverAlreadyRevokedException.class,
            CaregiverNotActiveException.class,
            CareActivityNotActiveException.class,
            ActivityAssignmentNotActiveException.class,
            ScheduleConflictException.class,
            ScheduleNotEditableException.class,
            OccurrenceClosedException.class,
            DniAlreadyExistsException.class,
            EmailAddressAlreadyExistsException.class,
            ShiftConflictException.class
    })
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "conflict", ex.getMessage());
    }

    @ExceptionHandler(ShiftHasPendingHandoverException.class)
    public ProblemDetail handleShiftHasPendingHandover(ShiftHasPendingHandoverException ex) {
        return problem(HttpStatus.CONFLICT, "has_pending", ex.getMessage());
    }

    @ExceptionHandler(HandoverNotPendingException.class)
    public ProblemDetail handleHandoverNotPending(HandoverNotPendingException ex) {
        return problem(HttpStatus.CONFLICT, "already_recorded", ex.getMessage());
    }

    // 400 Bad Request
    @ExceptionHandler({
            InvalidScheduleException.class,
            DayNotClosedYetException.class,
            OccurrenceNotScheduledException.class,
            OccurrenceNotStartedException.class,
            OccurrenceEndedException.class
    })
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        problem.setProperty(ERRORS_PROPERTY, List.of());
        return problem;
    }
}
