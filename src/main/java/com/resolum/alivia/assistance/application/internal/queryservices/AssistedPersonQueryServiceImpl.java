package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetAssistedPersonsByRelativeIdQuery;
import com.resolum.alivia.assistance.domain.repositories.AssistedPersonRepository;
import com.resolum.alivia.assistance.domain.services.AssistedPersonQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AssistedPersonQueryServiceImpl implements AssistedPersonQueryService {

    private final AssistedPersonRepository assistedPersonRepository;

    public AssistedPersonQueryServiceImpl(AssistedPersonRepository assistedPersonRepository) {
        this.assistedPersonRepository = assistedPersonRepository;
    }

    @Override
    public List<AssistedPerson> handle(GetAssistedPersonsByRelativeIdQuery query) {
        return assistedPersonRepository.findAllByRelativeId(query.relativeId());
    }

    @Override
    public Optional<AssistedPerson> handle(GetAssistedPersonByIdQuery query) {
        return assistedPersonRepository.findById(query.assistedPersonId());
    }
}
