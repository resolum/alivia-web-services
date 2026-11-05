package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalBusinessService;
import com.resolum.alivia.assets.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAlreadyRegistered;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.RegisterInstallationCommand;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class HouseholdDeviceRegistryCommandServiceImpl implements HouseholdDeviceRegistryCommandService {
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final ExternalBusinessService externalBusinessService;

    public HouseholdDeviceRegistryCommandServiceImpl(HouseholdDeviceRegistryRepository registryRepository,
                                                     ExternalBusinessService externalBusinessService) {
        this.registryRepository = registryRepository;
        this.externalBusinessService = externalBusinessService;
    }

    @Override
    public Long handle(RegisterInstallationCommand command) {
        var registry = HouseholdDeviceRegistry.open(command.installationId(), command.householdId(), command.businessId());
        if (!externalBusinessService.existsBusiness(command.businessId())) {
            throw new BusinessNotFound(String.format("business with id %d not found", command.businessId()));
        }
        if (registryRepository.existsByInstallationId(new InstallationId(command.installationId()))) {
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
        if (registryRepository.existsByHouseholdId(command.householdId())) {
            throw InstallationAlreadyRegistered.activeInHousehold(command.householdId());
        }
        try {
            return registryRepository.saveAndFlush(registry).getId();
        } catch (DataIntegrityViolationException exception) {
            // Another request registered the installation or the household first
            // (uk_household_device_registries_installation, uk_household_device_registries_household).
            throw InstallationAlreadyRegistered.installation(command.installationId());
        }
    }
}
