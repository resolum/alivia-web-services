package com.resolum.alivia.nursing.interfaces.rest.controllers;

import com.resolum.alivia.nursing.domain.model.queries.GetCareAgendaQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetPendingRemindersQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.AgendaItem;
import com.resolum.alivia.nursing.domain.model.valueobjects.OccurrenceStatus;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderPriority;
import com.resolum.alivia.nursing.domain.model.valueobjects.ReminderStatus;
import com.resolum.alivia.nursing.domain.services.CareActivityQueryService;
import com.resolum.alivia.nursing.interfaces.rest.resources.CareAgendaItemResource;
import com.resolum.alivia.nursing.interfaces.rest.transform.CareAgendaItemResourceFromEntityAssembler;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for the care agenda and the reminders derived from it.
 *
 * <p>The user always comes from the authenticated principal, whose name is the IAM user id.</p>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care Agenda", description = "Care agenda and pending reminders of the authenticated caregiver or relative")
public class CareAgendaController {

    private final CareActivityQueryService careActivityQueryService;

    /**
     * Creates the controller.
     *
     * @param careActivityQueryService the care activity query service
     */
    public CareAgendaController(CareActivityQueryService careActivityQueryService) {
        this.careActivityQueryService = careActivityQueryService;
    }

    /**
     * Returns the care agenda of the authenticated user within a date range.
     *
     * @param principal        the authenticated user
     * @param from             the first date, inclusive
     * @param to               the last date, inclusive
     * @param assistedPersonId optional assisted person profile filter
     * @param status           optional occurrence status filter
     * @param priority         optional priority filter
     * @return the agenda occurrences sorted by actual start
     */
    @GetMapping("/care-agenda")
    @Operation(summary = "Get the care agenda for a period",
            description = "Computes the shifts and activities visible to the authenticated user between from and to. "
                    + "Caregivers see the assignments they are responsible for on assisted persons with an active link; "
                    + "relatives see every assignment of their assisted persons. Sorted by actual start.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agenda occurrences",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CareAgendaItemResource.class)))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid dates, from after to, or invalid filter",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No authenticated user",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The user is not linked to the assisted person",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<CareAgendaItemResource> getCareAgenda(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "First date, inclusive (ISO)", example = "2026-10-26", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last date, inclusive (ISO)", example = "2026-11-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Assisted person profile identifier", example = "8")
            @RequestParam(required = false) Long assistedPersonId,
            @Parameter(description = "Occurrence status")
            @RequestParam(required = false) OccurrenceStatus status,
            @Parameter(description = "Priority derived from the activity type")
            @RequestParam(required = false) ReminderPriority priority) {
        log.info("Received care agenda request from {} to {}", from, to);
        var query = new GetCareAgendaQuery(userIdOf(principal), from, to, personProfileIdOf(assistedPersonId),
                status, priority);
        return toResources(careActivityQueryService.handle(query));
    }

    /**
     * Returns the pending reminders of the authenticated user.
     *
     * @param principal        the authenticated user
     * @param status           the reminder status; only {@code PENDING} is accepted
     * @param assistedPersonId optional assisted person profile filter
     * @param priority         optional priority filter
     * @return the pending occurrences sorted by actual start and then by priority
     */
    @GetMapping("/reminders")
    @Operation(summary = "Get pending reminders",
            description = "Derives reminders from the care agenda: scheduled or in progress occurrences from yesterday "
                    + "until the configured horizon, in America/Lima time. Sorted by actual start, then by priority "
                    + "(HIGH, MEDIUM, LOW, ROUTINE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending reminders",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CareAgendaItemResource.class)))),
            @ApiResponse(responseCode = "400", description = "Status other than PENDING or invalid filter",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No authenticated user",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "The user is not linked to the assisted person",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<CareAgendaItemResource> getReminders(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "Reminder status; only PENDING is supported", example = "PENDING")
            @RequestParam(defaultValue = "PENDING") ReminderStatus status,
            @Parameter(description = "Assisted person profile identifier", example = "8")
            @RequestParam(required = false) Long assistedPersonId,
            @Parameter(description = "Priority derived from the activity type")
            @RequestParam(required = false) ReminderPriority priority) {
        log.info("Received pending reminders request");
        var query = new GetPendingRemindersQuery(userIdOf(principal), personProfileIdOf(assistedPersonId), priority);
        return toResources(careActivityQueryService.handle(query));
    }

    private static UserId userIdOf(Principal principal) {
        return new UserId(Long.valueOf(principal.getName()));
    }

    private static PersonProfileId personProfileIdOf(Long assistedPersonId) {
        return assistedPersonId == null ? null : new PersonProfileId(assistedPersonId);
    }

    private static List<CareAgendaItemResource> toResources(List<AgendaItem> items) {
        return items.stream().map(CareAgendaItemResourceFromEntityAssembler::toResourceFromEntity).toList();
    }
}
