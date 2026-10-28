package com.resolum.alivia.support;

import com.resolum.alivia.hcm.application.acl.HcmContextFacadeImpl;
import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade.StaffProfileData;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.support.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SupportAclTest {
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 29);

    @Test
    void hcmListsOnlyTechniciansWithContractAndScheduleInForce() {
        var employees = mock(EmployeeRepository.class);
        var contracts = mock(ContractPersistenceRepository.class);
        var schedules = mock(WorkSchedulePersistenceRepository.class);
        var profiles = mock(ExternalProfilesService.class);
        var technician = employee(3L, EmployeeRole.TECH);
        var withoutContract = employee(4L, EmployeeRole.TECH);
        var manager = employee(5L, EmployeeRole.SUBSCRIPTION_MANAGER);
        when(employees.findAll()).thenReturn(List.of(technician, withoutContract, manager));
        when(contracts.findByEmployeeId(anyLong())).thenReturn(List.of());
        when(contracts.findByEmployeeId(3L)).thenReturn(List.of(contract(3L)));
        when(contracts.findByEmployeeId(5L)).thenReturn(List.of(contract(5L)));
        when(schedules.findByBusinessIdAndStatus(1L, WorkScheduleStatus.ACTIVE))
                .thenReturn(List.of(schedule(3L), schedule(4L), schedule(5L)));
        when(profiles.fetchStaffProfile(30L)).thenReturn(Optional.of(new StaffProfileData(30L, "ana@alivia.pe", null,
                null, "51984123456", "Ana", "Pérez", "12345678", LocalDate.of(1990, 1, 1))));
        var facade = new HcmContextFacadeImpl(employees, contracts, schedules, profiles, 1L);

        var technicians = facade.fetchTechniciansWorkingOn(THURSDAY);

        assertEquals(1, technicians.size());
        assertEquals(3L, technicians.getFirst().employeeId());
        assertEquals("Ana Pérez", technicians.getFirst().fullName());
        assertEquals(LocalTime.of(13, 0), technicians.getFirst().shifts().get(1).start());
        assertTrue(facade.fetchTechniciansWorkingOn(THURSDAY.plusDays(2)).isEmpty());

        var hcmFacade = mock(HcmContextFacade.class);
        when(hcmFacade.fetchTechniciansWorkingOn(THURSDAY)).thenReturn(technicians);
        assertEquals("Ana Pérez", new ExternalHcmService(hcmFacade).fetchTechniciansWorkingOn(THURSDAY).getFirst().fullName());
    }

    @Test
    void subscriptionsAllowsOnlyPreauthorizedDrafts() {
        var drafts = mock(ConfigurationDraftRepository.class);
        var draft = mock(ConfigurationDraft.class);
        when(draft.getPaymentStatus()).thenReturn(PaymentStatus.PREAUTHORIZED, PaymentStatus.AWAITING_CONFIRMATION);
        when(drafts.findById("d1")).thenReturn(Optional.of(draft));
        when(drafts.findById("missing")).thenReturn(Optional.empty());
        var service = new ExternalSubscriptionsService(new SubscriptionsContextFacadeImpl(drafts));

        assertTrue(service.isReadyForEvaluation("d1"));
        assertFalse(service.isReadyForEvaluation("d1"));
        assertFalse(service.isReadyForEvaluation("missing"));
        assertFalse(service.isReadyForEvaluation(" "));
    }

    private static Employee employee(Long id, EmployeeRole role) {
        var employee = new Employee(1L, id * 10, id * 10, role);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private static Contract contract(Long employeeId) {
        return new Contract(employeeId, 1L, "Técnico", ContractModality.SERVICE, 40,
                new ContractPeriod(THURSDAY.minusMonths(1), THURSDAY.plusMonths(6)), ContractStatus.ACTIVE, null);
    }

    private static WorkSchedule schedule(Long employeeId) {
        var schedule = new WorkSchedule(employeeId, 1L, 9L, "Diurna", new WorkSchedulePeriod(THURSDAY.minusDays(7), null),
                List.of(new WorkShift(DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(17, 0)),
                        new WorkShift(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))), 1L);
        schedule.activate();
        return schedule;
    }
}
