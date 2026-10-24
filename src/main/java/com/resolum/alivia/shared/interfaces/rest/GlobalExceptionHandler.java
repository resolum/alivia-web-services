package com.resolum.alivia.shared.interfaces.rest;

import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationConflict;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PreauthorizationConflict.class)
    public ProblemDetail handlePreauthorizationConflict(PreauthorizationConflict exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler(PreauthorizationUnavailable.class)
    public ProblemDetail handlePreauthorizationUnavailable(PreauthorizationUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
    @ExceptionHandler(PaymentRejected.class)
    public ProblemDetail handlePaymentRejected(PaymentRejected exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setProperty("status", "PAYMENT_REJECTED");
        return problem;
    }
    @ExceptionHandler(StripeWebhookProcessingFailed.class)
    public ProblemDetail handleStripeWebhookProcessingFailed(StripeWebhookProcessingFailed exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(InvalidStripeWebhookSignature.class)
    public ProblemDetail handleInvalidStripeWebhookSignature(InvalidStripeWebhookSignature exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StripeWebhookUnavailable.class)
    public ProblemDetail handleStripeWebhookUnavailable(StripeWebhookUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupNotFound.class)
    public ProblemDetail handlePaymentSetupNotFound(PaymentSetupNotFound exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupUnauthorized.class)
    public ProblemDetail handlePaymentSetupUnauthorized(PaymentSetupUnauthorized exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PlanNotFound.class)
    public ProblemDetail handlePlanNotFound(PlanNotFound exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupUnavailable.class)
    public ProblemDetail handlePaymentSetupUnavailable(PaymentSetupUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
