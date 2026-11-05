package com.resolum.alivia.assets.application.internal.eventhandlers;

import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage.Type;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.events.AssetStatusChangedEvent;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceAssignedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceUnassignedEvent;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DevicePlacement;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.DeviceNetworkQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.function.Supplier;

/**
 * When something changes in the devices or edges of an installation, tells that installation's edge services so
 * they update their information. Every message carries the complete table of devices of the household after the
 * change (see {@link EdgeAccessTable}), so the edge only has to replace its copy.
 *
 * <p>It works from the data of the events, never from entities, and runs after the change was committed (or at once
 * when there is no transaction). A failure to notify never undoes or fails the change: it is logged. An asset that
 * is not placed in any installation has no edge services to tell.</p>
 */
@Slf4j
@Component
public class AssetSynchronizationEventHandler {
    private final DeviceAssignmentRepository assignmentRepository;
    private final DeviceNetworkRepository networkRepository;
    private final DeviceNetworkQueryService networkQueryService;
    private final EdgeSynchronizationService edgeSynchronizationService;

    public AssetSynchronizationEventHandler(DeviceAssignmentRepository assignmentRepository,
                                            DeviceNetworkRepository networkRepository,
                                            DeviceNetworkQueryService networkQueryService,
                                            EdgeSynchronizationService edgeSynchronizationService) {
        this.assignmentRepository = assignmentRepository;
        this.networkRepository = networkRepository;
        this.networkQueryService = networkQueryService;
        this.edgeSynchronizationService = edgeSynchronizationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DeviceAssignedEvent event) {
        notifyEdges(() -> List.of(placement(Type.DEVICE_ASSIGNED, event.placement())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DeviceUnassignedEvent event) {
        notifyEdges(() -> List.of(placement(Type.DEVICE_UNASSIGNED, event.placement())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AssetStatusChangedEvent event) {
        notifyEdges(() -> messagesFor(Type.ASSET_STATUS_CHANGED, event.assetKind(), event.assetId(), event.macAddress(),
                event.assetType(), event.newStatus()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AssetUpdatedEvent event) {
        notifyEdges(() -> messagesFor(Type.ASSET_UPDATED, event.assetKind(), event.assetId(), event.macAddress(),
                event.assetType(), event.status()));
    }

    private void notifyEdges(Supplier<List<EdgeSyncMessage>> messages) {
        try {
            messages.get().forEach(edgeSynchronizationService::publish);
        } catch (RuntimeException exception) {
            // The change is already stored; the edge services catch up on their next synchronization.
            log.error("Could not notify the edge services of an inventory change", exception);
        }
    }

    private EdgeSyncMessage placement(Type type, DevicePlacement placement) {
        return new EdgeSyncMessage(type, placement.installationId(), placement.householdId(), AssetKind.DEVICE,
                placement.deviceId(), placement.macAddress(), placement.deviceType().name(),
                placement.deviceStatus().name(), placement.roomKey(), placement.function(),
                tableOf(placement.installationId()));
    }

    /** One message per installation the asset belongs to: none when it is not placed anywhere. */
    private List<EdgeSyncMessage> messagesFor(Type type, AssetKind kind, Long assetId, String macAddress,
                                              String assetType, AssetStatus status) {
        if (kind == AssetKind.DEVICE) {
            return assignmentRepository.findActiveByDeviceId(assetId)
                    .map(assignment -> new EdgeSyncMessage(type, assignment.getInstallationId(),
                            assignment.getHouseholdId(), kind, assetId, macAddress, assetType, status.name(),
                            assignment.getRoomKey(), assignment.getFunction(), tableOf(assignment.getInstallationId())))
                    .stream().toList();
        }
        return networkRepository.findAllByEdgeId(assetId).stream()
                .map(network -> new EdgeSyncMessage(type, network.getInstallationId().installationId(),
                        network.getHouseholdId(), kind, assetId, macAddress, assetType, status.name(), null, null,
                        tableOf(network.getInstallationId().installationId())))
                .toList();
    }

    private EdgeAccessTable tableOf(Long installationId) {
        return networkQueryService.handle(new GetEdgeAccessTableByInstallationIdQuery(installationId)).orElse(null);
    }
}
