package com.resolum.alivia.subscriptions.application.internal.queryservices;

import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomeByIdQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetServicedHomesQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ServicedHome;
import com.resolum.alivia.subscriptions.domain.services.HiringRequestQueryService;
import com.resolum.alivia.subscriptions.domain.services.ServicedHomeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Homes in service: the installed homes of Operations with the contracting request the family pays. */
@Service
public class ServicedHomeQueryServiceImpl implements ServicedHomeQueryService {
    private final ExternalOperationsService operations;
    private final HiringRequestQueryService requests;

    public ServicedHomeQueryServiceImpl(ExternalOperationsService operations, HiringRequestQueryService requests) {
        this.operations = operations;
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServicedHome> handle(GetServicedHomesQuery query) {
        var homes = operations.fetchInstalledHomes(query.technicianUserId());
        if (homes.isEmpty()) return List.of();
        Map<String, HiringRequest> byDraft = requests.handle(new GetHiringRequestsQuery(null)).stream()
                .collect(Collectors.toMap(HiringRequest::draftId, Function.identity(), (first, second) -> first));
        return homes.stream().map(home -> home.withRequest(byDraft.get(home.draftId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServicedHome> handle(GetServicedHomeByIdQuery query) {
        return handle(new GetServicedHomesQuery(query.technicianUserId())).stream()
                .filter(home -> home.draftId().equals(query.draftId()))
                .findFirst();
    }
}
