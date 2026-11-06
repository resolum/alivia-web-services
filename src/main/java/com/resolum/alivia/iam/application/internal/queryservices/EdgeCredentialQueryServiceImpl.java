package com.resolum.alivia.iam.application.internal.queryservices;

import com.resolum.alivia.iam.domain.model.aggregates.EdgeCredential;
import com.resolum.alivia.iam.domain.model.queries.GetEdgeCredentialByEdgeIdQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeCredentialView;
import com.resolum.alivia.iam.domain.repositories.EdgeCredentialRepository;
import com.resolum.alivia.iam.domain.services.EdgeCredentialQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Describes the credentials of the edge services. A secret never expires by itself (an edge in a household cannot
 * rotate it and would go silent), so age only produces a recommendation to rotate it.
 */
@Service
public class EdgeCredentialQueryServiceImpl implements EdgeCredentialQueryService {

    private final EdgeCredentialRepository repository;
    private final Clock clock;
    private final Duration rotationRecommendedAfter;

    /**
     * @param rotationRecommendedAfter age of an active secret after which {@code rotationRecommended} is true
     */
    public EdgeCredentialQueryServiceImpl(EdgeCredentialRepository repository, Clock clock,
                                          @Value("${alivia.iam.edge-credential.rotation-recommended-after:P365D}")
                                          Duration rotationRecommendedAfter) {
        this.repository = repository;
        this.clock = clock;
        this.rotationRecommendedAfter = rotationRecommendedAfter;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EdgeCredentialView> handle(GetEdgeCredentialByEdgeIdQuery query) {
        return repository.findByEdgeId(query.edgeId()).map(this::viewOf);
    }

    private EdgeCredentialView viewOf(EdgeCredential credential) {
        // The issue date is stored without a zone: it is the local time of the application clock, so that zone is the one
        // that gives it meaning when it is compared with the present.
        var now = ZonedDateTime.now(clock);
        var age = Duration.between(credential.getIssuedAt().atZone(clock.getZone()), now);
        var ageDays = Math.max(0, age.toDays());
        return new EdgeCredentialView(credential.getEdgeId(), credential.getEdgeKind(), credential.isActive(),
                credential.getIssuedAt(), credential.getIssuedBy(), ageDays,
                credential.isActive() && age.compareTo(rotationRecommendedAfter) > 0, credential.getLastUsedAt(),
                credential.getRevokedAt(), credential.getRevokedBy(), credential.getRevokedReason());
    }
}
