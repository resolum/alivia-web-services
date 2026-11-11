package com.resolum.alivia.assistance.application.internal.queryservices;

import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativeByUserIdQuery;
import com.resolum.alivia.assistance.domain.model.queries.GetRelativesByBusinessIdQuery;
import com.resolum.alivia.assistance.domain.repositories.RelativeRepository;
import com.resolum.alivia.assistance.domain.services.RelativeQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RelativeQueryServiceImpl implements RelativeQueryService {

    private final RelativeRepository relativeRepository;

    public RelativeQueryServiceImpl(RelativeRepository relativeRepository) {
        this.relativeRepository = relativeRepository;
    }

    @Override
    public Optional<Relative> handle(GetRelativeByIdQuery query) {
        return relativeRepository.findById(query.relativeId());
    }

    @Override
    public Optional<Relative> handle(GetRelativeByUserIdQuery query) {
        return relativeRepository.findByUserId(new UserId(query.userId()));
    }

    @Override
    public List<Relative> handle(GetRelativesByBusinessIdQuery query) {
        return relativeRepository.findAllByBusinessId(new BusinessId(query.businessId()));
    }
}
