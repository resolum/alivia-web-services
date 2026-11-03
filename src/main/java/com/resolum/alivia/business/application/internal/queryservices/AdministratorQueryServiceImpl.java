package com.resolum.alivia.business.application.internal.queryservices;

import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByUserIdQuery;
import com.resolum.alivia.business.domain.repositories.AdministratorRepository;
import com.resolum.alivia.business.domain.services.AdministratorQueryService;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class AdministratorQueryServiceImpl implements AdministratorQueryService {

    private final AdministratorRepository administratorRepository;

    public AdministratorQueryServiceImpl(AdministratorRepository administratorRepository) {
        this.administratorRepository = administratorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Administrator> handle(GetAdministratorByIdQuery query) {
        return administratorRepository.findById(query.administratorId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Administrator> handle(GetAdministratorByUserIdQuery query) {
        return administratorRepository.findByUserId(new UserId(query.userId()));
    }
}
