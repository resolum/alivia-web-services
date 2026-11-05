package com.resolum.alivia.assets.application.internal.outboundservices.edge;

import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetTableVersionByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.EdgeTableWaitService;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The way the edge services learn that their table changed: they ask for it, and when they are up to date the request
 * waits here until a newer version exists. The table they read is always the source of truth; this class only decides
 * when to answer, so a lost wake-up costs a few seconds and never a change.
 *
 * <p>Two things wake a waiting request. When this instance commits a change, the event of the registry arrives through
 * the {@link EdgeSynchronizationService} port and the waiters of that installation are checked at once. And every
 * {@code poll-interval} the installations that have waiters are checked against the database, which also covers changes
 * committed by another instance of the application. A check reads one version per installation; only a waiter that is
 * behind reads its table. Nothing runs in the thread that committed the change.</p>
 *
 * <p>A request waits without holding a thread of the server. An edge has at most one wait: a newer request of the same
 * edge ends the previous one, so a client that reconnects without closing cannot pile them up.</p>
 */
@Slf4j
@Service
public class LongPollEdgeSynchronizationService implements EdgeSynchronizationService, EdgeTableWaitService {

    private record Waiter(Long edgeId, Long installationId, long knownVersion,
                          CompletableFuture<Optional<EdgeAccessTable>> future) {
    }

    private final HouseholdDeviceRegistryQueryService queryService;
    private final Executor worker;
    private final ScheduledExecutorService ticker;
    private final Duration pollInterval;
    private final Map<Long, Waiter> waiters = new ConcurrentHashMap<>();

    @Autowired
    public LongPollEdgeSynchronizationService(HouseholdDeviceRegistryQueryService queryService,
                                              @Value("${alivia.assets.edge-sync.poll-interval:PT2S}") Duration pollInterval) {
        this(queryService, newPool(), pollInterval);
    }

    private LongPollEdgeSynchronizationService(HouseholdDeviceRegistryQueryService queryService,
                                               ScheduledThreadPoolExecutor pool, Duration pollInterval) {
        this(queryService, pool, pool, pollInterval);
    }

    /** For tests: the work runs in the given executor and nothing is scheduled. */
    LongPollEdgeSynchronizationService(HouseholdDeviceRegistryQueryService queryService, Executor worker,
                                       ScheduledExecutorService ticker, Duration pollInterval) {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("The poll interval must be positive.");
        }
        this.queryService = queryService;
        this.worker = worker;
        this.ticker = ticker;
        this.pollInterval = pollInterval;
    }

    private static ScheduledThreadPoolExecutor newPool() {
        var counter = new AtomicInteger();
        var pool = new ScheduledThreadPoolExecutor(2, runnable -> {
            var thread = new Thread(runnable, "edge-table-waiters-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }

    @PostConstruct
    void start() {
        if (ticker != null) {
            ticker.scheduleWithFixedDelay(this::checkAll, pollInterval.toMillis(), pollInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    void stop() {
        waiters.values().forEach(waiter -> waiter.future().complete(Optional.empty()));
        if (ticker != null) {
            ticker.shutdownNow();
        }
    }

    /** The registry of an installation committed a change: the edge services waiting there are checked now. */
    @Override
    public void publish(EdgeSyncMessage message) {
        if (waiters.isEmpty()) {
            return;
        }
        try {
            worker.execute(() -> check(message.installationId()));
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("Not waking the edge services of installation {}: shutting down", message.installationId());
        }
    }

    @Override
    public CompletableFuture<Optional<EdgeAccessTable>> awaitNewerTable(Long edgeId, Long installationId,
                                                                        long knownVersion, Duration timeout) {
        var future = new CompletableFuture<Optional<EdgeAccessTable>>();
        var waiter = new Waiter(edgeId, installationId, knownVersion, future);
        var previous = waiters.put(edgeId, waiter);
        if (previous != null) {
            previous.future().complete(Optional.empty());
        }
        future.whenComplete((result, error) -> waiters.remove(edgeId, waiter));
        future.completeOnTimeout(Optional.empty(), timeout.toMillis(), TimeUnit.MILLISECONDS);
        // The caller read the version before registering: a change in between is found now, not at the next tick.
        try {
            worker.execute(() -> check(installationId));
        } catch (RejectedExecutionException shuttingDown) {
            future.complete(Optional.empty());
        }
        return future;
    }

    int waiting() {
        return waiters.size();
    }

    void checkAll() {
        waiters.values().stream().map(Waiter::installationId).distinct().forEach(this::check);
    }

    void check(Long installationId) {
        List<Waiter> waiting = waiters.values().stream()
                .filter(waiter -> waiter.installationId().equals(installationId) && !waiter.future().isDone())
                .toList();
        if (waiting.isEmpty()) {
            return;
        }
        try {
            var version = queryService.handle(new GetTableVersionByInstallationIdQuery(installationId));
            for (var waiter : waiting) {
                if (version.isEmpty()) {
                    waiter.future().completeExceptionally(new EdgeWithoutInstallation(waiter.edgeId()));
                } else if (version.get() > waiter.knownVersion()) {
                    deliver(waiter);
                }
            }
        } catch (RuntimeException exception) {
            // The waiters stay: the next check tries again, and their own time limit ends them in any case.
            log.warn("Could not check the edge services waiting for installation {}: {}", installationId, exception.toString());
        }
    }

    private void deliver(Waiter waiter) {
        try {
            var table = queryService.handle(new GetEdgeAccessTableByEdgeIdQuery(waiter.edgeId()));
            if (table.isEmpty()) {
                waiter.future().completeExceptionally(new EdgeWithoutInstallation(waiter.edgeId()));
            } else if (table.get().version() > waiter.knownVersion()) {
                waiter.future().complete(table);
            }
        } catch (RuntimeException exception) {
            log.warn("Could not read the table for edge {}: {}", waiter.edgeId(), exception.toString());
        }
    }
}
