package com.resolum.alivia.tracking.infrastructure.adapters;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents.DeviceStateDocument;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** {@link DeviceStateRepository} over the {@code device_states} collection, one document per device. */
@Component
public class DeviceStateRepositoryImpl implements DeviceStateRepository {

    private final MongoTemplate mongo;

    public DeviceStateRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<DeviceState> findByDeviceId(Long deviceId) {
        return Optional.ofNullable(mongo.findById(deviceId, DeviceStateDocument.class)).map(DeviceStateDocument::toDomain);
    }

    @Override
    public List<DeviceState> findAllByDeviceIds(Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) return List.of();
        return mongo.find(new Query(where("_id").in(deviceIds)), DeviceStateDocument.class).stream()
                .map(DeviceStateDocument::toDomain).toList();
    }

    @Override
    public boolean saveUnlessOlder(DeviceState state) {
        // One upsert that matches the document only when the one kept was not observed later. When it was, the upsert
        // tries to insert the same id and the unique id refuses it: nothing is written and the report is stale.
        var query = new Query(where("_id").is(state.deviceId()).and("observedAt").lte(state.observedAt()));
        var update = new Update()
                .set("edgeId", state.edgeId())
                .set("householdId", state.householdId())
                .set("connectionStatus", state.connectionStatus().name())
                .set("operationalStatus", state.operationalStatus().name())
                .set("currentState", state.currentState())
                .set("batteryPct", state.batteryPct())
                .set("lastCommunicationAt", state.lastCommunicationAt())
                .set("observedAt", state.observedAt())
                .set("updatedAt", state.updatedAt())
                .set("faults", state.faults());
        try {
            mongo.upsert(query, update, DeviceStateDocument.class);
            return true;
        } catch (DuplicateKeyException newerStateKept) {
            return false;
        }
    }
}
