package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByUserIdQuery;
import com.resolum.alivia.assistance.domain.repositories.CaregiverRepository;
import com.resolum.alivia.assistance.domain.services.CaregiverQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CaregiverQueryServiceImpl implements CaregiverQueryService {

    private final CaregiverRepository caregiverRepository;

    public CaregiverQueryServiceImpl(CaregiverRepository caregiverRepository) {
        this.caregiverRepository = caregiverRepository;
    }

    @Override
    public List<Caregiver> handle(GetCaregiversByAssistedPersonIdQuery query) {
        return caregiverRepository.findAllByAssistedPersonId(query.assistedPersonId());
    }

    @Override
    public List<Caregiver> handle(GetCaregiversByUserIdQuery query) {
        return caregiverRepository.findAllByUserId(new UserId(query.userId()));
    }
}
