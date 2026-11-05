package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class HouseholdDeviceRegistryQueryServiceImpl implements HouseholdDeviceRegistryQueryService {
    private final HouseholdDeviceRegistryRepository registryRepository;

    public HouseholdDeviceRegistryQueryServiceImpl(HouseholdDeviceRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeAccessTable> handle(GetEdgeAccessTableByInstallationIdQuery query) {
        return registryRepository.findByInstallationId(new InstallationId(query.installationId()))
                .map(HouseholdDeviceRegistry::table);
    }
}
