package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A home in service for the administration and the technicians who served it: who receives the visits, where,
 * the plan the family pays, the devices installed and its technical orders (latest first).
 */
public record ServicedHomeResource(String id, String code, String contactName, String contactPhone, String directions,
                                Instant installedAt, String installedBy, String planCode, String planName,
                                String billingCycle, BigDecimal monthlyAmount, String currency, String paymentStatus,
                                int rooms, List<Device> devices, List<Order> orders) {

    public record Device(String alias, String macAddress, String type) {
    }

    public record Order(Long id, String code, String serviceType, String status, LocalDate date, String startTime,
                        String endTime, String technicianName, Instant startedAt, Instant completedAt,
                        String incidentCode, String incidentStatus) {
    }
}
