package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.CareShift;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetShiftsQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CareShiftRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.CareShiftQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CareShiftQueryServiceImpl implements CareShiftQueryService {

    private final CareShiftRepository careShiftRepository;
    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public CareShiftQueryServiceImpl(CareShiftRepository careShiftRepository,
            AssistedPersonRepository assistedPersonRepository,
            RelativeRepository relativeRepository,
            CaregiverRepository caregiverRepository) {
        this.careShiftRepository = careShiftRepository;
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
    public List<CareShift> handle(GetShiftsQuery query) {
        Long assistedPersonId = resolveAssistedPersonId(query.assistedPersonId(), query.userId());
        if (query.from() != null && query.to() != null) {
            return careShiftRepository.findByAssistedPersonIdAndRange(assistedPersonId, query.from(), query.to());
        }
        return careShiftRepository.findByAssistedPersonId(assistedPersonId);
    }

    @Override
    public Optional<CareShift> handle(GetShiftByIdQuery query) {
        return careShiftRepository.findById(query.shiftId());
    }
}
