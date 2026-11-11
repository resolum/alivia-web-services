package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.commands.AssignShiftCommand;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssignShiftResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareShiftResource;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class CareShiftResourceAssembler {

    public static CareShiftResource toResourceFromEntity(CareShift entity) {
        String startsAt = entity.getStartsAt() != null
                ? entity.getStartsAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                : null;
        String endsAt = entity.getEndsAt() != null
                ? entity.getEndsAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                : null;

        return new CareShiftResource(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getCaregiverId()),
                entity.getCaregiverName(),
                startsAt,
                endsAt,
                entity.getNotes() != null ? entity.getNotes() : ""
        );
    }

    public static AssignShiftCommand toCommandFromResource(AssignShiftResource resource, Long resolvedAssistedPersonId) {
        return toCommandFromResource(resource, resolvedAssistedPersonId, null);
    }

    public static AssignShiftCommand toCommandFromResource(AssignShiftResource resource, Long resolvedAssistedPersonId, UserId userId) {
        Long shiftId = resource.shiftId() != null && !resource.shiftId().isBlank()
                ? Long.parseLong(resource.shiftId())
                : null;
        Long assistedPersonId = resource.assistedPersonId() != null
                ? resource.assistedPersonId()
                : resolvedAssistedPersonId;
        Long caregiverId = resource.caregiverId() != null && !resource.caregiverId().isBlank()
                ? Long.parseLong(resource.caregiverId())
                : 0L;

        LocalDateTime startsAt = parseDateTime(resource.startsAt());
        LocalDateTime endsAt = parseDateTime(resource.endsAt());

        boolean allowConflict = Boolean.TRUE.equals(resource.allowConflict());

        return new AssignShiftCommand(
                shiftId,
                assistedPersonId,
                userId,
                caregiverId,
                resource.caregiverName() != null ? resource.caregiverName() : "",
                startsAt,
                endsAt,
                resource.notes() != null ? resource.notes() : "",
                allowConflict
        );
    }

    public static LocalDateTime parseDateTime(String iso) {
        if (iso == null || iso.isBlank()) return LocalDateTime.now();
        try {
            return Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception ex) {
                return LocalDateTime.parse(iso);
            }
        }
    }
}
