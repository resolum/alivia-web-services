package com.resolum.alivia.tracking.domain.repositories;

import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Keeps the last reliable state of each device. */
public interface DeviceStateRepository {

    Optional<DeviceState> findByDeviceId(Long deviceId);

    List<DeviceState> findAllByDeviceIds(Collection<Long> deviceIds);

    /**
     * Keeps the state unless one observed later is already kept. The check and the write are one atomic operation, so
     * two reports at once leave the one observed last.
     *
     * @return whether it was kept; {@code false} when a state observed later was already there
     */
    boolean saveUnlessOlder(DeviceState state);
}
