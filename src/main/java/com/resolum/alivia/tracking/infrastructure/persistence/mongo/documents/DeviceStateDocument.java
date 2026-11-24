package com.resolum.alivia.tracking.infrastructure.persistence.mongo.documents;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** The last reliable state of a device; its id is the id of the device, so there is one document per device. */
@Document("device_states")
public class DeviceStateDocument {

    @Id
    private Long id;
    private Long edgeId;
    @Indexed(name = "household")
    private Long householdId;
    private String connectionStatus;
    private String operationalStatus;
    private String currentState;
    private Integer batteryPct;
    private Instant lastCommunicationAt;
    private Instant observedAt;
    private Instant updatedAt;
    private List<String> faults;

    protected DeviceStateDocument() {
    }

    public DeviceState toDomain() {
        return new DeviceState(id, edgeId, householdId, ConnectionStatus.valueOf(connectionStatus),
                OperationalStatus.valueOf(operationalStatus), currentState, batteryPct, lastCommunicationAt, observedAt,
                updatedAt, faults);
    }
}
