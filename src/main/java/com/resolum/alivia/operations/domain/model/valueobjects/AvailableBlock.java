package com.resolum.alivia.operations.domain.model.valueobjects;

/**
 * A block that can still take an order, without saying which technicians are free in it.
 *
 * @param capacity how many more orders the block can take (one per free technician)
 */
public record AvailableBlock(SlotId slotId, ServiceType serviceType, int capacity) {
}
