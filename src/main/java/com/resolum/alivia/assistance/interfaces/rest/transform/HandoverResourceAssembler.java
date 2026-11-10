package com.resolum.alivia.assistance.interfaces.rest.transform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.interfaces.rest.resources.HandoverResource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class HandoverResourceAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static HandoverResource toResourceFromEntity(Handover entity) {
        String requestedAt = formatDateTime(entity.getRequestedAt());
        String respondedAt = formatDateTime(entity.getRespondedAt());
        String escalatedAt = formatDateTime(entity.getEscalatedAt());

        List<String> attended = parseTaskList(entity.getAttendedTasks());
        List<String> pending = parseTaskList(entity.getPendingTasks());

        return new HandoverResource(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getFromShift().getId()),
                String.valueOf(entity.getToShift().getId()),
                String.valueOf(entity.getFromCaregiverId()),
                entity.getFromCaregiverName(),
                String.valueOf(entity.getToCaregiverId()),
                entity.getToCaregiverName(),
                entity.getStatus().name().toLowerCase(),
                requestedAt,
                respondedAt,
                escalatedAt,
                attended,
                pending,
                entity.isSubstitute()
        );
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static List<String> parseTaskList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
