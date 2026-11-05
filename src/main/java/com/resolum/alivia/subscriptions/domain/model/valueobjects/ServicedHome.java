package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A home in service: a contracting request whose installation closed approved, with the plan the family pays,
 * the devices installed and the technical orders of the home.
 *
 * @param request the contracting request (plan, cycle, amount, payment), or null when it is no longer listed
 */
public record ServicedHome(String draftId, HiringRequest request, String contactName, String contactPhone, String directions,
                        Instant installedAt, String installedBy, List<Device> devices, List<Order> orders) {

    public record Device(String alias, String macAddress, String type) {
    }

    public record Order(Long id, String code, String serviceType, String status, LocalDate date, LocalTime startTime,
                        LocalTime endTime, String technicianName, Instant startedAt, Instant completedAt,
                        String incidentCode, String incidentStatus) {
    }

    /** Readable code of the home: "HOG-" and the last eight characters of its request. */
    public String code() {
        var hex = draftId.replace("-", "");
        return "HOG-" + hex.substring(Math.max(0, hex.length() - 8)).toUpperCase(java.util.Locale.ROOT);
    }

    /** The same home with the contracting request that pays for it. */
    public ServicedHome withRequest(HiringRequest contractingRequest) {
        return new ServicedHome(draftId, contractingRequest, contactName, contactPhone, directions, installedAt, installedBy, devices, orders);
    }

    public BigDecimal monthlyAmount() {
        return request == null ? null : request.heldAmount();
    }
}
