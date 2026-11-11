package com.resolum.alivia.communications.domain.repositories;

import com.resolum.alivia.communications.domain.model.aggregates.PushDevice;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository {
    PushDevice save(PushDevice pushDevice);
    Optional<PushDevice> findByToken(String token);
    List<PushDevice> findByUserId(UserId userId);
    void delete(PushDevice pushDevice);
}
