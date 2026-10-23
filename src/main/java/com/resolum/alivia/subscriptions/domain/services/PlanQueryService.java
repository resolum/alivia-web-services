package com.resolum.alivia.subscriptions.domain.services;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.queries.GetAllPlansQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;

import java.util.List;

public interface PlanQueryService {
    List<Plan> handle(GetAllPlansQuery query);
    Plan handle(GetPlanByCodeQuery query);
    PlanQuote handle(QuotePlanQuery query);
}
