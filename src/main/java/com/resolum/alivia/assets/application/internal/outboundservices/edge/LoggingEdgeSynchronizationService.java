package com.resolum.alivia.assets.application.internal.outboundservices.edge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Placeholder adapter: records what would be sent to the edge services. Replace it when the transport exists. */
@Slf4j
@Service
public class LoggingEdgeSynchronizationService implements EdgeSynchronizationService {

    @Override
    public void publish(EdgeSyncMessage message) {
        log.info("Edge synchronization pending transport: {} installation={} household={} {} id={} mac={} status={} devices={}",
                message.type(), message.installationId(), message.householdId(), message.assetKind(),
                message.assetId(), message.macAddress(), message.status(),
                message.table() == null ? "-" : message.table().nodes().size());
    }
}
