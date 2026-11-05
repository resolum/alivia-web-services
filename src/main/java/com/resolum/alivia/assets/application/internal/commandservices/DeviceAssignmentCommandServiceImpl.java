package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.commands.AssignDeviceToHouseholdCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DeviceAssignmentCommandServiceImpl implements DeviceAssignmentCommandService {
    private final DeviceRepository deviceRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final ExternalSubscriptionsService externalSubscriptionsService;
    private final Clock clock;

    public DeviceAssignmentCommandServiceImpl(DeviceRepository deviceRepository,
                                              DeviceAssignmentRepository assignmentRepository,
                                              ExternalSubscriptionsService externalSubscriptionsService, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.externalSubscriptionsService = externalSubscriptionsService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DeviceAssignment handle(AssignDeviceToHouseholdCommand command) {
        var device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFound(command.deviceId()));
        device.ensureAssignable();
        ensureCompatibleWithPlan(device, command);

        // Nothing is stored before every rule passed, and assignTo itself fails before changing the device.
        var assignment = device.assignTo(command.householdId(), command.roomKey(), command.function(),
                command.assignedBy(), LocalDateTime.now(clock));
        try {
            deviceRepository.save(device);
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            // Another request assigned the same device between the check and the insert
            // (uk_device_assignments_active_device).
            throw new DeviceNotAvailable(device.getId(), AssetStatus.IN_USE);
        }
    }

    /**
     * Derived rule, to be confirmed with Subscriptions: the household needs an active subscription; an actuator
     * fits when its type was contracted and not all of that type are already assigned; the microphone fits when
     * its room has none yet (the plan includes one per room).
     */
    private void ensureCompatibleWithPlan(Device device, AssignDeviceToHouseholdCommand command) {
        var contracted = externalSubscriptionsService.fetchContractedActuators(command.householdId())
                .orElseThrow(() -> new DeviceIncompatibleWithPlan("The household has no active subscription."));
        var type = device.getDeviceType();
        var roomKey = command.roomKey().strip();
        if (type == DeviceType.MICROPHONE) {
            if (assignmentRepository.countActiveByHouseholdIdAndRoomKeyAndDeviceType(
                    command.householdId(), roomKey, type) > 0) {
                throw new DeviceIncompatibleWithPlan("The room already has its microphone.");
            }
            return;
        }
        var contractedQuantity = contracted.quantityOf(type);
        if (contractedQuantity == 0) {
            throw new DeviceIncompatibleWithPlan("The plan does not include " + type + " devices.");
        }
        if (assignmentRepository.countActiveByHouseholdIdAndDeviceType(command.householdId(), type)
                >= contractedQuantity) {
            throw new DeviceIncompatibleWithPlan(
                    "All " + contractedQuantity + " " + type + " devices of the plan are already assigned.");
        }
    }
}
