package com.resolum.alivia.operations;

import com.resolum.alivia.operations.domain.exceptions.SlotUnavailable;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.commands.CreateTechnicalOrderCommand;
import com.resolum.alivia.operations.domain.model.commands.ReassignTechnicianCommand;
import com.resolum.alivia.operations.domain.model.queries.GetTechnicalAvailabilityQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.AvailableBlock;
import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import com.resolum.alivia.operations.domain.model.valueobjects.SlotId;
import com.resolum.alivia.operations.domain.services.AppointmentCommandService;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalAvailabilityController;
import com.resolum.alivia.operations.interfaces.rest.controllers.TechnicalOrdersController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TechnicalOrdersControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private static final SlotId SLOT = SlotId.parse("2026-10-29T08:00-10:00");
    private static final String BODY = """
            {"draftId":"d1","serviceType":"EVALUATION","slotId":"2026-10-29T08:00-10:00",
             "contactName":"María Villanueva","contactPhone":"984123456"}""";

    private AppointmentCommandService commands;
    private AppointmentQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(AppointmentCommandService.class);
        queries = mock(AppointmentQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TechnicalOrdersController(commands, queries),
                        new TechnicalAvailabilityController(queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listsCapacityWithoutTechnicians() throws Exception {
        when(queries.handle(new GetTechnicalAvailabilityQuery(ServiceType.EVALUATION, DAY, 1)))
                .thenReturn(List.of(new AvailableBlock(SLOT, ServiceType.EVALUATION, 2)));

        mvc.perform(get("/api/v1/technical-availability").param("serviceType", "EVALUATION").param("date", "2026-10-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotId").value("2026-10-29T08:00-10:00"))
                .andExpect(jsonPath("$[0].capacity").value(2))
                .andExpect(jsonPath("$[0].technicianId").doesNotExist())
                .andExpect(jsonPath("$[0].technicianName").doesNotExist());
    }

    @Test
    void createsOrdersAndAnswersConflictsWithAlternatives() throws Exception {
        var order = new Appointment("d1", ServiceType.EVALUATION, DAY, SLOT.window(), 7L, "Ana Pérez",
                "María Villanueva", "984123456", null, Instant.parse("2026-10-28T15:00:00Z"));
        ReflectionTestUtils.setField(order, "id", 4L);
        var alternative = new AvailableBlock(SlotId.parse("2026-10-29T10:00-12:00"), ServiceType.EVALUATION, 1);
        when(commands.handle(any(CreateTechnicalOrderCommand.class)))
                .thenReturn(order).thenThrow(new SlotUnavailable(List.of(alternative)));

        mvc.perform(post("/api/v1/technical-orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.technicianName").value("Ana Pérez"))
                .andExpect(jsonPath("$.assignments[0].reason").value("BOOKED"));
        mvc.perform(post("/api/v1/technical-orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("slot_unavailable"))
                .andExpect(jsonPath("$.alternatives[0].slotId").value("2026-10-29T10:00-12:00"));
        verify(commands, times(2)).handle(new CreateTechnicalOrderCommand("d1", ServiceType.EVALUATION, SLOT,
                "María Villanueva", "984123456", null));
    }

    @Test
    void rejectsMalformedBlocksAndUnknownOrders() throws Exception {
        mvc.perform(post("/api/v1/technical-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("2026-10-29T08:00-10:00", "mañana")))
                .andExpect(status().isBadRequest());
        when(queries.handle(any(com.resolum.alivia.operations.domain.model.queries.GetTechnicalOrderByIdQuery.class)))
                .thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/technical-orders/9")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/technical-orders/9/reassignment").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(commands, never()).handle(any(ReassignTechnicianCommand.class));
    }
}
