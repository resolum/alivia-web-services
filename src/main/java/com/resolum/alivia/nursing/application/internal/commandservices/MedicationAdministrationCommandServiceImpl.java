package com.resolum.alivia.nursing.application.internal.commandservices;

import com.resolum.alivia.nursing.domain.exceptions.MedicationOutcomeAlreadyRecordedException;
import com.resolum.alivia.nursing.domain.model.aggregates.ActivityLog;
import com.resolum.alivia.nursing.domain.model.commands.RecordMedicationOutcomeCommand;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationAdministrationQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.MedicationAdministration;
import com.resolum.alivia.nursing.domain.repositories.ActivityLogRepository;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationCommandService;
import com.resolum.alivia.nursing.domain.services.MedicationAdministrationQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Records medication outcomes in the activity log.
 *
 * <p>Checks run in this order: future moment (400), existence (404), access (403), existing
 * outcome (409) and moment before the start (400). A concurrent duplicate is rejected by the
 * unique constraint on assignment and date and also answered as 409.</p>
 */
@Slf4j
@Service
public class MedicationAdministrationCommandServiceImpl implements MedicationAdministrationCommandService {

    private static final ZoneId AGENDA_ZONE = ZoneId.of("America/Lima");

    private final MedicationAdministrationQueryService medicationAdministrationQueryService;
    private final ActivityLogRepository activityLogRepository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param medicationAdministrationQueryService the query service that resolves the administration and access
     * @param activityLogRepository                the activity log repository
     * @param clock                                the shared clock
     */
    public MedicationAdministrationCommandServiceImpl(
            MedicationAdministrationQueryService medicationAdministrationQueryService,
            ActivityLogRepository activityLogRepository,
            Clock clock) {
        this.medicationAdministrationQueryService = medicationAdministrationQueryService;
        this.activityLogRepository = activityLogRepository;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MedicationAdministration handle(RecordMedicationOutcomeCommand command) {
        log.debug("Handling record medication outcome command");
        var now = LocalDateTime.now(clock.withZone(AGENDA_ZONE));
        // The aggregate enforces this too; checked here so an invalid body answers 400 before 404.
        if (command.occurredAt().isAfter(now)) {
            log.warn("Medication outcome rejected: occurredAt is in the future");
            throw new IllegalArgumentException("The occurredAt moment cannot be in the future.");
        }
        var id = command.administrationId();
        var administration = medicationAdministrationQueryService.handle(
                new GetMedicationAdministrationQuery(command.userId(), id));
        if (administration.outcome() != null) {
            log.warn("Medication outcome rejected: administration {} already has an outcome", id);
            throw new MedicationOutcomeAlreadyRecordedException(id);
        }
        // Access is already granted, so anyone else than the assigned caregiver is a relative.
        var completedBy = command.userId().equals(administration.caregiverUserId()) ? administration.caregiverId() : null;
        var activityLog = ActivityLog.medicationOutcome(administration, command, completedBy, now);
        try {
            var saved = activityLogRepository.save(activityLog);
            log.info("Recorded medication outcome for administration {} by user id {}", id, command.userId().userId());
            return administration.withOutcome(saved);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Medication outcome rejected: concurrent outcome already recorded for administration {}", id);
            throw new MedicationOutcomeAlreadyRecordedException(id);
        }
    }
}
