package com.resolum.alivia.assets.domain.model.queries;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import org.springframework.data.domain.Pageable;

/**
 * A page of the devices in the inventory of a business, optionally of one status and one type.
 *
 * @param status     only the devices in this status; {@code null} for any
 * @param deviceType only the devices of this type; {@code null} for any
 */
public record GetDevicesQuery(Long businessId, AssetStatus status, DeviceType deviceType, Pageable pageable) {
}
