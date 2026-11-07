package com.resolum.alivia.nursing.application.internal.queryservices;

import com.resolum.alivia.nursing.domain.exceptions.AssistedPersonAccessDeniedException;
import com.resolum.alivia.nursing.domain.exceptions.MedicationScheduleNotFoundException;
import com.resolum.alivia.nursing.domain.model.aggregates.CareActivity;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationByIdQuery;
import com.resolum.alivia.nursing.domain.model.queries.GetMedicationsByAssistedPersonQuery;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareActivityType;
import com.resolum.alivia.nursing.domain.repositories.CareActivityRepository;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.domain.services.MedicationScheduleQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service implementing medication schedule queries.
 */
@Slf4j
@Service
public class MedicationScheduleQueryServiceImpl implements MedicationScheduleQueryService {

    private final CareActivityRepository careActivityRepository;
    private final CareMembershipRepository careMembershipRepository;

    public MedicationScheduleQueryServiceImpl(
            CareActivityRepository careActivityRepository,
            CareMembershipRepository careMembershipRepository) {
        this.careActivityRepository = careActivityRepository;
        this.careMembershipRepository = careMembershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareActivity> handle(GetMedicationsByAssistedPersonQuery query) {
        log.debug("Listing medications for assisted person {}", query.assistedPersonProfileId().personProfileId());
        validateAccess(query.userId(), query.assistedPersonProfileId());

        return careActivityRepository.findAllByAssistedPersonProfileIds(List.of(query.assistedPersonProfileId()))
                .stream()
                .filter(activity -> activity.getType() == CareActivityType.MEDICATION)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CareActivity handle(GetMedicationByIdQuery query) {
        log.debug("Getting medication schedule {}", query.medicationId());
        var activity = careActivityRepository.findById(query.medicationId())
                .filter(a -> a.getType() == CareActivityType.MEDICATION)
                .orElseThrow(() -> new MedicationScheduleNotFoundException(query.medicationId()));

        validateAccess(query.userId(), activity.getAssistedPersonProfileId());
        return activity;
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
}
