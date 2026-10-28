package com.resolum.alivia.operations;

import com.resolum.alivia.shared.domain.model.valueobjects.TimeRange;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.operations.domain.exceptions.AppointmentSlotTaken;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.ScheduleAppointmentCommand;
import com.resolum.alivia.operations.domain.model.queries.GetAvailableSlotsQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableSlot;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.interfaces.rest.controllers.AppointmentsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentsControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private static final TimeRange WINDOW = new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0));
    private static final String BODY = """
            {"draftId":"d1","date":"2026-10-29","startTime":"10:00","endTime":"12:00","technicianId":7,
             "contactName":"María Villanueva","contactPhone":"984123456","directions":"Puerta verde"}""";

    private AppointmentCommandService commands;
    private AppointmentQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(AppointmentCommandService.class);
        queries = mock(AppointmentQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentsController(commands, queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listsAvailableSlots() throws Exception {
        when(queries.handle(new GetAvailableSlotsQuery(DAY, 3)))
                .thenReturn(List.of(new AvailableSlot(DAY, WINDOW, 7L, "Ana Pérez", 2)));

        mvc.perform(get("/api/v1/appointments/available-slots").param("from", "2026-10-29").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("10:00"))
                .andExpect(jsonPath("$[0].technicianName").value("Ana Pérez"))
                .andExpect(jsonPath("$[0].availableTechnicians").value(2));
    }

    @Test
    void schedulesAndReportsTakenWindows() throws Exception {
        var appointment = new Appointment("d1", DAY, WINDOW, 7L, "Ana Pérez", "María Villanueva", "984123456", "Puerta verde");
        ReflectionTestUtils.setField(appointment, "id", 4L);
        when(commands.handle(any(ScheduleAppointmentCommand.class))).thenReturn(appointment).thenThrow(new AppointmentSlotTaken());

        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicianName").value("Ana Pérez"))
                .andExpect(jsonPath("$.endTime").value("12:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        verify(commands, times(2)).handle(new ScheduleAppointmentCommand("d1", DAY, LocalTime.of(10, 0),
                LocalTime.of(12, 0), 7L, "María Villanueva", "984123456", "Puerta verde"));
    }
}
