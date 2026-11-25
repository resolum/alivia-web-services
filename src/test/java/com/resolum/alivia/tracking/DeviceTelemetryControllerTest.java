package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.domain.exceptions.DeviceNotAccessible;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.DeviceTelemetryPageView;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.services.DeviceTelemetryQueryService;
import com.resolum.alivia.tracking.interfaces.rest.controllers.DeviceTelemetryController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /devices/{deviceId}/telemetry}: the shape of the answer against {@code q-device-telemetry.response.json}, and
 * who may call it under the real method security.
 */
class DeviceTelemetryControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DeviceTelemetryQueryService service = mock(DeviceTelemetryQueryService.class);

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> metrics(int rssi, int heap) {
        var values = new LinkedHashMap<String, Object>();
        values.put("rssi", rssi);
        values.put("freeHeap", heap);
        return values;
    }

    private DeviceTelemetryPageView page() {
        return new DeviceTelemetryPageView(10L, Instant.parse("2026-11-24T00:00:00Z"), Instant.parse("2026-11-25T00:00:00Z"),
                0, 2, 3, List.of(
                new TelemetrySample(10L, 3L, 30L, Instant.parse("2026-11-24T14:58:00Z"), new EventAttributes(metrics(-55, 180000)), null),
                new TelemetrySample(10L, 3L, 30L, Instant.parse("2026-11-24T14:53:00Z"), new EventAttributes(metrics(-61, 179200)), null)));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DeviceTelemetryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void thePageIsAnsweredAsTheContractSays() throws Exception {
        when(service.handle(any())).thenReturn(page());

        var answer = mvc().perform(get("/api/v1/devices/10/telemetry").param("from", "2026-11-24T00:00:00Z")
                        .param("to", "2026-11-25T00:00:00Z").param("size", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var expected = JSON.readTree(Files.readString(Path.of("docs", "fixtures", "tracking", "q-device-telemetry.response.json")));
        assertThat(JSON.readTree(answer)).isEqualTo(expected);
    }

    @Test
    void aDeviceNotPlacedInAHomeIsA404() throws Exception {
        when(service.handle(any())).thenThrow(new DeviceNotAccessible(10L));

        mvc().perform(get("/api/v1/devices/10/telemetry")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("device_not_found"));
    }

    @Test
    void aBadPeriodIsA400WithItsCode() throws Exception {
        when(service.handle(any())).thenThrow(
                new com.resolum.alivia.tracking.domain.exceptions.InvalidHistoryQuery("range_too_large", "too long"));

        mvc().perform(get("/api/v1/devices/10/telemetry")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("range_too_large"));
    }

    // ---- who may call it, under the real method security

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean
        DeviceTelemetryController controller(DeviceTelemetryQueryService service) {
            return new DeviceTelemetryController(service);
        }

        @Bean
        DeviceTelemetryQueryService service() {
            return mock(DeviceTelemetryQueryService.class);
        }
    }

    private void callAs(String... roles) {
        var authorities = AuthorityUtils.createAuthorityList(java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null, authorities));
    }

    @Test
    void onlyAnAdministratorOrATechnicianMayAskForTheTelemetry() {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var controller = context.getBean(DeviceTelemetryController.class);
            var queries = context.getBean(DeviceTelemetryQueryService.class);
            when(queries.handle(any())).thenReturn(page());

            callAs("ADMINISTRATOR");
            assertThat(controller.telemetry(10L, null, null, null, null).getStatusCode().value()).isEqualTo(200);
            callAs("TECHNICIAN");
            assertThat(controller.telemetry(10L, null, null, null, null).getStatusCode().value()).isEqualTo(200);
        }
    }

    @Test
    void everyOtherAccountIsRefusedBeforeAnythingIsRead() {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var controller = context.getBean(DeviceTelemetryController.class);
            var queries = context.getBean(DeviceTelemetryQueryService.class);

            for (var role : List.of("CAREGIVER", "RELATIVE", "SUBSCRIPTION_MANAGER", "EDGE_MICROPHONE", "EDGE_ACTUATOR")) {
                callAs(role);
                assertThatThrownBy(() -> controller.telemetry(10L, null, null, null, null))
                        .as(role).isInstanceOf(AccessDeniedException.class);
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null, List.of()));
            assertThatThrownBy(() -> controller.telemetry(10L, null, null, null, null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(queries);
        }
    }
}
