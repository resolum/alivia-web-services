package com.resolum.alivia.operations.application.acl;

import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.OpenInstallationCommand;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianIdByUserIdQuery;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.IncidentPersistenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.resolum.alivia.operations.domain.model.valueobjects.AppointmentStatus;
import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationsContextFacadeImpl implements OperationsContextFacade {
    /** Visits of the contracting flow; maintenance visits belong to the incidents of a home in service. */
    private static final Set<ServiceType> CONTRACTING_VISITS = Set.of(ServiceType.EVALUATION, ServiceType.INSTALLATION);

    private final AppointmentRepository appointments;
    private final AppointmentCommandService commands;
    private final AppointmentQueryService queries;
    private final IncidentPersistenceRepository incidents;

    @Autowired
    public OperationsContextFacadeImpl(AppointmentRepository appointments, AppointmentCommandService commands,
                                       AppointmentQueryService queries, IncidentPersistenceRepository incidents) {
        this.appointments = appointments;
        this.commands = commands;
        this.queries = queries;
        this.incidents = incidents;
    }

    /** Only the contracting flow, without the homes in service. */
    public OperationsContextFacadeImpl(AppointmentRepository appointments, AppointmentCommandService commands) {
        this(appointments, commands, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstalledHome> fetchInstalledHomes(Long technicianUserId) {
        if (queries == null || incidents == null) return List.of();
        Long technicianId = null;
        if (technicianUserId != null) {
            var found = queries.handle(new GetTechnicianIdByUserIdQuery(technicianUserId));
            if (found.isEmpty()) return List.of();
            technicianId = found.get();
        }
        var orders = appointments.findAll().stream().collect(Collectors.groupingBy(Appointment::getDraftId));
        var incidentIds = orders.values().stream().flatMap(List::stream)
                .map(Appointment::getIncidentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Incident> incidentsById = incidents.findAllById(incidentIds).stream()
                .collect(Collectors.toMap(Incident::getId, incident -> incident));
        var homes = new java.util.ArrayList<InstalledHome>();
        for (var entry : orders.entrySet()) {
            var installation = entry.getValue().stream()
                    .filter(order -> order.getServiceType() == ServiceType.INSTALLATION
                            && order.getStatus() == AppointmentStatus.COMPLETED
                            && order.getInstallationResult() == InstallationResult.APPROVED)
                    .findFirst();
            if (installation.isEmpty()) continue;
            final Long servedBy = technicianId;
            if (servedBy != null && entry.getValue().stream().noneMatch(order -> servedBy.equals(order.getTechnicianId()))) {
                continue;
            }
            homes.add(homeOf(installation.get(), entry.getValue(), incidentsById));
        }
        homes.sort(Comparator.comparing(InstalledHome::installedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return homes;
    }

    private static InstalledHome homeOf(Appointment installation, List<Appointment> orders, Map<Long, Incident> incidents) {
        var contact = orders.stream().filter(order -> order.getContactName() != null).findFirst().orElse(installation);
        var directions = orders.stream().map(Appointment::getDirections).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new InstalledHome(installation.getDraftId(), contact.getContactName(), contact.getContactPhone(), directions,
                installation.getCompletedAt(), installation.getTechnicianName(),
                installation.getInstalledDevices().stream()
                        .map(device -> new HomeDevice(device.alias(), device.macAddress(), device.type().name())).toList(),
                orders.stream()
                        .sorted(Comparator.comparing(Appointment::getScheduledDate).thenComparing(Appointment::getStartTime).reversed())
                        .map(order -> {
                            var incident = order.getIncidentId() == null ? null : incidents.get(order.getIncidentId());
                            return new HomeOrder(order.getId(), "ORD-%d-%04d".formatted(order.getScheduledDate().getYear(), order.getId()),
                                    order.getServiceType().name(), order.getStatus().name(), order.getScheduledDate(),
                                    order.getStartTime(), order.getEndTime(), order.getTechnicianName(), order.getStartedAt(),
                                    order.getCompletedAt(), incident == null ? null : incident.code(),
                                    incident == null ? null : incident.getStatus().name());
                        })
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ScheduledVisit> fetchScheduledVisitsOfDrafts(Collection<String> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) return Map.of();
        return appointments.findByDraftIdInAndStatusIn(draftIds,
                        List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)).stream()
                .filter(order -> CONTRACTING_VISITS.contains(order.getServiceType()))
                .collect(Collectors.groupingBy(Appointment::getDraftId, Collectors.collectingAndThen(
                        // INSTALLATION is declared after EVALUATION, so the highest ordinal is the furthest stage.
                        Collectors.maxBy(Comparator.comparing(Appointment::getServiceType)),
                        order -> order.map(visit -> new ScheduledVisit(visit.getServiceType().name(),
                                visit.getScheduledDate(), visit.getInstallationResult() == InstallationResult.APPROVED)).orElseThrow())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractingVisit> fetchContractingVisitsOfDraft(String draftId) {
        if (draftId == null || draftId.isBlank()) return List.of();
        return appointments.findByDraftIdInAndStatusIn(List.of(draftId),
                        List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED)).stream()
                .filter(order -> CONTRACTING_VISITS.contains(order.getServiceType()))
                .collect(Collectors.toMap(Appointment::getServiceType, order -> order,
                        (first, second) -> second.getId() != null && first.getId() != null
                                && second.getId() > first.getId() ? second : first))
                .values().stream()
                .sorted(Comparator.comparing(Appointment::getServiceType))
                .map(OperationsContextFacadeImpl::toVisit)
                .toList();
    }

    private static ContractingVisit toVisit(Appointment order) {
        var result = order.getServiceType() == ServiceType.EVALUATION ? order.getEvaluationResult() : order.getInstallationResult();
        return new ContractingVisit(order.getServiceType().name(), order.getStatus().name(), order.getScheduledDate(),
                order.getStartTime(), order.getEndTime(), order.getTechnicianName(), order.getDirections(),
                result == null ? null : result.name(), order.getCompletionNotes());
    }

    @Override
    public void openInstallationOfConfirmedRequest(String draftId) {
        commands.handle(new OpenInstallationCommand(draftId));
    }
}
