package com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories;

import com.resolum.alivia.subscriptions.domain.model.entities.UserStripeCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStripeCustomerRepository extends JpaRepository<UserStripeCustomer, Long> {
}
