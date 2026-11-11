package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.assistance.interfaces.rest.resources.ActivityLogResource;

public class ActivityLogResourceAssembler {
    public static ActivityLogResource toResourceFromEntity(ActivityLog entity) {
        return new ActivityLogResource(
                entity.getId(),
                entity.getAssistedPerson() != null ? entity.getAssistedPerson().getId() : null,
                entity.getCareActivity() != null ? entity.getCareActivity().getId() : null,
                entity.getAssignmentId(),
                entity.getDate(),
                entity.getTitle(),
                entity.getTimeRange() != null ? entity.getTimeRange().start() : null,
                entity.getTimeRange() != null ? entity.getTimeRange().end() : null,
                entity.getAssignedCaregiver() != null ? entity.getAssignedCaregiver().getId() : null,
                entity.getStatus().name(),
                entity.getCompletedBy() != null ? entity.getCompletedBy().getId() : null,
                entity.getCompletedAt(),
                entity.getNotes()
        );
    }
}
