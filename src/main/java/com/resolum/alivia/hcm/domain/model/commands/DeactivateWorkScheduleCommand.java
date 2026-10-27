package com.resolum.alivia.hcm.domain.model.commands;

public record DeactivateWorkScheduleCommand(Long employeeId, Long workScheduleId, Long businessId,
                                            Long actorUserId) {
}
