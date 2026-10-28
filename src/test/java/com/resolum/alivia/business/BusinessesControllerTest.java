package com.resolum.alivia.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.business.domain.exceptions.BusinessAlreadyExistsForAdministrator;
import com.resolum.alivia.business.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.business.domain.exceptions.UnauthorizedAdministratorException;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.aggregates.Business;
import com.resolum.alivia.business.domain.model.commands.CreateBusinessCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateBusinessCommand;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByAdministratorIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetBusinessByIdQuery;
import com.resolum.alivia.business.domain.services.BusinessCommandService;
import com.resolum.alivia.business.domain.services.BusinessQueryService;
import com.resolum.alivia.business.interfaces.rest.controllers.BusinessesController;
import com.resolum.alivia.business.interfaces.rest.resources.CreateBusinessResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateBusinessResource;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.StaffProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessesController Tests")
class BusinessesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BusinessCommandService commandService;

    @Mock
    private BusinessQueryService queryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var controller = new BusinessesController(commandService, queryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Business createBusinessEntity(Long id, Long profileId, Long adminId) {
        Administrator admin = new Administrator(new UserId(100L), new StaffProfileId(200L), new EmailAddress("admin@test.com"));
        ReflectionTestUtils.setField(admin, "id", adminId);
        Business business = new Business(new BusinessProfileId(profileId), admin);
        ReflectionTestUtils.setField(business, "id", id);
        return business;
    }

    @Test
    @DisplayName("POST /api/v1/businesses returns 201 Created on success")
    void createBusinessSuccess() throws Exception {
        var resource = new CreateBusinessResource(
                null,
                null,
                "Clínica San Pablo",
                "San Pablo S.A.C.",
                20601234567L,
                "Atención Médica",
                "Centro médico",
                "contacto@sanpablo.com",
                "5112345678",
                "Av. El Derby 250",
                -12.109,
                -76.974,
                "place123",
                null,
                "soporte@sanpablo.com",
                "51987654321",
                "51987654321",
                "Tu salud primero",
                1L
        );

        when(commandService.handle(any(CreateBusinessCommand.class))).thenReturn(1L);
        when(queryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.of(createBusinessEntity(1L, 50L, 1L)));

        mockMvc.perform(post("/api/v1/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.businessProfileId").value(50))
                .andExpect(jsonPath("$.administratorId").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/businesses returns 409 Conflict when business already exists for administrator")
    void createBusinessDuplicateForAdmin() throws Exception {
        var resource = new CreateBusinessResource(
                null,
                null,
                "Clínica San Pablo",
                "San Pablo S.A.C.",
                20601234567L,
                "Atención Médica",
                "Centro médico",
                "contacto@sanpablo.com",
                "5112345678",
                "Av. El Derby 250",
                -12.109,
                -76.974,
                "place123",
                null,
                "soporte@sanpablo.com",
                "51987654321",
                "51987654321",
                "Tu salud primero",
                1L
        );

        when(commandService.handle(any(CreateBusinessCommand.class)))
                .thenThrow(new BusinessAlreadyExistsForAdministrator(1L));

        mockMvc.perform(post("/api/v1/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("business_already_exists_for_administrator"));
    }

    @Test
    @DisplayName("PUT /api/v1/businesses/{id} returns 200 OK on success")
    void updateBusinessSuccess() throws Exception {
        var resource = new UpdateBusinessResource(
                1L,
                null,
                null,
                "Clínica San Pablo Sur",
                "San Pablo S.A.C.",
                "Atención Médica",
                "Centro médico ampliado",
                "5112345678",
                null,
                "soporte@sanpablo.com",
                "51987654321",
                "51987654321",
                "Tu salud primero"
        );

        when(commandService.handle(any(UpdateBusinessCommand.class))).thenReturn(1L);
        when(queryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.of(createBusinessEntity(1L, 50L, 1L)));

        mockMvc.perform(put("/api/v1/businesses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/businesses/{id} returns 403 Forbidden when administrator is unauthorized")
    void updateBusinessUnauthorized() throws Exception {
        var resource = new UpdateBusinessResource(
                2L,
                null,
                null,
                "Clínica San Pablo Sur",
                "San Pablo S.A.C.",
                "Atención Médica",
                "Centro médico ampliado",
                "5112345678",
                null,
                "soporte@sanpablo.com",
                "51987654321",
                "51987654321",
                "Tu salud primero"
        );

        when(commandService.handle(any(UpdateBusinessCommand.class)))
                .thenThrow(new UnauthorizedAdministratorException("Administrator is not authorized to manage this business."));

        mockMvc.perform(put("/api/v1/businesses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("unauthorized_administrator"));
    }

    @Test
    @DisplayName("GET /api/v1/businesses/{id} returns 200 OK when found")
    void getBusinessByIdFound() throws Exception {
        when(queryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.of(createBusinessEntity(1L, 50L, 1L)));

        mockMvc.perform(get("/api/v1/businesses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.businessProfileId").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/businesses/{id} returns 404 when not found")
    void getBusinessByIdNotFound() throws Exception {
        when(queryService.handle(any(GetBusinessByIdQuery.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/businesses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("business_not_found"));
    }

    @Test
    @DisplayName("GET /api/v1/businesses/by-administrator/{adminId} returns 200 OK when found")
    void getBusinessByAdminIdFound() throws Exception {
        when(queryService.handle(any(GetBusinessByAdministratorIdQuery.class)))
                .thenReturn(Optional.of(createBusinessEntity(1L, 50L, 1L)));

        mockMvc.perform(get("/api/v1/businesses/by-administrator/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/businesses/by-administrator/{adminId} returns 404 when not found")
    void getBusinessByAdminIdNotFound() throws Exception {
        when(queryService.handle(any(GetBusinessByAdministratorIdQuery.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/businesses/by-administrator/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("business_not_found"));
    }
}
