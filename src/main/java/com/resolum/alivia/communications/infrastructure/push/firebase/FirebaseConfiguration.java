package com.resolum.alivia.communications.infrastructure.push.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Starts the Firebase Admin SDK when Firebase Cloud Messaging is the push provider.
 *
 * <p>The service account is read from {@code alivia.communications.push.firebase.credentials-json} (JSON or Base64),
 * then from {@code credentials-path}; with neither, the Google application default credentials
 * are used.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "alivia.communications.push", name = "provider", havingValue = "firebase")
public class FirebaseConfiguration {

    static final String APP_NAME = "alivia";

    @Bean
    FirebaseApp firebaseApp(
            @Value("${alivia.communications.push.firebase.credentials-json:}") String credentialsJson,
            @Value("${alivia.communications.push.firebase.credentials-path:}") String credentialsPath) {
        GoogleCredentials credentials = loadCredentials(credentialsJson, credentialsPath);
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        return FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    /** The service account as JSON, or the same JSON encoded in Base64 (easier to keep in one line). */
    static byte[] decode(String credentials) {
        String value = credentials.trim();
        if (value.startsWith("{")) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    static GoogleCredentials loadCredentials(String credentialsJson, String credentialsPath) {
        try {
            if (credentialsJson != null && !credentialsJson.isBlank()) {
                try (InputStream in = new ByteArrayInputStream(decode(credentialsJson))) {
                    return GoogleCredentials.fromStream(in);
                }
            }
            if (credentialsPath != null && !credentialsPath.isBlank()) {
                try (InputStream in = new FileInputStream(credentialsPath)) {
                    return GoogleCredentials.fromStream(in);
                }
            }
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read the Firebase service account; set FIREBASE_CREDENTIALS_PATH or FIREBASE_CREDENTIALS_JSON.", e);
        }
    }
}
