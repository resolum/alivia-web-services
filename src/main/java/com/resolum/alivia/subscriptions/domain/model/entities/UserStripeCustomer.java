package com.resolum.alivia.subscriptions.domain.model.entities;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class UserStripeCustomer {
    @Id
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String stripeCustomerId;

    protected UserStripeCustomer() {
    }

    public UserStripeCustomer(UserId userId, String stripeCustomerId) {
        if (userId == null || stripeCustomerId == null || stripeCustomerId.isBlank()) {
            throw new IllegalArgumentException("Se requieren User ID y Customer de Stripe.");
        }
        this.userId = userId.value();
        this.stripeCustomerId = stripeCustomerId;
    }
}
