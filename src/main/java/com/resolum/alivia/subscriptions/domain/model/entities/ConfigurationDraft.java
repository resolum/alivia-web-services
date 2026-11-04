package com.resolum.alivia.subscriptions.domain.model.entities;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantities;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
public class ConfigurationDraft {
    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanCode planCode;

    @Column(nullable = false, length = 10)
    private String billingCycle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "configuration_draft_rooms", joinColumns = @JoinColumn(name = "draft_id"))
    @OrderColumn(name = "display_order")
    private List<ConfiguredRoom> rooms = new ArrayList<>();

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 64)
    private String stripeCustomerId;

    @Column(length = 64)
    private String stripeSetupIntentId;

    @Column(length = 64)
    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentStatus paymentStatus;

    private Instant captureBefore;

    private Instant preauthorizedAt;

    private Instant confirmedAt;

    /** Account of the family that contracts; linked when that account signs in with the request in its browser. */
    private Long ownerUserId;

    protected ConfigurationDraft() {
    }

    public ConfigurationDraft(PlanCode planCode, String billingCycle) {
        if (planCode == null || !("MONTHLY".equals(billingCycle) || "ANNUAL".equals(billingCycle))) {
            throw new IllegalArgumentException("El plan o la periodicidad no son válidos.");
        }
        this.id = UUID.randomUUID().toString();
        this.planCode = planCode;
        this.billingCycle = billingCycle;
        this.updatedAt = Instant.now();
    }

    public void replaceRooms(List<ConfiguredRoom> nextRooms) {
        if (nextRooms == null || nextRooms.isEmpty()) {
            throw new IllegalArgumentException("La configuración debe incluir al menos una habitación.");
        }
        if (nextRooms.size() > 3) {
            throw new IllegalArgumentException("La configuración admite hasta tres habitaciones.");
        }
        if (planCode == PlanCode.MINIMUM && nextRooms.size() != 1) {
            throw new IllegalArgumentException("El Plan Base admite una sola habitación.");
        }
        var keys = new HashSet<String>();
        if (nextRooms.stream().anyMatch(room -> !keys.add(room.getKey()))) {
            throw new IllegalArgumentException("No se permiten habitaciones duplicadas.");
        }
        int actuators = nextRooms.stream().mapToInt(ConfiguredRoom::actuatorCount).sum();
        if (actuators > (planCode == PlanCode.MINIMUM ? 1 : 4)) {
            throw new IllegalArgumentException("La selección supera el máximo de actuadores del plan.");
        }
        rooms.clear();
        rooms.addAll(nextRooms);
        updatedAt = Instant.now();
    }

    public ActuatorQuantities actuatorQuantities() {
        return new ActuatorQuantities(
                rooms.stream().mapToInt(ConfiguredRoom::getLights).sum(),
                rooms.stream().mapToInt(ConfiguredRoom::getDoors).sum(),
                rooms.stream().mapToInt(ConfiguredRoom::getWindows).sum(),
                rooms.stream().mapToInt(ConfiguredRoom::getCurtains).sum());
    }

    public void linkStripeCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("El cliente de Stripe es obligatorio.");
        }
        if (stripeCustomerId != null && !stripeCustomerId.equals(customerId)) {
            throw new IllegalStateException("El borrador ya pertenece a otro cliente de Stripe.");
        }
        stripeCustomerId = customerId;
        updatedAt = Instant.now();
    }

    public void recordPaymentSetup(String setupIntentId) {
        if (stripeCustomerId == null || setupIntentId == null || setupIntentId.isBlank()) {
            throw new IllegalStateException("Primero debe prepararse el cliente y el registro de pago.");
        }
        stripeSetupIntentId = setupIntentId;
        updatedAt = Instant.now();
    }

    public void recordPreauthorization(StripeAuthorization authorization) {
        if (authorization == null) throw new IllegalArgumentException("La preautorización es obligatoria.");
        stripePaymentIntentId = authorization.intentId().value();
        paymentStatus = authorization.state();
        captureBefore = authorization.captureBefore();
        if (preauthorizedAt == null) preauthorizedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public boolean preauthorizationReleaseDue(Instant now) {
        if (paymentStatus != PaymentStatus.PREAUTHORIZED || preauthorizedAt == null) return false;
        var deadline = preauthorizedAt.plusSeconds(7 * 24 * 3600L);
        if (captureBefore != null && captureBefore.minusSeconds(12 * 3600L).isBefore(deadline)) {
            deadline = captureBefore.minusSeconds(12 * 3600L);
        }
        return !now.isBefore(deadline);
    }

    public void markPreauthorizationCanceled() {
        paymentStatus = PaymentStatus.CANCELED;
        updatedAt = Instant.now();
    }

    /** The capture was requested; the signed Stripe webhook is the authority that confirms its outcome. */
    public void markCaptureRequested() {
        if (paymentStatus == PaymentStatus.SUCCEEDED) return;
        if (paymentStatus != PaymentStatus.PREAUTHORIZED && paymentStatus != PaymentStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("La preautorización no está disponible para captura.");
        }
        paymentStatus = PaymentStatus.AWAITING_CONFIRMATION;
        updatedAt = Instant.now();
    }

    public void confirmCapturedPayment() {
        paymentStatus = PaymentStatus.SUCCEEDED;
        updatedAt = Instant.now();
    }

    public void recordCaptureFailure() {
        if (paymentStatus != PaymentStatus.SUCCEEDED) paymentStatus = PaymentStatus.FAILED;
        updatedAt = Instant.now();
    }

    /** The family confirmed the configuration that the technician validated; confirming again changes nothing. */
    public void confirm(Instant now) {
        if (now == null) throw new IllegalArgumentException("La fecha de confirmación es obligatoria.");
        if (confirmedAt != null) return;
        confirmedAt = now;
        updatedAt = now;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /**
     * Links the request to the family's account. The request is built before the account exists, so the
     * first account that claims it keeps it; another account cannot take it.
     *
     * @return true when the request belongs to {@code userId} after the call
     */
    public boolean claimBy(Long userId) {
        if (userId == null) throw new IllegalArgumentException("La cuenta es obligatoria.");
        if (ownerUserId == null) ownerUserId = userId;
        return ownerUserId.equals(userId);
    }

    /** Whether the family already paid (or had paid) the preauthorization, so the request left the configuration. */
    public boolean isPaid() {
        return preauthorizedAt != null;
    }
}
