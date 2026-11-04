package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;

/**
 * Creates an order in a block. The contact fields may be null for an installation: they are then copied
 * from the evaluation of the same request.
 */
public record CreateTechnicalOrderCommand(String draftId, ServiceType serviceType, SlotId slotId, String contactName,
                                          String contactPhone, String directions) {
}
