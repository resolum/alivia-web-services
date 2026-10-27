package com.resolum.alivia.subscriptions.domain.model.valueobjects;

public record PaymentResult(PaymentStatus status, String receiptUrl, String failureMessage) {
    public PaymentResult {
        if (status == null) throw new IllegalArgumentException("El estado del pago es obligatorio.");
    }
    public static PaymentResult succeeded(String receiptUrl) { return new PaymentResult(PaymentStatus.SUCCEEDED, receiptUrl, null); }
    public static PaymentResult failed(String failureMessage) { return new PaymentResult(PaymentStatus.FAILED, null, failureMessage); }
}
