package com.resolum.alivia.operations;

import com.resolum.alivia.operations.application.internal.outboundservices.storage.IncidentPhotoStorage;
import com.resolum.alivia.operations.domain.exceptions.PhotoUploadUnavailable;
import com.resolum.alivia.operations.domain.model.aggregates.Incident;
import com.resolum.alivia.operations.domain.model.commands.AttachEvidenceCommand;
import com.resolum.alivia.operations.domain.model.queries.GetIncidentByIdQuery;
import com.resolum.alivia.operations.domain.model.queries.GetReportableDevicesQuery;
import com.resolum.alivia.operations.domain.model.valueobjects.Evidence;
import com.resolum.alivia.operations.domain.model.valueobjects.IncidentSeverity;
import com.resolum.alivia.operations.domain.model.valueobjects.ReportableHome;
import com.resolum.alivia.operations.domain.services.AppointmentQueryService;
import com.resolum.alivia.operations.domain.services.IncidentCommandService;
import com.resolum.alivia.operations.domain.services.IncidentQueryService;
import com.resolum.alivia.operations.interfaces.rest.controllers.IncidentsController;
import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The family may add a photo to its report; without photo storage configured the report keeps working. */
class IncidentPhotoUploadTest {
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final UsernamePasswordAuthenticationToken FAMILY = new UsernamePasswordAuthenticationToken("10", null, List.of());
    private static final MockMultipartFile JPG = new MockMultipartFile("file", "sensor.jpg", "image/jpeg", new byte[]{1, 2, 3});

    private IncidentCommandService commands;
    private IncidentQueryService queries;
    private StorageService storage;
    private Incident incident;

    @BeforeEach
    void setUp() {
        commands = mock(IncidentCommandService.class);
        queries = mock(IncidentQueryService.class);
        storage = mock(StorageService.class);
        incident = new Incident("draft-1", "draft-1", 1L, 10L, "El sensor no responde.", IncidentSeverity.MEDIUM, NOW);
        ReflectionTestUtils.setField(incident, "id", 84L);
        when(queries.handle(new GetIncidentByIdQuery(84L))).thenReturn(Optional.of(incident));
        when(commands.handle(any(AttachEvidenceCommand.class))).thenAnswer(invocation -> {
            AttachEvidenceCommand command = invocation.getArgument(0);
            incident.attachEvidence(new Evidence(command.url(), command.publicId(), NOW));
            return incident;
        });
    }

    private MockMvc mvc(IncidentPhotoStorage photos) {
        return MockMvcBuilders.standaloneSetup(new IncidentsController(commands, queries, mock(AppointmentQueryService.class), photos))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void theFamilyAttachesAPhotoToItsReport() throws Exception {
        when(storage.upload(any(), eq("sensor.jpg")))
                .thenReturn(Map.of("url", "https://res.cloudinary.com/alivia/sensor.jpg", "publicId", "profiles/sensor"));

        mvc(new IncidentPhotoStorage(storage, true))
                .perform(multipart("/api/v1/support-incidents/84/evidences/photo").file(JPG).principal(FAMILY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidences[0].url").value("https://res.cloudinary.com/alivia/sensor.jpg"));
        verify(commands).handle(new AttachEvidenceCommand(84L, 10L, "https://res.cloudinary.com/alivia/sensor.jpg", "profiles/sensor"));
    }

    @Test
    void onlyASmallJpgOrPngOfTheFamilysOwnIncidentIsAccepted() throws Exception {
        var mvc = mvc(new IncidentPhotoStorage(storage, true));
        mvc.perform(multipart("/api/v1/support-incidents/84/evidences/photo")
                        .file(new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1})).principal(FAMILY))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/support-incidents/84/evidences/photo")
                        .file(new MockMultipartFile("file", "big.png", "image/png", new byte[2 * 1024 * 1024 + 1])).principal(FAMILY))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/support-incidents/84/evidences/photo").file(JPG)
                        .principal(new UsernamePasswordAuthenticationToken("11", null, List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/support-incidents/99/evidences/photo").file(JPG).principal(FAMILY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
        verify(commands, never()).handle(any(AttachEvidenceCommand.class));
    }

    @Test
    void withoutStorageTheUploadAnswersUnavailableAndTheFormHidesThePhoto() throws Exception {
        when(queries.handle(new GetReportableDevicesQuery(10L)))
                .thenReturn(Optional.of(new ReportableHome("draft-1", "Av. Del Parque Norte 742", "María", List.of())));
        var mvc = mvc(new IncidentPhotoStorage(storage, "CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET"));

        mvc.perform(get("/api/v1/support-incidents/devices").principal(FAMILY))
                .andExpect(jsonPath("$.photoUploadEnabled").value(false));
        mvc.perform(multipart("/api/v1/support-incidents/84/evidences/photo").file(JPG).principal(FAMILY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("photo_upload_unavailable"));
        verifyNoInteractions(storage);

        var plain = MockMvcBuilders.standaloneSetup(new IncidentsController(commands, queries, mock(AppointmentQueryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        plain.perform(get("/api/v1/support-incidents/devices").principal(FAMILY))
                .andExpect(jsonPath("$.photoUploadEnabled").value(false));
    }

    @Test
    void aStorageFailureOrAnIncompleteAnswerIsReportedAsUnavailable() {
        var photos = new IncidentPhotoStorage(storage, "demo", "123", "secret");
        assertTrue(photos.isEnabled());
        when(storage.upload(any(), any())).thenThrow(new RuntimeException("Cloudinary down"));
        assertThrows(PhotoUploadUnavailable.class, () -> photos.store(new byte[]{1}, "a.jpg"));
        doReturn(Map.of("secure_url", "https://x/a.jpg", "public_id", "a")).when(storage).upload(any(), any());
        assertEquals(new IncidentPhotoStorage.StoredPhoto("https://x/a.jpg", "a"), photos.store(new byte[]{1}, "a.jpg"));
        doReturn(Map.of("url", "https://x/a.jpg")).when(storage).upload(any(), any());
        assertThrows(PhotoUploadUnavailable.class, () -> photos.store(new byte[]{1}, "a.jpg"));
        doReturn(null).when(storage).upload(any(), any());
        assertThrows(PhotoUploadUnavailable.class, () -> photos.store(new byte[]{1}, "a.jpg"));

        assertFalse(new IncidentPhotoStorage(storage, "", "123", "secret").isEnabled());
        assertFalse(new IncidentPhotoStorage(storage, "demo", "${CLOUDINARY_API_KEY}", "secret").isEnabled());
        assertFalse(new IncidentPhotoStorage(storage, "demo", "123", null).isEnabled());
        assertFalse(new IncidentPhotoStorage(null, true).isEnabled());
    }
}
