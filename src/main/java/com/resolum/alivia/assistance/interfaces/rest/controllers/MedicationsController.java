package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalIamService;
import com.resolum.alivia.assistance.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.CareActivity;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.commands.AssignCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.CreateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.DeactivateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateActivityAssignmentCommand;
import com.resolum.alivia.assistance.domain.model.commands.UpdateCareActivityCommand;
import com.resolum.alivia.assistance.domain.model.entities.ActivityAssignment;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivitiesByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareActivityByIdQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.ActivityStatus;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.assistance.domain.model.valueobjects.ScheduleType;
import com.resolum.alivia.assistance.domain.services.AssistanceAccessService;
import com.resolum.alivia.assistance.domain.services.CareActivityCommandService;
import com.resolum.alivia.assistance.domain.services.CareActivityQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.AssignMedicationResponsibleRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.MedicationScheduleResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.MedicationScheduleResource.MedicationRevisionResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.MedicationScheduleResource.MedicationSuspensionResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.ScheduleMedicationRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.SuspendMedicationRequestResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.UpdateMedicationRequestResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Medications", description = "Endpoints for managing medication schedules")
public class MedicationsController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final CareActivityCommandService commandService;
    private final CareActivityQueryService queryService;
    private final AssistanceAccessService accessService;
    private final ExternalIamService externalIamService;
    private final ExternalProfilesService externalProfilesService;

    public MedicationsController(CareActivityCommandService commandService,
                                 CareActivityQueryService queryService,
                                 AssistanceAccessService accessService) {
        this(commandService, queryService, accessService, null, null);
    }

    @Autowired
    public MedicationsController(CareActivityCommandService commandService,
                                 CareActivityQueryService queryService,
                                 AssistanceAccessService accessService,
                                 ExternalIamService externalIamService,
                                 ExternalProfilesService externalProfilesService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
        this.externalIamService = externalIamService;
        this.externalProfilesService = externalProfilesService;
    }

    @GetMapping("/medications")
    public ResponseEntity<List<MedicationScheduleResource>> listMedications(Principal principal) {
        List<AssistedPerson> assistedPersons = accessService.readableBy(CallerIdentity.requireUserId(principal));
        List<CareActivity> medications = assistedPersons.stream()
                .flatMap(person -> queryService.handle(new GetCareActivitiesByAssistedPersonIdQuery(person.getId())).stream())
                .filter(activity -> activity.getType() == CareActivityType.MEDICATION)
                .toList();

        Map<UserId, String> profileNames = resolveProfileNames(medications);
        List<MedicationScheduleResource> resources = medications.stream()
                .map(activity -> toResource(activity, profileNames))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping("/assisted-persons/{assistedPersonId}/medications")
    public ResponseEntity<MedicationScheduleResource> scheduleMedication(
            @PathVariable Long assistedPersonId,
            Principal principal,
            @RequestBody ScheduleMedicationRequestResource resource) {
        Long userId = CallerIdentity.requireUserId(principal);
        // The body can name another person than the path: both must be reachable by the caller.
        accessService.requireOperate(assistedPersonId, userId);
        Long targetAssistedPersonId = resource.assistedPersonId() != null ? resource.assistedPersonId() : assistedPersonId;
        var person = accessService.requireOperate(targetAssistedPersonId, userId);
        Long relativeId = person.getRelative().getId();

        Long activityId = commandService.handle(new CreateCareActivityCommand(
                relativeId,
                targetAssistedPersonId,
                CareActivityType.MEDICATION,
                resource.medication(),
                resource.dose()
        ));

        LocalDate startDate = parseDateOrToday(resource.startDate());
        Set<DayOfWeek> days = mapFrequencyToDays(resource.frequency(), startDate);
        List<String> times = normalizeTimes(resource.times());

        for (String timeStr : times) {
            LocalTime startTime = parseStartTime(timeStr);
            LocalTime endTime = computeEndTime(startTime);
            commandService.handle(new AssignCareActivityCommand(
                    activityId,
                    relativeId,
                    resource.caregiverId(),
                    ScheduleType.WEEKLY,
                    days,
                    null,
                    startTime,
                    endTime
            ));
        }

        return queryService.handle(new GetCareActivityByIdQuery(activityId))
                .map(activity -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(toResource(activity, resolveProfileNames(List.of(activity)))))
                .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    @PutMapping("/medications/{scheduleId}")
    public ResponseEntity<MedicationScheduleResource> updateMedication(
            @PathVariable Long scheduleId,
            Principal principal,
            @RequestBody UpdateMedicationRequestResource resource) {
        CareActivity existing = accessService.requireOperateActivity(scheduleId, CallerIdentity.requireUserId(principal));
        Long relativeId = existing.getAssistedPerson().getRelative().getId();

        commandService.handle(new UpdateCareActivityCommand(
                scheduleId,
                relativeId,
                CareActivityType.MEDICATION,
                existing.getTitle(),
                resource.dose()
        ));

        List<ActivityAssignment> activeAssignments = existing.getActivityAssignments().getActivityAssignments().stream()
                .filter(a -> a.getStatus() == ActivityStatus.ACTIVE)
                .toList();
        Long caregiverId = activeAssignments.stream()
                .map(ActivityAssignment::getCaregiver)
                .filter(Objects::nonNull)
                .map(Caregiver::getId)
                .findFirst()
                .orElse(null);

        LocalDate effectiveDate = parseDateOrToday(resource.effectiveFrom());
        Set<DayOfWeek> days = mapFrequencyToDays(resource.frequency(), effectiveDate);
        List<String> times = normalizeTimes(resource.times());

        int sharedCount = Math.min(activeAssignments.size(), times.size());
        for (int i = 0; i < sharedCount; i++) {
            ActivityAssignment assignment = activeAssignments.get(i);
            LocalTime startTime = parseStartTime(times.get(i));
            LocalTime endTime = computeEndTime(startTime);
            commandService.handle(new UpdateActivityAssignmentCommand(
                    scheduleId,
                    assignment.getId(),
                    relativeId,
                    caregiverId,
                    ScheduleType.WEEKLY,
                    days,
                    null,
                    startTime,
                    endTime
            ));
        }
        for (int i = sharedCount; i < activeAssignments.size(); i++) {
            commandService.handle(new DeactivateActivityAssignmentCommand(
                    scheduleId,
                    activeAssignments.get(i).getId(),
                    relativeId
            ));
        }
        for (int i = sharedCount; i < times.size(); i++) {
            LocalTime startTime = parseStartTime(times.get(i));
            LocalTime endTime = computeEndTime(startTime);
            commandService.handle(new AssignCareActivityCommand(
                    scheduleId,
                    relativeId,
                    caregiverId,
                    ScheduleType.WEEKLY,
                    days,
                    null,
                    startTime,
                    endTime
            ));
        }

        return queryService.handle(new GetCareActivityByIdQuery(scheduleId))
                .map(activity -> ResponseEntity.ok(toResource(activity, resolveProfileNames(List.of(activity)))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/medications/{scheduleId}/suspend")
    public ResponseEntity<MedicationScheduleResource> suspendMedication(
            @PathVariable Long scheduleId,
            Principal principal,
            @RequestBody(required = false) SuspendMedicationRequestResource resource) {
        var target = accessService.requireOperateActivity(scheduleId, CallerIdentity.requireUserId(principal));
        commandService.handle(new DeactivateCareActivityCommand(scheduleId, target.getAssistedPerson().getRelative().getId()));

        return queryService.handle(new GetCareActivityByIdQuery(scheduleId))
                .map(activity -> ResponseEntity.ok(toResource(activity, resolveProfileNames(List.of(activity)))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/medications/{scheduleId}/responsible")
    public ResponseEntity<MedicationScheduleResource> assignMedicationResponsible(
            @PathVariable Long scheduleId,
            Principal principal,
            @RequestBody AssignMedicationResponsibleRequestResource resource) {
        CareActivity existing = accessService.requireOperateActivity(scheduleId, CallerIdentity.requireUserId(principal));
        Long relativeId = existing.getAssistedPerson().getRelative().getId();

        List<ActivityAssignment> activeAssignments = existing.getActivityAssignments().getActivityAssignments().stream()
                .filter(a -> a.getStatus() == ActivityStatus.ACTIVE)
                .toList();

        if (activeAssignments.isEmpty()) {
            commandService.handle(new AssignCareActivityCommand(
                    scheduleId,
                    relativeId,
                    resource.caregiverId(),
                    ScheduleType.WEEKLY,
                    EnumSet.allOf(DayOfWeek.class),
                    null,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 30)
            ));
        } else {
            for (ActivityAssignment assignment : activeAssignments) {
                commandService.handle(new UpdateActivityAssignmentCommand(
                        scheduleId,
                        assignment.getId(),
                        relativeId,
                        resource.caregiverId(),
                        assignment.getScheduleType(),
                        parseAssignmentDays(assignment),
                        assignment.getScheduledDate(),
                        assignment.getStartTime(),
                        assignment.getEndTime()
                ));
            }
        }

        return queryService.handle(new GetCareActivityByIdQuery(scheduleId))
                .map(activity -> ResponseEntity.ok(toResource(activity, resolveProfileNames(List.of(activity)))))
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<UserId, String> resolveProfileNames(List<CareActivity> activities) {
        if (externalIamService == null || externalProfilesService == null) {
            return Map.of();
        }
        Set<UserId> userIds = activities.stream()
                .flatMap(activity -> activity.getActivityAssignments().getActivityAssignments().stream())
                .map(ActivityAssignment::getCaregiver)
                .filter(Objects::nonNull)
                .map(Caregiver::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UserId, PersonProfileId> profileIdsByUser = externalIamService.fetchPersonProfileIds(userIds);
        if (profileIdsByUser.isEmpty()) {
            return Map.of();
        }
        Map<PersonProfileId, String> namesByProfile = externalProfilesService.fetchPersonNames(
                new HashSet<>(profileIdsByUser.values()));
        Map<UserId, String> namesByUser = new HashMap<>();
        profileIdsByUser.forEach((userId, profileId) -> {
            String name = namesByProfile.get(profileId);
            if (name != null) {
                namesByUser.put(userId, name);
            }
        });
        return namesByUser;
    }

    private MedicationScheduleResource toResource(CareActivity activity, Map<UserId, String> caregiverNames) {
        List<ActivityAssignment> allAssignments = activity.getActivityAssignments().getActivityAssignments();
        List<ActivityAssignment> activeAssignments = allAssignments.stream()
                .filter(a -> a.getStatus() == ActivityStatus.ACTIVE)
                .toList();
        List<ActivityAssignment> sourceAssignments = activeAssignments.isEmpty() ? allAssignments : activeAssignments;

        List<String> times = sourceAssignments.stream()
                .map(ActivityAssignment::getStartTime)
                .filter(Objects::nonNull)
                .map(time -> time.format(TIME_FORMATTER))
                .distinct()
                .sorted()
                .toList();
        if (times.isEmpty()) {
            times = List.of("08:00");
        }

        Caregiver caregiver = sourceAssignments.stream()
                .map(ActivityAssignment::getCaregiver)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        String caregiverId = caregiver != null && caregiver.getId() != null
                ? String.valueOf(caregiver.getId())
                : "";
        String caregiverName = "";
        if (caregiver != null) {
            caregiverName = caregiver.getUserId() != null
                    ? caregiverNames.getOrDefault(caregiver.getUserId(), "Cuidador #" + caregiver.getId())
                    : "Cuidador #" + caregiver.getId();
        }

        String frequency = inferFrequency(sourceAssignments);
        LocalDateTime createdAt = activity.getCreatedAt() != null ? activity.getCreatedAt() : LocalDateTime.now();
        LocalDateTime updatedAt = activity.getUpdatedAt() != null ? activity.getUpdatedAt() : createdAt;
        String startDate = createdAt.toLocalDate().toString();
        String recordedAtIso = updatedAt.atOffset(ZoneOffset.UTC).toString();
        String dose = activity.getDescription() != null ? activity.getDescription() : "";

        MedicationRevisionResource revision = new MedicationRevisionResource(
                startDate,
                dose,
                times,
                frequency,
                "",
                recordedAtIso,
                "Familiar"
        );

        MedicationSuspensionResource suspension = activity.getStatus() == ActivityStatus.INACTIVE
                ? new MedicationSuspensionResource(
                        updatedAt.toLocalDate().toString(),
                        "manual",
                        recordedAtIso,
                        "Familiar"
                )
                : null;

        String assistedPersonId = activity.getAssistedPerson() != null && activity.getAssistedPerson().getId() != null
                ? String.valueOf(activity.getAssistedPerson().getId())
                : "";

        return new MedicationScheduleResource(
                String.valueOf(activity.getId()),
                activity.getTitle(),
                assistedPersonId,
                caregiverId,
                caregiverName,
                startDate,
                null,
                List.of(revision),
                suspension
        );
    }

    private String inferFrequency(List<ActivityAssignment> assignments) {
        if (assignments.isEmpty()) {
            return "daily";
        }
        Set<DayOfWeek> days = parseAssignmentDays(assignments.get(0));
        if (days.size() == 1) {
            return "weekly";
        }
        if (days.size() >= 2 && days.size() <= 4) {
            return "every_other_day";
        }
        return "daily";
    }

    private Set<DayOfWeek> parseAssignmentDays(ActivityAssignment assignment) {
        if (assignment.getScheduleType() != ScheduleType.WEEKLY || assignment.getWeekDays() == null || assignment.getWeekDays().isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(assignment.getWeekDays().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private Set<DayOfWeek> mapFrequencyToDays(String frequency, LocalDate referenceDate) {
        if ("weekly".equalsIgnoreCase(frequency)) {
            return EnumSet.of(referenceDate.getDayOfWeek());
        }
        if ("every_other_day".equalsIgnoreCase(frequency)) {
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
        }
        return EnumSet.allOf(DayOfWeek.class);
    }

    private List<String> normalizeTimes(List<String> times) {
        if (times == null || times.isEmpty()) {
            return List.of("08:00");
        }
        List<String> cleaned = times.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return cleaned.isEmpty() ? List.of("08:00") : cleaned;
    }

    private LocalDate parseDateOrToday(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private LocalTime parseStartTime(String timeStr) {
        try {
            LocalTime parsed = LocalTime.parse(timeStr.trim());
            return parsed.equals(LocalTime.of(23, 59)) ? LocalTime.of(23, 58) : parsed;
        } catch (Exception ignored) {
            return LocalTime.of(8, 0);
        }
    }

    private LocalTime computeEndTime(LocalTime startTime) {
        return startTime.isBefore(LocalTime.of(23, 30))
                ? startTime.plusMinutes(30)
                : LocalTime.of(23, 59);
    }
}
