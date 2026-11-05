package com.resolum.alivia.assets.application.internal.queryservices;

import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

    /**
     * The registry row (with its version) and its entries are two reads. At the default isolation a change could commit
     * between them and the table would carry the entries of a newer version under an older number; a single snapshot
     * rules that out.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<EdgeAccessTable> handle(GetEdgeAccessTableByEdgeIdQuery query) {
        return registryRepository.findByAssignedEdgeId(query.edgeId())
                .filter(HouseholdDeviceRegistry::isActive)
                .map(HouseholdDeviceRegistry::table);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeTableVersion> handle(GetEdgeTableVersionByEdgeIdQuery query) {
        return registryRepository.findActiveTableVersionByAssignedEdgeId(query.edgeId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> handle(GetTableVersionByInstallationIdQuery query) {
        return registryRepository.findActiveTableVersionByInstallationId(query.installationId());
    }
}
