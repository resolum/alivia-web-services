package com.resolum.alivia.assistance.domain.services;

import com.resolum.alivia.assistance.domain.model.aggregates.Caregiver;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByAssistedPersonIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetCaregiversByUserIdQuery;

import java.util.List;

public interface CaregiverQueryService {
    List<Caregiver> handle(GetCaregiversByAssistedPersonIdQuery query);
    List<Caregiver> handle(GetCaregiversByUserIdQuery query);
}
