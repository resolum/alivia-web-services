package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.Handover;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoverByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetHandoversQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.HandoverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.HandoverQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class HandoverQueryServiceImpl implements HandoverQueryService {

    private final HandoverRepository handoverRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public HandoverQueryServiceImpl(HandoverRepository handoverRepository,
            AssistedPersonRepository assistedPersonRepository,
            RelativeRepository relativeRepository,
            CaregiverRepository caregiverRepository) {
        this.handoverRepository = handoverRepository;
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    private Long resolveAssistedPersonId(Long assistedPersonId, UserId userId) {
        if (assistedPersonId != null)
            return assistedPersonId;
        if (userId != null) {
            var relativeOpt = relativeRepository.findByUserId(userId);
            if (relativeOpt.isPresent()) {
                var persons = assistedPersonRepository.findAllByRelativeId(relativeOpt.get().getId());
                if (!persons.isEmpty())
                    return persons.getFirst().getId();
            } else {
                var caregivers = caregiverRepository.findAllByUserId(userId);
                if (!caregivers.isEmpty() && caregivers.getFirst().getAssistedPerson() != null) {
                    return caregivers.getFirst().getAssistedPerson().getId();
                }
            }
        }
        var persons = assistedPersonRepository.findAll();
        if (!persons.isEmpty())
            return persons.getFirst().getId();
        return 1L;
    }

    @Override
    public List<Handover> handle(GetHandoversQuery query) {
        Long assistedPersonId = resolveAssistedPersonId(query.assistedPersonId(), query.userId());
        return handoverRepository.findByAssistedPersonId(assistedPersonId);
    }

    @Override
    public Optional<Handover> handle(GetHandoverByIdQuery query) {
        return handoverRepository.findById(query.handoverId());
    }
}
