package com.resolum.alivia.communications;

import com.resolum.alivia.communications.application.internal.outboundservices.email.EmailSender;
import com.resolum.alivia.communications.application.internal.outboundservices.email.LoggingEmailSender;
import com.resolum.alivia.communications.application.internal.outboundservices.email.UnavailableEmailSender;
import com.resolum.alivia.communications.infrastructure.email.sendgrid.SendGridEmailSender;
import com.resolum.alivia.communications.infrastructure.email.smtp.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies which {@link EmailSender} implementation is wired for each provider setting,
 * so local development never fails on a missing provider and production never logs
 * email bodies.
 */
class EmailSenderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LoggingEmailSender.class,
                    UnavailableEmailSender.class,
                    SmtpEmailSender.class,
                    SendGridEmailSender.class)
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withPropertyValues("alivia.communications.email.from=no-reply@alivia.test");

    @Test
    void logsTheEmailByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(EmailSender.class)
                .getBean(EmailSender.class)
                .isInstanceOf(LoggingEmailSender.class));
    }

    @Test
    void logsTheEmailInTheDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context)
                        .getBean(EmailSender.class)
                        .isInstanceOf(LoggingEmailSender.class));
    }

    @Test
    void failsClosedInTheProdProfileByDefault() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context)
                        .getBean(EmailSender.class)
                        .isInstanceOf(UnavailableEmailSender.class));
    }

    @Test
    void neverLogsEmailsInTheProdProfile() {
        runner.withPropertyValues("spring.profiles.active=prod",
                        "alivia.communications.email.provider=log")
                .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }

    @Test
    void usesSmtpWhenTheProviderIsExplicitlyConfigured() {
        runner.withPropertyValues("alivia.communications.email.provider=smtp",
                        "spring.mail.host=localhost")
                .run(context -> assertThat(context)
                        .getBean(EmailSender.class)
                        .isInstanceOf(SmtpEmailSender.class));
    }

    @Test
    void failsFastWhenSmtpIsSelectedWithoutAMailHost() {
        runner.withPropertyValues("alivia.communications.email.provider=smtp")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sendsThroughSendGridWhenItIsTheProvider() {
        runner.withPropertyValues("alivia.communications.email.provider=sendgrid",
                        "alivia.communications.email.sendgrid.api-key=SG.test-key")
                .run(context -> assertThat(context)
                        .getBean(EmailSender.class)
                        .isInstanceOf(SendGridEmailSender.class));
    }
}
