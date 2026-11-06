package com.resolum.alivia.nursing.application.internal.queryservices;

import com.resolum.alivia.nursing.domain.model.aggregates.AssistedPersonCareProfile;
import com.resolum.alivia.nursing.domain.model.queries.GetAssistedPersonCareProfileQuery;
import com.resolum.alivia.nursing.domain.repositories.AssistedPersonCareProfileRepository;
import com.resolum.alivia.nursing.domain.services.AssistedPersonCareProfileQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation of {@link AssistedPersonCareProfileQueryService}.
 */
@Service
public class AssistedPersonCareProfileQueryServiceImpl implements AssistedPersonCareProfileQueryService {

    private final AssistedPersonCareProfileRepository repository;

    /**
     * Creates the service.
     *
     * @param repository the care profile repository
     */
    public AssistedPersonCareProfileQueryServiceImpl(AssistedPersonCareProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AssistedPersonCareProfile> handle(GetAssistedPersonCareProfileQuery query) {
        return repository.findByRegisteredBy(query.requesterUserId());
    }
}
