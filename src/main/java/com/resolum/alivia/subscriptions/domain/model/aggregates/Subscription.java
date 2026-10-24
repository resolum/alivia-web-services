package com.resolum.alivia.subscriptions.domain.model.aggregates;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HouseholdId;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.AssistedPersonId;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.BillingPeriod;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = "active_household_id"))
@Getter
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    private Long assistedPersonId;
    private Long businessId;
    @Column(nullable = false) private String householdId;
    @Column(name = "active_household_id", unique = true) private String activeHouseholdId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "subscription_actuators", joinColumns = @JoinColumn(name = "subscription_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "actuator_type")
    @Column(name = "quantity", nullable = false)
    private Map<ActuatorType, Integer> actuators = new EnumMap<>(ActuatorType.class);
    @Column(name = "monthly_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SubscriptionStatus status;
    @Column(nullable = false, unique = true) private String idempotencyKey;
    @Column(length = 100) private String stripeCustomerId;
    @Column(length = 100) private String stripeSubscriptionId;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    @Column(nullable = false) private boolean refunded;
    protected Subscription() { }
    public Subscription(UserId userId, HouseholdId householdId, Plan plan, ActuatorSelection selection,
                        Money monthlyAmount, String stripeCustomerId, String idempotencyKey) {
        if (userId == null || householdId == null || plan == null || selection == null || monthlyAmount == null
                || stripeCustomerId == null || stripeCustomerId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("La contratación requiere usuario, hogar, plan, actuadores e importe.");
        this.userId = userId.value(); this.householdId = householdId.value(); this.activeHouseholdId = householdId.value();
        this.plan = plan; this.idempotencyKey = idempotencyKey;
        selection.items().forEach(item -> this.actuators.put(item.type(), item.quantity()));
        this.monthlyAmount = monthlyAmount.amount(); this.currency = monthlyAmount.currency();
        this.stripeCustomerId = stripeCustomerId;
        this.status = SubscriptionStatus.PENDING_PAYMENT;
    }
    public ActuatorSelection getActuatorSelection() {
        return new ActuatorSelection(actuators.entrySet().stream()
                .map(entry -> new com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity(entry.getKey(), entry.getValue()))
                .toList());
    }
    public AssistedPersonId getAssistedPerson() { return assistedPersonId == null ? null : new AssistedPersonId(assistedPersonId); }
    public BusinessId getBusiness() { return businessId == null ? null : new BusinessId(businessId); }
    public void linkContext(AssistedPersonId assistedPersonId, BusinessId businessId) {
        this.assistedPersonId = assistedPersonId == null ? null : assistedPersonId.value();
        this.businessId = businessId == null ? null : businessId.value();
    }
    public Money getMonthlyPrice() { return new Money(monthlyAmount, currency); }
    public BillingPeriod getCurrentPeriod() {
        return currentPeriodStart == null ? null : new BillingPeriod(currentPeriodStart, currentPeriodEnd);
    }
    public void linkStripeSubscription(String stripeSubscriptionId) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) throw new IllegalArgumentException("Se requiere la suscripción de Stripe.");
        this.stripeSubscriptionId = stripeSubscriptionId;
    }
    public void activate(BillingPeriod period) {
        if (period == null || stripeSubscriptionId == null) throw new IllegalStateException("No se puede activar sin facturación confirmada.");
        this.currentPeriodStart = period.start(); this.currentPeriodEnd = period.end(); this.status = SubscriptionStatus.ACTIVE;
    }
    public void markPastDue() { this.status = SubscriptionStatus.PAST_DUE; }
    public void cancel() { this.status = SubscriptionStatus.CANCELED; this.activeHouseholdId = null; }
    public void refund() { this.refunded = true; }
    public boolean isActive() { return status == SubscriptionStatus.ACTIVE; }
    public boolean isCurrent() { return isActive() && currentPeriodEnd != null && currentPeriodEnd.isAfter(LocalDateTime.now()); }
}
