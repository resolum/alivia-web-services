package com.resolum.alivia.shared.domain.model.aggregates;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

/**
 * Base class for all MongoDB aggregate roots that require auditing.
 *
 * @param <T> the type of the aggregate root
 * @summary The class is an abstract class that extends the {@link AbstractAggregateRoot} class and adds auditing fields to the class for MongoDB documents.
 */
@Getter
public abstract class AuditableMongoAggregateRoot<T extends AbstractAggregateRoot<T>> extends AbstractAggregateRoot<T> {

    @Id
    private String id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Registers a domain event.
     *
     * @param event the domain event to register
     */
    public void addDomainEvent(Object event) {
        super.registerEvent(event);
    }
}