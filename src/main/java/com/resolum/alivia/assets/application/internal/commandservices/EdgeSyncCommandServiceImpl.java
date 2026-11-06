package com.resolum.alivia.assets.application.internal.commandservices;

import com.resolum.alivia.assets.domain.exceptions.AppliedVersionAhead;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.aggregates.HouseholdDeviceRegistry;
import com.resolum.alivia.assets.domain.model.commands.AcknowledgeEdgeVersionCommand;
import com.resolum.alivia.assets.domain.model.commands.RecordEdgeSeenCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.AppliedEdgeVersion;
import com.resolum.alivia.assets.domain.services.EdgeSyncCommandService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.EdgeSyncStateRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.HouseholdDeviceRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Keeps what each edge service confirmed. The server never acts on it: the edge asks for the table and the highest
 * version wins. It is bookkeeping for the business, so it is built from single statements that cannot conflict with the
 * edge reading its table or with a confirmation that arrives at the same time.
 */
@Service
public class EdgeSyncCommandServiceImpl implements EdgeSyncCommandService {

    /** An edge that asks every few seconds does not write on every request. */
    static final Duration SEEN_RECORD_INTERVAL = Duration.ofMinutes(1);

    private final EdgeSyncStateRepository syncStates;
    private final HouseholdDeviceRegistryRepository registryRepository;
    private final Clock clock;

    public EdgeSyncCommandServiceImpl(EdgeSyncStateRepository syncStates,
                                      HouseholdDeviceRegistryRepository registryRepository, Clock clock) {
        this.syncStates = syncStates;
        this.registryRepository = registryRepository;
        this.clock = clock;
    }

    /**
     * Reports newer than the table are refused. Reports of the same or a lower version answer with what is stored. The
     * version is raised by one conditional statement, so two confirmations arriving together leave the higher one.
     */
    @Override
    @Transactional
    public AppliedEdgeVersion handle(AcknowledgeEdgeVersionCommand command) {
        var registry = registryRepository.findByAssignedEdgeId(command.edgeId())
                .filter(HouseholdDeviceRegistry::isActive)
                .orElseThrow(() -> new EdgeWithoutInstallation(command.edgeId()));
        if (command.appliedVersion() > registry.getTableVersion()) {
            throw new AppliedVersionAhead(command.edgeId(), command.appliedVersion(), registry.getTableVersion());
        }
        var installationId = registry.getInstallationId().installationId();
        var now = LocalDateTime.now(clock);

        ensureStateOf(command.edgeId(), installationId, now);
        // After a restore the stored confirmation can be higher than the table: it is forgotten, not kept as a maximum.
        syncStates.resetIfAheadOfTable(command.edgeId(), registry.getTableVersion());
        syncStates.raiseAppliedVersion(command.edgeId(), command.appliedVersion(), now);
        // A report is also a sign of life, even when it did not raise the version.
        syncStates.touchLastSeen(command.edgeId(), now, now.minus(SEEN_RECORD_INTERVAL));

        var state = syncStates.findById(command.edgeId()).orElseThrow();
        return new AppliedEdgeVersion(command.edgeId(), state.getAppliedVersion(), state.getAckedAt());
    }

    @Override
    @Transactional
    public void handle(RecordEdgeSeenCommand command) {
        var now = LocalDateTime.now(clock);
        var staleBefore = now.minus(SEEN_RECORD_INTERVAL);
        var state = syncStates.findById(command.edgeId());
        // The common case, many times a minute: one read and nothing to write.
        if (state.isPresent() && state.get().getInstallationId().equals(command.installationId())
                && state.get().getLastSeenAt() != null && !state.get().getLastSeenAt().isBefore(staleBefore)) {
            return;
        }
        ensureStateOf(command.edgeId(), command.installationId(), now);
        syncStates.touchLastSeen(command.edgeId(), now, staleBefore);
    }

    /** The edge has a row for this installation: created if missing, started over if it referred to another one. */
    private void ensureStateOf(Long edgeId, Long installationId, LocalDateTime now) {
        syncStates.insertIfAbsent(edgeId, installationId, now);
        syncStates.resetIfOtherInstallation(edgeId, installationId);
    }
}
