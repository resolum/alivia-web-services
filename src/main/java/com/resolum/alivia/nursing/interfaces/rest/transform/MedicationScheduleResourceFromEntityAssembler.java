package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationScheduleResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationScheduleResource.MedicationRevisionResource;
import com.resolum.alivia.nursing.interfaces.rest.resources.MedicationScheduleResource.MedicationSuspensionResource;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MedicationScheduleResourceFromEntityAssembler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @SuppressWarnings("unchecked")
    public static MedicationScheduleResource toResourceFromEntity(
            CareActivity activity,
            String caregiverName,
            ObjectMapper objectMapper) {
        String idStr = activity.getId() != null ? String.valueOf(activity.getId()) : "";
        String assistedPersonIdStr = String.valueOf(activity.getAssistedPersonProfileId().personProfileId());

        List<ActivityAssignment> assignments = activity.getActivityAssignments() != null
                ? activity.getActivityAssignments().getActivityAssignments()
                : List.of();

        List<String> times = assignments.stream()
                .map(a -> a.getStartTime().format(TIME_FMT))
                .distinct()
                .sorted()
                .toList();

        Long caregiverId = assignments.isEmpty() ? null : assignments.get(0).getCaregiverId();
        String caregiverIdStr = caregiverId != null ? String.valueOf(caregiverId) : "";

        Map<String, Object> metadata = null;
        if (activity.getDescription() != null && activity.getDescription().startsWith("{")) {
            try {
                metadata = objectMapper.readValue(activity.getDescription(), Map.class);
            } catch (Exception ignored) {
            }
        }

        String medication = metadata != null && metadata.containsKey("medication")
                ? (String) metadata.get("medication")
                : activity.getTitle();

        String dose = metadata != null && metadata.containsKey("dose")
                ? (String) metadata.get("dose")
                : "";

        String startDate = metadata != null && metadata.containsKey("startDate")
                ? (String) metadata.get("startDate")
                : (activity.getCreatedAt() != null ? activity.getCreatedAt().toString().substring(0, 10) : "");

        String endDate = metadata != null && metadata.containsKey("endDate")
                ? (String) metadata.get("endDate")
                : null;

        String frequency = metadata != null && metadata.containsKey("frequency")
                ? (String) metadata.get("frequency")
                : "daily";

        String indicationRef = metadata != null && metadata.containsKey("indicationRef")
                ? (String) metadata.get("indicationRef")
                : (activity.getDescription() != null && !activity.getDescription().startsWith("{")
                        ? activity.getDescription() : "");

        List<MedicationRevisionResource> revisions = new ArrayList<>();
        if (metadata != null && metadata.containsKey("revisions")) {
            List<Map<String, Object>> revList = (List<Map<String, Object>>) metadata.get("revisions");
            for (Map<String, Object> revMap : revList) {
                revisions.add(new MedicationRevisionResource(
                        (String) revMap.getOrDefault("effectiveFrom", startDate),
                        (String) revMap.getOrDefault("dose", dose),
                        (List<String>) revMap.getOrDefault("times", times),
                        (String) revMap.getOrDefault("frequency", frequency),
                        (String) revMap.getOrDefault("indicationRef", indicationRef),
                        (String) revMap.getOrDefault("recordedAt", ""),
                        (String) revMap.getOrDefault("recordedByName", "Responsable")
                ));
            }
        }
        if (revisions.isEmpty()) {
            revisions.add(new MedicationRevisionResource(
                    startDate,
                    dose,
                    times,
                    frequency,
                    indicationRef,
                    activity.getCreatedAt() != null ? activity.getCreatedAt().toString() : "",
                    "Responsable"
            ));
        }

        MedicationSuspensionResource suspension = null;
        if (metadata != null && metadata.containsKey("suspension")) {
            Map<String, Object> suspMap = (Map<String, Object>) metadata.get("suspension");
            if (suspMap != null) {
                suspension = new MedicationSuspensionResource(
                        (String) suspMap.getOrDefault("from", ""),
                        (String) suspMap.getOrDefault("source", ""),
                        (String) suspMap.getOrDefault("recordedAt", ""),
                        (String) suspMap.getOrDefault("recordedByName", "Responsable")
                );
            }
        } else if (!activity.isActive()) {
            suspension = new MedicationSuspensionResource(
                    startDate,
                    "Suspendido",
                    "",
                    "Responsable"
            );
        }

        String status = activity.isActive() ? "active" : "suspended";

        return new MedicationScheduleResource(
                idStr,
                medication,
                dose,
                assistedPersonIdStr,
                caregiverIdStr,
                caregiverName != null ? caregiverName : "",
                startDate,
                endDate,
                status,
                revisions,
                suspension
        );
    }
}
