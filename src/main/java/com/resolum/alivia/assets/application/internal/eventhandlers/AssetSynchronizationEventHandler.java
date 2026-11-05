package com.resolum.alivia.assets.application.internal.eventhandlers;

import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage.AssetKind;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage.Type;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.aggregates.Asset;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.events.AssetStatusChangedEvent;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceAssignedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceUnassignedEvent;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * When something changes in the devices or edges, tells the edge services of the installation they belong to
 * so they synchronize. It runs after the change was committed (or at once when there is no transaction), and a
 * failure to notify never undoes or fails the change: it is logged.
 *
 * <p>An asset that is not placed in any installation has no edge services to tell.</p>
 */
@Slf4j
@Component
public class AssetSynchronizationEventHandler {
    private final DeviceAssignmentRepository assignmentRepository;
    private final DeviceNetworkRepository networkRepository;
    private final EdgeSynchronizationService edgeSynchronizationService;

    public AssetSynchronizationEventHandler(DeviceAssignmentRepository assignmentRepository,
                                            DeviceNetworkRepository networkRepository,
                                            EdgeSynchronizationService edgeSynchronizationService) {
        this.assignmentRepository = assignmentRepository;
        this.networkRepository = networkRepository;
        this.edgeSynchronizationService = edgeSynchronizationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DeviceAssignedEvent event) {
        notifyEdges(() -> List.of(placement(Type.DEVICE_ASSIGNED, event.assignment())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DeviceUnassignedEvent event) {
        notifyEdges(() -> List.of(placement(Type.DEVICE_UNASSIGNED, event.assignment())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AssetStatusChangedEvent event) {
        notifyEdges(() -> messagesFor(Type.ASSET_STATUS_CHANGED, event.asset()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AssetUpdatedEvent event) {
        notifyEdges(() -> messagesFor(Type.ASSET_UPDATED, event.asset()));
    }

    private void notifyEdges(java.util.function.Supplier<List<EdgeSyncMessage>> messages) {
        try {
            messages.get().forEach(edgeSynchronizationService::publish);
        } catch (RuntimeException exception) {
            // The change is already stored; the edge services catch up on their next synchronization.
            log.error("Could not notify the edge services of an inventory change", exception);
        }
    }

    private static EdgeSyncMessage placement(Type type, DeviceAssignment assignment) {
        var device = assignment.getDevice();
        return new EdgeSyncMessage(type, assignment.getInstallationId(), assignment.getHouseholdId(), AssetKind.DEVICE,
                device.getId(), device.getMacAddress().macAddress(), device.getDeviceType().name(),
                device.getAssetStatus().name(), assignment.getRoomKey(), assignment.getFunction());
    }

    /** One message per installation the asset belongs to: none when it is not placed anywhere. */
    private List<EdgeSyncMessage> messagesFor(Type type, Asset asset) {
        if (asset instanceof Device device) {
            return assignmentRepository.findActiveByDeviceId(device.getId())
                    .map(assignment -> new EdgeSyncMessage(type, assignment.getInstallationId(),
                            assignment.getHouseholdId(), AssetKind.DEVICE, device.getId(),
                            device.getMacAddress().macAddress(), device.getDeviceType().name(),
                            device.getAssetStatus().name(), assignment.getRoomKey(), assignment.getFunction()))
                    .stream().toList();
        }
        if (asset instanceof Edge edge) {
            return networkRepository.findAllByEdgeId(edge.getId()).stream()
                    .map(network -> edgeMessage(type, edge, network))
                    .toList();
        }
        return List.of();
    }

    private static EdgeSyncMessage edgeMessage(Type type, Edge edge, DeviceNetwork network) {
        return new EdgeSyncMessage(type, network.getInstallationId().installationId(), network.getHouseholdId(),
                AssetKind.EDGE, edge.getId(), edge.getMacAddress().macAddress(), edge.getEdgeType().name(),
                edge.getAssetStatus().name(), null, null);
    }
}
