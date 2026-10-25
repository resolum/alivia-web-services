package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.notifications.LoggingVerificationEmailService;
import com.resolum.alivia.iam.application.internal.outboundservices.notifications.UnavailableVerificationEmailService;
import com.resolum.alivia.iam.application.internal.outboundservices.notifications.VerificationEmailService;
import com.resolum.alivia.iam.infrastructure.notifications.smtp.SmtpVerificationEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies which {@link VerificationEmailService} implementation is wired for each
 * provider setting, so registration never fails on a missing email provider in local
 * development and never logs verification links in production.
 */
class VerificationEmailServiceWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LoggingVerificationEmailService.class,
                    UnavailableVerificationEmailService.class,
                    SmtpVerificationEmailService.class)
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withPropertyValues("alivia.iam.email-verification.from=no-reply@alivia.test");

    @Test
    void logsTheVerificationLinkByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(VerificationEmailService.class)
                .getBean(VerificationEmailService.class)
                .isInstanceOf(LoggingVerificationEmailService.class));
    }

    @Test
    void logsTheVerificationLinkInTheDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context)
                        .getBean(VerificationEmailService.class)
                        .isInstanceOf(LoggingVerificationEmailService.class));
    }

    @Test
    void failsClosedInTheProdProfileByDefault() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context)
                        .getBean(VerificationEmailService.class)
                        .isInstanceOf(UnavailableVerificationEmailService.class));
    }

    @Test
    void neverLogsVerificationLinksInTheProdProfile() {
        runner.withPropertyValues("spring.profiles.active=prod",
                        "alivia.iam.email-verification.provider=log")
                .run(context -> assertThat(context).doesNotHaveBean(VerificationEmailService.class));
    }

    @Test
    void usesSmtpWhenTheProviderIsExplicitlyConfigured() {
        runner.withPropertyValues("alivia.iam.email-verification.provider=smtp",
                        "spring.mail.host=localhost")
                .run(context -> assertThat(context)
                        .getBean(VerificationEmailService.class)
                        .isInstanceOf(SmtpVerificationEmailService.class));
    }

    @Test
    void failsFastWhenSmtpIsSelectedWithoutAMailHost() {
        runner.withPropertyValues("alivia.iam.email-verification.provider=smtp")
                .run(context -> assertThat(context).hasFailed());
    }
}
