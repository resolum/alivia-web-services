package com.resolum.alivia.communications.infrastructure.push.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.resolum.alivia.communications.application.internal.outboundservices.push.PushNotificationSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies which push sender is wired for each provider setting. */
class PushNotificationSenderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LoggingPushNotificationSender.class,
                    FirebaseConfiguration.class,
                    FirebasePushNotificationSender.class);

    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    void logsPushNotificationsByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(PushNotificationSender.class)
                .doesNotHaveBean(FirebaseMessaging.class)
                .getBean(PushNotificationSender.class)
                .isInstanceOf(LoggingPushNotificationSender.class));
    }

    @Test
    void sendsThroughFirebaseWhenItIsTheProvider() throws Exception {
        runner.withPropertyValues("alivia.communications.push.provider=firebase",
                        "alivia.communications.push.firebase.credentials-json=" + serviceAccountJson())
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(PushNotificationSender.class)
                            .getBean(PushNotificationSender.class)
                            .isInstanceOf(FirebasePushNotificationSender.class);
                    assertThat(context.getBean(FirebaseApp.class).getName()).isEqualTo(FirebaseConfiguration.APP_NAME);
                });
    }

    @Test
    void reusesTheFirebaseAppWhenTheContextRestarts() throws Exception {
        FirebaseConfiguration configuration = new FirebaseConfiguration();
        String json = serviceAccountJson();

        FirebaseApp first = configuration.firebaseApp(json, "");
        FirebaseApp second = configuration.firebaseApp(json, "");

        assertThat(second).isSameAs(first);
    }

    @Test
    void failsFastWhenTheServiceAccountFileIsMissing() {
        runner.withPropertyValues("alivia.communications.push.provider=firebase",
                        "alivia.communications.push.firebase.credentials-path=/does/not/exist.json")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void readsTheServiceAccountFromAFile(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("service-account.json");
        Files.writeString(file, serviceAccountJson());

        assertThat(FirebaseConfiguration.loadCredentials(" ", file.toString())).isNotNull();
    }

    @Test
    void acceptsTheServiceAccountEncodedInBase64() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(serviceAccountJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(FirebaseConfiguration.loadCredentials(encoded, null)).isNotNull();
    }

    @Test
    void rejectsAServiceAccountThatIsNotJson() {
        assertThatThrownBy(() -> FirebaseConfiguration.loadCredentials("not json", null))
                .isInstanceOf(UncheckedIOException.class);
    }

    static String serviceAccountJson() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "{\"type\":\"service_account\",\"project_id\":\"alivia-test\","
                + "\"private_key_id\":\"test\","
                + "\"private_key\":\"-----BEGIN PRIVATE KEY-----\\n" + key + "\\n-----END PRIVATE KEY-----\\n\","
                + "\"client_email\":\"push@alivia-test.iam.gserviceaccount.com\","
                + "\"client_id\":\"1\",\"token_uri\":\"https://oauth2.googleapis.com/token\"}";
    }
}
