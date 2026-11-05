package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.outboundservices.acl.ExternalHcmService;
import com.resolum.alivia.operations.application.internal.queryservices.AppointmentQueryServiceImpl;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicianScheduleQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.TechnicianAvailability;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.AppointmentRepository;
import com.resolum.alivia.operations.infrastructure.persistence.jpa.repositories.EvaluationSlotHoldRepository;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The technician sees the work schedule the administration assigned them, day by day. */
class TechnicianScheduleTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Test
    void readsTheShiftsOfTheSignedInTechnicianDayByDay() throws Exception {
        var hcm = mock(ExternalHcmService.class);
        when(hcm.fetchTechnicianIdOfUser(70L)).thenReturn(Optional.of(7L));
        when(hcm.fetchTechnicianIdOfUser(71L)).thenReturn(Optional.empty());
        var morning = new TimeRange(LocalTime.of(8, 0), LocalTime.of(12, 0));
        var afternoon = new TimeRange(LocalTime.of(13, 0), LocalTime.of(16, 0));
        when(hcm.fetchTechniciansWorkingOn(any())).thenReturn(List.of());
        when(hcm.fetchTechniciansWorkingOn(MONDAY)).thenReturn(List.of(
                new TechnicianAvailability(8L, "Bruno Silva", List.of(morning)),
                new TechnicianAvailability(7L, "Ana Torres", List.of(morning, afternoon))));
        when(hcm.fetchTechniciansWorkingOn(MONDAY.plusDays(1))).thenReturn(List.of(new TechnicianAvailability(7L, "Ana Torres", List.of())));
        AppointmentQueryService queries = new AppointmentQueryServiceImpl(mock(AppointmentRepository.class), mock(EvaluationSlotHoldRepository.class),
                hcm, Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC));

        var days = queries.handle(new GetTechnicianScheduleQuery(70L, MONDAY, MONDAY.plusDays(6)));
        assertEquals(1, days.size());
        assertEquals(List.of(morning, afternoon), days.getFirst().shifts());
        assertTrue(queries.handle(new GetTechnicianScheduleQuery(71L, MONDAY, MONDAY)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queries.handle(new GetTechnicianScheduleQuery(70L, MONDAY, MONDAY.minusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> queries.handle(new GetTechnicianScheduleQuery(70L, MONDAY, MONDAY.plusDays(63))));
        assertThrows(IllegalArgumentException.class, () -> queries.handle(new GetTechnicianScheduleQuery(70L, null, MONDAY)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TechnicalOrdersController(mock(AppointmentCommandService.class), queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var technician = new UsernamePasswordAuthenticationToken("70", null, List.of());
        mvc.perform(get("/api/v1/technical-orders/mine/schedule").param("from", "2026-11-02").param("to", "2026-11-08").principal(technician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$[0].shifts[0].start").value("08:00"))
                .andExpect(jsonPath("$[0].shifts[1].end").value("16:00"));
        mvc.perform(get("/api/v1/technical-orders/mine/schedule").param("from", "2026-11-08").param("to", "2026-11-02").principal(technician))
                .andExpect(status().isBadRequest());
    }
}
