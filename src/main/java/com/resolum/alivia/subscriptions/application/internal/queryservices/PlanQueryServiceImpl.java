package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.queries.GetAllPlansQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanQueryServiceImpl implements PlanQueryService {

    private final PlanRepository repository;

    public PlanQueryServiceImpl(PlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<Plan> handle(GetAllPlansQuery query) {
        var plans = repository.findByStatusOrderByIdAsc(query.status());
        plans.forEach(this::loadCatalogDetails);
        return plans;
    }

    @Transactional(readOnly = true)
    @Override
    public Plan handle(GetPlanByCodeQuery query) {
        var plan = repository.findByCodeAndStatus(query.code(), PlanStatus.ACTIVE)
                .orElseThrow(() -> new PlanNotFound(query.code()));
        loadCatalogDetails(plan);
        return plan;
    }

    @Transactional(readOnly = true)
    @Override
    public PlanQuote handle(QuotePlanQuery query) {
        var plan = repository.findByCodeAndStatus(query.code(), PlanStatus.ACTIVE)
                .orElseThrow(() -> new PlanNotFound(query.code()));
        return plan.quoteMonthly(query.rooms() == null ? plan.getMinimumRooms() : query.rooms(),
                query.quantities().toSelection());
    }

    private void loadCatalogDetails(Plan plan) {
        plan.getDeviceMonthlyPrices();
        plan.getFeatures();
        plan.getIncludedDevices();
    }
}
