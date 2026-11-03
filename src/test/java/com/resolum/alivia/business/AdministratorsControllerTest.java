package com.resolum.alivia.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BackupPersonalEmailAlreadyExists;
import com.resolum.alivia.business.domain.model.aggregates.Administrator;
import com.resolum.alivia.business.domain.model.commands.CreateAdministratorCommand;
import com.resolum.alivia.business.domain.model.commands.UpdateAdministratorCommand;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByIdQuery;
import com.resolum.alivia.business.domain.model.queries.GetAdministratorByUserIdQuery;
import com.resolum.alivia.business.domain.services.AdministratorCommandService;
import com.resolum.alivia.business.domain.services.AdministratorQueryService;
import com.resolum.alivia.business.interfaces.rest.controllers.AdministratorsController;
import com.resolum.alivia.business.interfaces.rest.resources.CreateAdministratorResource;
import com.resolum.alivia.business.interfaces.rest.resources.UpdateAdministratorResource;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdministratorsController Tests")
class AdministratorsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdministratorCommandService commandService;

    @Mock
    private AdministratorQueryService queryService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        var controller = new AdministratorsController(commandService, queryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Administrator createAdministratorEntity(Long id, Long userId, Long staffProfileId, String backupEmail) {
        Administrator admin = new Administrator(new UserId(userId), new StaffProfileId(staffProfileId), new EmailAddress(backupEmail));
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    @Test
    @DisplayName("POST /api/v1/administrators returns 201 Created on success")
    void createAdministratorSuccess() throws Exception {
        var resource = new CreateAdministratorResource(
                "admin@alivia.com",
                "Password123!",
                null,
                null,
                "Carlos",
                "Mendoza",
                "12345678",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "backup@gmail.com"
        );

        when(commandService.handle(any(CreateAdministratorCommand.class))).thenReturn(1L);
        when(queryService.handle(any(GetAdministratorByIdQuery.class)))
                .thenReturn(Optional.of(createAdministratorEntity(1L, 10L, 20L, "backup@gmail.com")));

        mockMvc.perform(post("/api/v1/administrators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.staffProfileId").value(20))
                .andExpect(jsonPath("$.backupPersonalEmail").value("backup@gmail.com"));
    }

    @Test
    @DisplayName("POST /api/v1/administrators returns 409 Conflict when backup email already exists")
    void createAdministratorDuplicateBackupEmail() throws Exception {
        var resource = new CreateAdministratorResource(
                "admin@alivia.com",
                "Password123!",
                null,
                null,
                "Carlos",
                "Mendoza",
                "12345678",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "backup@gmail.com"
        );

        when(commandService.handle(any(CreateAdministratorCommand.class)))
                .thenThrow(new BackupPersonalEmailAlreadyExists("backup@gmail.com"));

        mockMvc.perform(post("/api/v1/administrators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("backup_personal_email_already_exists"));
    }

    @Test
    @DisplayName("PUT /api/v1/administrators/{id} returns 200 OK on success")
    void updateAdministratorSuccess() throws Exception {
        var resource = new UpdateAdministratorResource(
                null,
                null,
                "Carlos Alberto",
                "Mendoza Ruiz",
                LocalDate.of(1985, 5, 20),
                "51987654321",
                "newbackup@gmail.com"
        );

        when(commandService.handle(any(UpdateAdministratorCommand.class))).thenReturn(1L);
        when(queryService.handle(any(GetAdministratorByIdQuery.class)))
                .thenReturn(Optional.of(createAdministratorEntity(1L, 10L, 20L, "newbackup@gmail.com")));

        mockMvc.perform(put("/api/v1/administrators/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.backupPersonalEmail").value("newbackup@gmail.com"));
    }

    @Test
    @DisplayName("GET /api/v1/administrators/{id} returns 200 OK when found")
    void getAdministratorByIdFound() throws Exception {
        when(queryService.handle(any(GetAdministratorByIdQuery.class)))
                .thenReturn(Optional.of(createAdministratorEntity(1L, 10L, 20L, "backup@gmail.com")));

        mockMvc.perform(get("/api/v1/administrators/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/administrators/{id} returns 404 when not found")
    void getAdministratorByIdNotFound() throws Exception {
        when(queryService.handle(any(GetAdministratorByIdQuery.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/administrators/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("administrator_not_found"));
    }

    @Test
    @DisplayName("GET /api/v1/administrators/by-user/{userId} returns 200 OK when found")
    void getAdministratorByUserIdFound() throws Exception {
        when(queryService.handle(any(GetAdministratorByUserIdQuery.class)))
                .thenReturn(Optional.of(createAdministratorEntity(1L, 10L, 20L, "backup@gmail.com")));

        mockMvc.perform(get("/api/v1/administrators/by-user/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/administrators/by-user/{userId} returns 404 when not found")
    void getAdministratorByUserIdNotFound() throws Exception {
        when(queryService.handle(any(GetAdministratorByUserIdQuery.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/administrators/by-user/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("administrator_not_found"));
    }
}
