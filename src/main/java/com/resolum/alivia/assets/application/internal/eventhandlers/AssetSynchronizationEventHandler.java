package com.resolum.alivia.assets.application.internal.eventhandlers;

import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When the table of devices of a household changes, tells the edge services of that household's installation so they
 * update their information. The event already carries the complete table with its version, taken while the registry was
 * locked, so this handler asks nothing from the database.
 *
 * <p>It runs after the change was committed (or at once when there is no transaction). A failure to notify never
 * undoes or fails the change: it is logged.</p>
 */
@Slf4j
@Component
public class AssetSynchronizationEventHandler {
    private final EdgeSynchronizationService edgeSynchronizationService;

    public AssetSynchronizationEventHandler(EdgeSynchronizationService edgeSynchronizationService) {
        this.edgeSynchronizationService = edgeSynchronizationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(HouseholdDevicesChangedEvent event) {
        try {
            edgeSynchronizationService.publish(new EdgeSyncMessage(event.cause(), event.installationId(),
                    event.householdId(), event.deviceId(), event.table()));
        } catch (RuntimeException exception) {
            // The change is already stored; the edge services catch up when they ask for the table.
            log.error("Could not notify the edge services of an inventory change", exception);
        }
    }
}
