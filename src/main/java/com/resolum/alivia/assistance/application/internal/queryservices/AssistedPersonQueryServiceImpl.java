package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.queries.GetAllAssistedPersonsQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByUserIdQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AssistedPersonQueryServiceImpl implements AssistedPersonQueryService {

    private final AssistedPersonRepository assistedPersonRepository;
    private final RelativeRepository relativeRepository;
    private final CaregiverRepository caregiverRepository;

    public AssistedPersonQueryServiceImpl(AssistedPersonRepository assistedPersonRepository,
                                         RelativeRepository relativeRepository,
                                         CaregiverRepository caregiverRepository) {
        this.assistedPersonRepository = assistedPersonRepository;
        this.relativeRepository = relativeRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @Override
    public List<AssistedPerson> handle(GetAssistedPersonsByRelativeIdQuery query) {
        return assistedPersonRepository.findAllByRelativeId(query.relativeId());
    }

    @Override
    public Optional<AssistedPerson> handle(GetAssistedPersonByIdQuery query) {
        return assistedPersonRepository.findById(query.assistedPersonId());
    }

    @Override
    public List<AssistedPerson> handle(GetAssistedPersonsByUserIdQuery query) {
        if (query.userId() == null) {
            return List.of();
        }
        var relativeOpt = relativeRepository.findByUserId(query.userId());
        if (relativeOpt.isPresent()) {
            return assistedPersonRepository.findAllByRelativeId(relativeOpt.get().getId());
        }
        var caregivers = caregiverRepository.findAllByUserId(query.userId());
        return caregivers.stream()
                .map(Caregiver::getAssistedPerson)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<AssistedPerson> handle(GetAllAssistedPersonsQuery query) {
        return assistedPersonRepository.findAll();
    }
}
