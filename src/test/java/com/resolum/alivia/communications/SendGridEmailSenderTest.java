package com.resolum.alivia.communications;

import com.resolum.alivia.communications.infrastructure.email.sendgrid.SendGridEmailSender;
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

class SendGridEmailSenderTest {
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsThePlainAndHtmlBodiesThroughTheMailSendApi() {
        var sender = new SendGridEmailSender(builder, "SG.test-key", "equipo.alivia@gmail.com", "Alivia");
        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer SG.test-key"))
                .andExpect(jsonPath("$.personalizations[0].to[0].email").value("ana@alivia.pe"))
                .andExpect(jsonPath("$.from.email").value("equipo.alivia@gmail.com"))
                .andExpect(jsonPath("$.subject").value("Bienvenido"))
                .andExpect(jsonPath("$.content[0].type").value("text/plain"))
                .andExpect(jsonPath("$.content[1].value").value("<p>Hola</p>"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON));

        sender.send("ana@alivia.pe", "Bienvenido", "Hola", "<p>Hola</p>");

        server.verify();
    }

    @Test
    void reportsRejectedEmails() {
        var sender = new SendGridEmailSender(builder, "SG.test-key", "equipo.alivia@gmail.com", "Alivia");
        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(IllegalStateException.class, () -> sender.send("ana@alivia.pe", "Hola", "Hola", "<p>Hola</p>"));
    }

    @Test
    void requiresTheApiKeyAndTheSender() {
        assertThrows(IllegalArgumentException.class, () -> new SendGridEmailSender(builder, " ", "a@b.c", "Alivia"));
        assertThrows(IllegalArgumentException.class, () -> new SendGridEmailSender(builder, "SG.key", "", "Alivia"));
    }
}
