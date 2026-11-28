package com.resolum.alivia.analytics.domain.services;

import com.resolum.alivia.analytics.domain.model.queries.GetAutonomyMetricsQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetBusinessOverviewQuery;
import com.resolum.alivia.analytics.domain.model.queries.GetSalesMetricsQuery;
import com.resolum.alivia.analytics.domain.model.valueobjects.AutonomyMetrics;
import com.resolum.alivia.analytics.domain.model.valueobjects.BusinessOverview;
import com.resolum.alivia.analytics.domain.model.valueobjects.SalesMetrics;

public interface AnalyticsQueryService {

    SalesMetrics handle(GetSalesMetricsQuery query);

    BusinessOverview handle(GetBusinessOverviewQuery query);

    /** @throws org.springframework.security.access.AccessDeniedException when the caller may not read the person */
    AutonomyMetrics handle(GetAutonomyMetricsQuery query);
}
