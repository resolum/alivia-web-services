package com.resolum.alivia.nursing.application.internal.commandservices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationScheduleNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.commands.ChangeMedicationResponsibleCommand;
import com.resolum.alivia.nursing.domain.model.commands.CreateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.SuspendMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.commands.UpdateMedicationScheduleCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.model.valueobjects.Schedule;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleCommandService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.domain.model.valueobjects.WeeklyPeriod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service implementing medication lifecycle commands.
 */
@Slf4j
@Service
public class MedicationScheduleCommandServiceImpl implements MedicationScheduleCommandService {

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    private final CareActivityRepository careActivityRepository;
    private final CareMembershipRepository careMembershipRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public MedicationScheduleCommandServiceImpl(
            CareActivityRepository careActivityRepository,
            CareMembershipRepository careMembershipRepository,
            Clock clock,
            ObjectMapper objectMapper) {
        this.careActivityRepository = careActivityRepository;
        this.careMembershipRepository = careMembershipRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CareActivity handle(CreateMedicationScheduleCommand command) {
        log.info("Scheduling medication {} for assisted person {}", command.medication(),
                command.assistedPersonProfileId().personProfileId());
        validateAccess(command.userId(), command.assistedPersonProfileId());

        var today = LocalDate.now(clock.withZone(ZONE));
        String title = buildTitle(command.medication(), command.dose());

        var metadata = new HashMap<String, Object>();
        metadata.put("medication", command.medication());
        metadata.put("dose", command.dose() != null ? command.dose() : "");
        metadata.put("frequency", command.frequency());
        metadata.put("startDate", command.startDate().toString());
        if (command.endDate() != null) {
            metadata.put("endDate", command.endDate().toString());
        }
        metadata.put("indicationRef", command.indicationRef() != null ? command.indicationRef() : "");

        var initialRevision = new HashMap<String, Object>();
        initialRevision.put("effectiveFrom", command.startDate().toString());
        initialRevision.put("dose", command.dose() != null ? command.dose() : "");
        initialRevision.put("times", command.times());
        initialRevision.put("frequency", command.frequency());
        initialRevision.put("indicationRef", command.indicationRef() != null ? command.indicationRef() : "");
        initialRevision.put("recordedAt", java.time.Instant.now(clock).toString());
        initialRevision.put("recordedByName", "Responsable");
        metadata.put("revisions", List.of(initialRevision));

        String descriptionJson = toJson(metadata);
        var activity = new CareActivity(command.assistedPersonProfileId(), CareActivityType.MEDICATION,
                title, descriptionJson);

        for (String timeStr : command.times()) {
            var schedule = buildSchedule(timeStr, command.frequency(), command.startDate(), command.endDate());
            activity.assign(command.caregiverId(), schedule, today);
        }

        return careActivityRepository.save(activity);
    }

    @Override
    @Transactional
    public CareActivity handle(UpdateMedicationScheduleCommand command) {
        log.info("Updating medication schedule {}", command.medicationId());
        var activity = findMedication(command.medicationId());
        validateAccess(command.userId(), activity.getAssistedPersonProfileId());

        var today = LocalDate.now(clock.withZone(ZONE));
        Map<String, Object> metadata = parseMetadata(activity.getDescription());
        String medicationName = (String) metadata.getOrDefault("medication", activity.getTitle());
        String newTitle = buildTitle(medicationName, command.dose());

        metadata.put("dose", command.dose());
        metadata.put("frequency", command.frequency());
        metadata.put("indicationRef", command.indicationRef());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> revisions = (List<Map<String, Object>>) metadata.get("revisions");
        if (revisions == null) {
            revisions = new ArrayList<>();
        } else {
            revisions = new ArrayList<>(revisions);
        }

        var newRevision = new HashMap<String, Object>();
        newRevision.put("effectiveFrom", command.effectiveFrom().toString());
        newRevision.put("dose", command.dose());
        newRevision.put("times", command.times());
        newRevision.put("frequency", command.frequency());
        newRevision.put("indicationRef", command.indicationRef());
        newRevision.put("recordedAt", java.time.Instant.now(clock).toString());
        newRevision.put("recordedByName", "Responsable");
        revisions.add(newRevision);
        metadata.put("revisions", revisions);

        activity.update(newTitle, toJson(metadata));

        Long caregiverId = activity.getActivityAssignments().getActivityAssignments().isEmpty()
                ? null : activity.getActivityAssignments().getActivityAssignments().get(0).getCaregiverId();

        List<Schedule> newSchedules = new ArrayList<>();
        for (String timeStr : command.times()) {
            newSchedules.add(buildSchedule(timeStr, command.frequency(), command.effectiveFrom(), null));
        }
        activity.replaceAssignments(caregiverId, newSchedules, today);

        return careActivityRepository.save(activity);
    }

    @Override
    @Transactional
    public CareActivity handle(SuspendMedicationScheduleCommand command) {
        log.info("Suspending medication schedule {}", command.medicationId());
        var activity = findMedication(command.medicationId());
        validateAccess(command.userId(), activity.getAssistedPersonProfileId());

        Map<String, Object> metadata = parseMetadata(activity.getDescription());
        var suspension = new HashMap<String, Object>();
        suspension.put("from", command.from().toString());
        suspension.put("source", command.source());
        suspension.put("recordedAt", java.time.Instant.now(clock).toString());
        suspension.put("recordedByName", "Responsable");
        metadata.put("suspension", suspension);

        activity.update(activity.getTitle(), toJson(metadata));
        activity.deactivate();

        return careActivityRepository.save(activity);
    }

    @Override
    @Transactional
    public CareActivity handle(ChangeMedicationResponsibleCommand command) {
        log.info("Changing responsible for medication schedule {} to caregiver {}", command.medicationId(),
                command.caregiverId());
        var activity = findMedication(command.medicationId());
        validateAccess(command.userId(), activity.getAssistedPersonProfileId());

        activity.reassignCaregiver(command.caregiverId());
        return careActivityRepository.save(activity);
    }

    private void validateAccess(UserId userId, PersonProfileId assistedPersonProfileId) {
        boolean hasAccess = careMembershipRepository.findActiveByUserId(userId).stream()
                .anyMatch(m -> m.getAssistedPersonProfileId().equals(assistedPersonProfileId));
        if (!hasAccess) {
            log.warn("Access denied for user {} to assisted person {}", userId.userId(),
                    assistedPersonProfileId.personProfileId());
            throw new AssistedPersonAccessDeniedException(assistedPersonProfileId.personProfileId());
        }
    }

    private CareActivity findMedication(Long id) {
        return careActivityRepository.findById(id)
                .filter(a -> a.getType() == CareActivityType.MEDICATION)
                .orElseThrow(() -> new MedicationScheduleNotFoundException(id));
    }

    private static String buildTitle(String medication, String dose) {
        if (dose != null && !dose.isBlank() && !medication.contains(dose)) {
            return (medication.strip() + " " + dose.strip()).strip();
        }
        return medication.strip();
    }

    private static Schedule buildSchedule(String timeStr, String frequency, LocalDate startDate, LocalDate endDate) {
        String cleanTime = timeStr.trim();
        if (cleanTime.length() > 5) {
            cleanTime = cleanTime.substring(0, 5);
        }
        var start = LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("HH:mm"));
        var end = start.plusMinutes(15);
        var timeRange = new TimeRange(start, end);

        if (endDate != null && startDate.equals(endDate)) {
            return Schedule.oneTime(startDate, timeRange);
        }
        if ("weekly".equalsIgnoreCase(frequency)) {
            return Schedule.weekly(new WeeklyPeriod(Set.of(startDate.getDayOfWeek()), timeRange));
        }
        return Schedule.weekly(new WeeklyPeriod(Set.of(DayOfWeek.values()), timeRange));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize medication metadata", e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String description) {
        if (description != null && description.startsWith("{")) {
            try {
                return objectMapper.readValue(description, Map.class);
            } catch (Exception e) {
                log.warn("Could not parse medication description JSON: {}", description);
            }
        }
        var fallback = new HashMap<String, Object>();
        fallback.put("indicationRef", description != null ? description : "");
        return fallback;
    }
}
