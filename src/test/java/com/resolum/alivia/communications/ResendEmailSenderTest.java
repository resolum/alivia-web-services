package com.resolum.alivia.communications;

import com.resolum.alivia.communications.infrastructure.email.resend.ResendEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ResendEmailSenderTest {
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsThePlainAndHtmlBodiesThroughTheEmailApi() {
        var sender = new ResendEmailSender(builder, "re_test-key", "onboarding@resend.dev", "Alivia");
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test-key"))
                .andExpect(jsonPath("$.from").value("Alivia <onboarding@resend.dev>"))
                .andExpect(jsonPath("$.to[0]").value("ana@alivia.pe"))
                .andExpect(jsonPath("$.subject").value("Bienvenido"))
                .andExpect(jsonPath("$.text").value("Hola"))
                .andExpect(jsonPath("$.html").value("<p>Hola</p>"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON));

        sender.send("ana@alivia.pe", "Bienvenido", "Hola", "<p>Hola</p>");

        server.verify();
    }

    @Test
    void reportsRejectedEmails() {
        var sender = new ResendEmailSender(builder, "re_test-key", "onboarding@resend.dev", "Alivia");
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(IllegalStateException.class, () -> sender.send("ana@alivia.pe", "Hola", "Hola", "<p>Hola</p>"));
    }

    @Test
    void requiresTheApiKeyAndTheSender() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResendEmailSender(builder, " ", "onboarding@resend.dev", "Alivia"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResendEmailSender(builder, "re_key", "", "Alivia"));
    }
}
