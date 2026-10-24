package com.resolum.alivia.shared;


import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.CloudinaryStorageService;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.configuration.CloudinaryConfig;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.services.CloudinaryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración contra Cloudinary REAL (sube y borra archivos de verdad).
 * <p>
 * Toma las credenciales de application-dev.properties (cloudinary.cloud-name, cloudinary.api-key,
 * cloudinary.api-secret). Se ejecuta solo si esas tres propiedades tienen valores reales: ni vacías,
 * ni el texto de relleno (CLOUDINARY_API_KEY...), ni un ${VAR} cuya variable de entorno no exista.
 * Si no, JUnit la omite y el build no falla.
 * <p>
 * Todo lo que sube lo borra al terminar cada test (carpeta "profiles").
 */
@EnabledIf("credentialsAvailable")
@SpringJUnitConfig(classes = {CloudinaryConfig.class, CloudinaryServiceImpl.class})
@TestPropertySource(locations = "classpath:application-dev.properties")
class CloudinaryServiceImplIntegrationTest {

    private static final String DEV_PROPERTIES = "/application-dev.properties";

    static boolean credentialsAvailable() {
        var props = new Properties();
        try (var in = CloudinaryServiceImplIntegrationTest.class.getResourceAsStream(DEV_PROPERTIES)) {
            if (in == null) {
                return false;
            }
            props.load(in);
        } catch (IOException e) {
            return false;
        }
        return isRealValue(props.getProperty("cloudinary.cloud-name"), "CLOUDINARY_CLOUD_NAME")
                && isRealValue(props.getProperty("cloudinary.api-key"), "CLOUDINARY_API_KEY")
                && isRealValue(props.getProperty("cloudinary.api-secret"), "CLOUDINARY_API_SECRET");
    }

    private static boolean isRealValue(String value, String placeholderName) {
        if (value == null || value.isBlank() || value.equals(placeholderName)) {
            return false;
        }
        if (value.startsWith("${")) {
            // ${VAR} o ${VAR:default}: es real si la variable existe o trae valor por defecto
            String expression = value.substring(2, value.length() - 1);
            int colon = expression.indexOf(':');
            String envName = colon == -1 ? expression : expression.substring(0, colon);
            boolean hasDefault = colon != -1 && colon < expression.length() - 1;
            return System.getenv(envName) != null || hasDefault;
        }
        return true;
    }

    /** PNG válido de 1x1 píxel. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    private CloudinaryStorageService storage;

    @Autowired
    private Cloudinary cloudinary;

    private final List<String> uploadedPublicIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // delete() es idempotente: borrar algo que ya no existe no falla.
        uploadedPublicIds.forEach(storage::delete);
        uploadedPublicIds.clear();
    }

    private Map<String, String> upload(String fileName) {
        Map<String, String> result = storage.upload(PNG, fileName);
        uploadedPublicIds.add(result.get("publicId"));
        return result;
    }

    // ---------------------------------------------------------------- subir

    @Test
    void uploadReturnsSecureUrlAndPublicId() {
        var result = upload("it-avatar.png");

        assertNotNull(result.get("url"));
        assertNotNull(result.get("publicId"));
        assertTrue(result.get("url").startsWith("https://res.cloudinary.com/"));
        assertTrue(result.get("publicId").startsWith("profiles/it-avatar_"));
    }

    @Test
    void uploadedFileExistsInCloudinary() throws Exception {
        var result = upload("it-exists.png");

        Map<?, ?> resource = cloudinary.api().resource(result.get("publicId"), ObjectUtils.emptyMap());

        assertEquals(result.get("publicId"), resource.get("public_id"));
        assertEquals("png", resource.get("format"));
    }

    @Test
    void uploadedFileIsPubliclyReachable() throws Exception {
        var result = upload("it-public.png");
        var request = HttpRequest.newBuilder(URI.create(result.get("url"))).GET().build();

        try (var client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            assertEquals(200, response.statusCode());
        }
    }

    @Test
    void uploadWithoutExtensionKeepsTheName() {
        var result = upload("it-sin-extension");

        assertTrue(result.get("publicId").startsWith("profiles/it-sin-extension_"));
    }

    @Test
    void uploadWithNullFileNameUsesDefaultName() {
        var result = upload(null);

        assertTrue(result.get("publicId").startsWith("profiles/file_"));
    }

    @SuppressWarnings("java:S2925") // el sufijo del servicio es currentTimeMillis(): hay que separar las subidas
    @Test
    void uploadingSameFileNameTwiceGeneratesDifferentPublicIds() throws InterruptedException {
        var first = upload("it-repetido.png");
        Thread.sleep(5); // el sufijo es System.currentTimeMillis()
        var second = upload("it-repetido.png");

        assertNotEquals(first.get("publicId"), second.get("publicId"));
        assertNotEquals(first.get("url"), second.get("url"));
    }

    // ---------------------------------------------------------------- borrar

    @Test
    void deleteRemovesTheFileFromCloudinary() throws Exception {
        var publicId = upload("it-delete.png").get("publicId");
        var api = cloudinary.api();
        var options = ObjectUtils.emptyMap();
        assertEquals(publicId, api.resource(publicId, options).get("public_id"));

        storage.delete(publicId);

        assertThrows(NotFound.class, () -> api.resource(publicId, options));
    }

    @Test
    void deleteOfUnknownPublicIdDoesNotThrow() {
        var unknownPublicId = "profiles/no-existe-" + System.currentTimeMillis();

        assertDoesNotThrow(() -> storage.delete(unknownPublicId));
    }
}