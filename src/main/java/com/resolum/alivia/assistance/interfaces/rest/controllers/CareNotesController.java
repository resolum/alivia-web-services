package com.resolum.alivia.assistance.interfaces.rest.controllers;

import com.resolum.alivia.assistance.domain.model.commands.CreateCareNoteCommand;
import com.resolum.alivia.assistance.domain.model.queries.GetCareNotesQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCareSummaryQuery;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareSummary;
import com.resolum.alivia.assistance.domain.services.CareNoteCommandService;
import com.resolum.alivia.assistance.domain.services.CareSummaryQueryService;
import com.resolum.alivia.assistance.interfaces.rest.CallerIdentity;
import com.resolum.alivia.assistance.interfaces.rest.resources.CareNoteResource;
import com.resolum.alivia.assistance.interfaces.rest.resources.CreateCareNoteResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping(value = "/api/v1/assisted-persons/{assistedPersonId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Care continuity", description = "Care notes and the continuity summary of an assisted person")
public class CareNotesController {

    private final CareNoteCommandService commandService;
    private final CareSummaryQueryService queryService;

    public CareNotesController(CareNoteCommandService commandService, CareSummaryQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/care-notes")
    @Operation(summary = "Register a care note",
            description = "The author is the caller; only the relative in charge or an active caregiver may write.")
    public ResponseEntity<CareNoteResource> createNote(@PathVariable Long assistedPersonId,
                                                       @RequestBody CreateCareNoteResource resource,
                                                       Principal principal) {
        var userId = CallerIdentity.requireUserId(principal);
        var note = commandService.handle(new CreateCareNoteCommand(assistedPersonId, userId,
                parseCategory(resource == null ? null : resource.category()),
                resource == null ? null : resource.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CareNoteResource.from(note));
    }

    @GetMapping("/care-notes")
    @Operation(summary = "Care notes of an assisted person, newest first")
    public ResponseEntity<List<CareNoteResource>> notes(@PathVariable Long assistedPersonId, Principal principal) {
        var userId = CallerIdentity.requireUserId(principal);
        return ResponseEntity.ok(queryService.handle(new GetCareNotesQuery(assistedPersonId, userId)).stream()
                .map(CareNoteResource::from).toList());
    }

    @GetMapping("/care-summary")
    @Operation(summary = "Chronological continuity summary",
            description = "Activities, medications, alerts and notes from 'from' to 'to' (days, both included; "
                    + "'to' defaults to today, at most 31 days), with how many were attended, are pending or were "
                    + "missed. incompleteSources names a source that could not be read.")
    public ResponseEntity<CareSummary> summary(
            @PathVariable Long assistedPersonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {
        var userId = CallerIdentity.requireUserId(principal);
        return ResponseEntity.ok(queryService.handle(new GetCareSummaryQuery(assistedPersonId, userId, from, to)));
    }

    private static CareNoteCategory parseCategory(String value) {
        if (value == null || value.isBlank()) return CareNoteCategory.GENERAL;
        try {
            return CareNoteCategory.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown note category: " + value);
        }
    }
}
