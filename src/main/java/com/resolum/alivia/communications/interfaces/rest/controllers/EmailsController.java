package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.model.queries.GetAllEmailsByStatusQuery;
import com.resolum.alivia.communications.domain.model.valueobjects.EmailStatus;
import com.resolum.alivia.communications.domain.services.EmailQueryService;
import com.resolum.alivia.communications.interfaces.rest.resources.EmailResource;
import com.resolum.alivia.communications.interfaces.rest.transform.EmailResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/emails")
@Tag(name = "Emails", description = "Endpoints for email tracking and monitoring")
public class EmailsController {

    private final EmailQueryService queryService;

    public EmailsController(EmailQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Get emails by delivery status")
    public ResponseEntity<List<EmailResource>> getAll(@RequestParam(defaultValue = "FAILED") String status) {
        EmailStatus emailStatus = EmailStatus.FAILED;
        if (status != null && !status.isBlank()) {
            try {
                emailStatus = EmailStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        var emails = queryService.handle(new GetAllEmailsByStatusQuery(emailStatus));
        var resources = emails.stream().map(EmailResourceAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(resources);
    }
}
