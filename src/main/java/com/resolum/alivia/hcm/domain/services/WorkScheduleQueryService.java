package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkScheduleQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetWorkSchedulesQuery;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface WorkScheduleQueryService {
    Page<WorkSchedule> handle(GetWorkSchedulesQuery query);
    WorkSchedule handle(GetWorkScheduleQuery query);
    Optional<Contract> associatedContract(WorkSchedule workSchedule);
}
