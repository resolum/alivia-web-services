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
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DeviceAssignmentCommandServiceImpl implements DeviceAssignmentCommandService {
    private final DeviceRepository deviceRepository;
    private final DeviceNetworkRepository networkRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final ExternalSubscriptionsService externalSubscriptionsService;
    private final ExternalHouseholdsService externalHouseholdsService;
    private final Clock clock;

    public DeviceAssignmentCommandServiceImpl(DeviceRepository deviceRepository,
                                              DeviceNetworkRepository networkRepository,
                                              DeviceAssignmentRepository assignmentRepository,
                                              ExternalSubscriptionsService externalSubscriptionsService,
                                              ExternalHouseholdsService externalHouseholdsService, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.networkRepository = networkRepository;
        this.assignmentRepository = assignmentRepository;
        this.externalSubscriptionsService = externalSubscriptionsService;
        this.externalHouseholdsService = externalHouseholdsService;
        this.clock = clock;
    }

    /**
     * Order of the checks: installation (404), access (403), installation state (409), device (404), device
     * state (409) and plan (422). Nothing is stored before every rule passed.
     */
    @Override
    @Transactional
    public DeviceAssignment handle(AssignDeviceToInstallationCommand command) {
        // The lock is held until the transaction ends: the quantity check below and the insert cannot interleave
        // with another assignment to the same household.
        var network = networkRepository.findByInstallationIdForUpdate(new InstallationId(command.installationId()))
                .filter(found -> found.getHouseholdId() != null)
                .orElseThrow(() -> new InstallationNotFound(command.installationId()));
        var householdId = network.getHouseholdId();
        if (!command.assignedByBusinessAdministrator()
                && !externalHouseholdsService.canManageHousehold(householdId, command.assignedBy())) {
            throw new InstallationAccessDenied();
        }
        if (!network.isActive()) {
            throw new InstallationNotActive(command.installationId());
        }

        var device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFound(command.deviceId()));
        device.ensureAssignable();
        ensureCompatibleWithPlan(device, network, command);

        // assignTo fails before changing the device when the assignment data is invalid.
        var assignment = device.assignTo(command.installationId(), householdId, command.roomKey(), command.function(),
                command.assignedBy(), LocalDateTime.now(clock));
        try {
            deviceRepository.save(device);
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            // The same device was assigned by a request that does not share the household lock
            // (uk_device_assignments_active_device).
            throw new DeviceNotAvailable(device.getId(), AssetStatus.IN_USE);
        }
    }

    @Override
    @Transactional
    public DeviceAssignment handle(UnassignDeviceCommand command) {
        var device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFound(command.deviceId()));
        var active = assignmentRepository.findActiveByDeviceIdForUpdate(command.deviceId());
        if (active.isEmpty()) {
            if (!command.unassignedByBusinessAdministrator()) {
                throw new InstallationAccessDenied();
            }
            throw new DeviceNotAssigned(command.deviceId());
        }
        var assignment = active.get();
        if (!command.unassignedByBusinessAdministrator()
                && !externalHouseholdsService.canManageHousehold(assignment.getHouseholdId(), command.unassignedBy())) {
            throw new InstallationAccessDenied();
        }

        // unassign validates before changing anything and returns the device to AVAILABLE when it was IN_USE.
        assignment.unassign(command.reason(), command.unassignedBy(), LocalDateTime.now(clock));
        deviceRepository.save(device);
        return assignmentRepository.saveAndFlush(assignment);
    }

    /**
     * Derived rule, to be confirmed with Subscriptions: the household needs an active subscription; an actuator
     * fits when its type was contracted and not all of that type are already assigned; the microphone fits when
     * its room has none yet (the plan includes one per room).
     */
    private void ensureCompatibleWithPlan(Device device, DeviceNetwork network, AssignDeviceToInstallationCommand command) {
        var householdId = network.getHouseholdId();
        var contracted = externalSubscriptionsService.fetchContractedActuators(householdId)
                .orElseThrow(() -> new DeviceIncompatibleWithPlan("The household has no active subscription."));
        var type = device.getDeviceType();
        var roomKey = command.roomKey().strip();
        if (type == DeviceType.MICROPHONE) {
            if (assignmentRepository.countActiveByHouseholdIdAndRoomKeyAndDeviceType(householdId, roomKey, type) > 0) {
                throw new DeviceIncompatibleWithPlan("The room already has its microphone.");
            }
            return;
        }
        var contractedQuantity = contracted.quantityOf(type);
        if (contractedQuantity == 0) {
            throw new DeviceIncompatibleWithPlan("The plan does not include " + type + " devices.");
        }
        if (assignmentRepository.countActiveByHouseholdIdAndDeviceType(householdId, type) >= contractedQuantity) {
            throw new DeviceIncompatibleWithPlan(
                    "All " + contractedQuantity + " " + type + " devices of the plan are already assigned.");
        }
    }
}
