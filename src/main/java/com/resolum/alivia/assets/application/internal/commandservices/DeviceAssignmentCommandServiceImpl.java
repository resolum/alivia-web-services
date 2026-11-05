package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalHouseholdsService;
import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.exceptions.InstallationAccessDenied;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotActive;
import com.resolum.alivia.assets.domain.exceptions.InstallationNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Orchestrates the changes of a household's devices: loads the registry under lock, checks who is acting, and lets the
 * registry apply the rules. The rules themselves live in {@code HouseholdDeviceRegistry}.
 */
@Service
public class DeviceAssignmentCommandServiceImpl implements DeviceAssignmentCommandService {
    private final DeviceRepository deviceRepository;
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final ExternalSubscriptionsService externalSubscriptionsService;
    private final ExternalHouseholdsService externalHouseholdsService;
    private final Clock clock;

    public DeviceAssignmentCommandServiceImpl(DeviceRepository deviceRepository,
                                              HouseholdDeviceRegistryRepository registryRepository,
                                              ExternalSubscriptionsService externalSubscriptionsService,
                                              ExternalHouseholdsService externalHouseholdsService, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.registryRepository = registryRepository;
        this.externalSubscriptionsService = externalSubscriptionsService;
        this.externalHouseholdsService = externalHouseholdsService;
        this.clock = clock;
    }

    /**
     * Order of the checks: installation (404), access (403), installation state (409), device (404), device
     * state (409) and plan (422). Nothing is stored before every rule passed.
     *
     * <p>Assets applies the inventory rules. Who may act is decided by the caller's {@link CallerAuthority}: the
     * administrator of the business and another context that already authorized its caller (Operations, for the work
     * of its technicians) are not asked again; a household member must manage the household. Who a worker is and
     * when the installation work allows them to place devices belong to HCM and Operations.</p>
     */
    @Override
    @Transactional
    public DeviceAssignment handle(AssignDeviceToInstallationCommand command) {
        // The lock is held until the transaction ends: the registry checks the plan's limits against its own
        // entries, and no other change of this household can interleave.
        var registry = registryRepository.findByInstallationIdForUpdate(new InstallationId(command.installationId()))
                .orElseThrow(() -> new InstallationNotFound(command.installationId()));
        if (!mayChangeDevicesOf(registry.getHouseholdId(), command.authority(), command.assignedBy())) {
            throw new InstallationAccessDenied();
        }
        if (!registry.isActive()) {
            throw new InstallationNotActive(command.installationId());
        }

        var device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFound(command.deviceId()));
        device.ensureAssignable();
        var contracted = externalSubscriptionsService.fetchContractedActuators(registry.getHouseholdId())
                .orElseThrow(() -> new DeviceIncompatibleWithPlan("The household has no active subscription."));

        var entry = registry.register(device, command.roomKey(), command.function(), command.assignedBy(),
                LocalDateTime.now(clock), contracted);
        try {
            deviceRepository.save(device);
            registryRepository.saveAndFlush(registry);
            return entry;
        } catch (DataIntegrityViolationException exception) {
            // The same device was placed by a request that does not share this registry's lock
            // (uk_device_assignments_active_device).
            throw new DeviceNotAvailable(device.getId(), AssetStatus.IN_USE);
        }
    }

    @Override
    @Transactional
    public DeviceAssignment handle(UnassignDeviceCommand command) {
        // The registry is locked before the device is read, so a change of the device that was waiting for the lock is
        // already stored when it is read (and the device is written after the registry, as everywhere else).
        var found = registryRepository.findByActiveDeviceIdForUpdate(command.deviceId());
        var device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFound(command.deviceId()));
        if (found.isEmpty()) {
            // Whoever cannot act on any household is not told whether the device is assigned.
            if (command.authority() == CallerAuthority.HOUSEHOLD_MEMBER) {
                throw new InstallationAccessDenied();
            }
            throw new DeviceNotAssigned(command.deviceId());
        }
        var registry = found.get();
        if (!mayChangeDevicesOf(registry.getHouseholdId(), command.authority(), command.unassignedBy())) {
            throw new InstallationAccessDenied();
        }

        // remove validates before changing anything and returns the device to AVAILABLE when it was IN_USE.
        var closed = registry.remove(command.deviceId(), command.reason(), command.unassignedBy(), LocalDateTime.now(clock));
        deviceRepository.save(device);
        registryRepository.saveAndFlush(registry);
        return closed;
    }

    private boolean mayChangeDevicesOf(Long householdId, CallerAuthority authority, Long userId) {
        return switch (authority) {
            case BUSINESS_ADMINISTRATOR, AUTHORIZED_BY_CONTEXT -> true;
            case HOUSEHOLD_MEMBER -> externalHouseholdsService.canManageHousehold(householdId, userId);
        };
    }
}
