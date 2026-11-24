package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.EdgeStatusDocument;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link EdgeStatusRepository} over the {@code edge_statuses} collection, one document per edge. */
@Component
public class EdgeStatusRepositoryImpl implements EdgeStatusRepository {

    private final MongoTemplate mongo;

    public EdgeStatusRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<EdgeStatus> findByEdgeId(Long edgeId) {
        return Optional.ofNullable(mongo.findById(edgeId, EdgeStatusDocument.class)).map(EdgeStatusDocument::toDomain);
    }

    @Override
    public Optional<EdgeStatus> recordHeartbeat(EdgeStatus status) {
        var query = new Query(where("_id").is(status.edgeId()));
        var update = new Update()
                .set("installationId", status.installationId())
                .set("householdId", status.householdId())
                .set("lastReportAt", status.lastReportAt())
                .set("link", EdgeLink.ONLINE.name())
                .set("pendingEvents", status.pendingEvents())
                .unset("offlineSince");
        var options = FindAndModifyOptions.options().upsert(true).returnNew(false);
        EdgeStatusDocument before;
        try {
            before = mongo.findAndModify(query, update, options, EdgeStatusDocument.class);
        } catch (DuplicateKeyException firstReportsAtOnce) {
            // Two first reports raced to create the document: the second one now finds it and updates it.
            before = mongo.findAndModify(query, update, options, EdgeStatusDocument.class);
        }
        return Optional.ofNullable(before).map(EdgeStatusDocument::toDomain);
    }

    @Override
    public boolean markOffline(Long edgeId, Instant seen, Instant offlineSince) {
        var query = new Query(where("_id").is(edgeId).and("link").is(EdgeLink.ONLINE.name())
                .and("lastReportAt").is(seen.truncatedTo(ChronoUnit.MILLIS)));
        var update = new Update().set("link", EdgeLink.OFFLINE.name()).set("offlineSince", offlineSince);
        return mongo.findAndModify(query, update, EdgeStatusDocument.class) != null;
    }

    @Override
    public List<EdgeStatus> findOnlineSilentSince(Instant limit) {
        var query = new Query(where("link").is(EdgeLink.ONLINE.name()).and("lastReportAt").lt(limit));
        return mongo.find(query, EdgeStatusDocument.class).stream().map(EdgeStatusDocument::toDomain).toList();
    }
}
