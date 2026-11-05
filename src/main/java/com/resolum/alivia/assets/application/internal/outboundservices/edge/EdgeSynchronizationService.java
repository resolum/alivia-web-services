package com.resolum.alivia.assets.application.internal.outboundservices.edge;

/**
 * Outbound port that delivers a synchronization message to the edge services of an installation.
 *
 * <p>The transport is not defined yet (the edge services live outside this repository). Until one is chosen,
 * {@link LoggingEdgeSynchronizationService} only records the message. A real implementation should be
 * asynchronous and durable (an outbox), because the message is sent after the change was committed: a crash
 * between both would otherwise lose it.</p>
 */
public interface EdgeSynchronizationService {

    void publish(EdgeSyncMessage message);
}
