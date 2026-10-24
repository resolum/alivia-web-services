package com.resolum.alivia.shared;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.services.CloudinaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    private static final byte[] DATA = {1, 2, 3};

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    @Captor
    private ArgumentCaptor<Map<String, Object>> paramsCaptor;

    private CloudinaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryServiceImpl(cloudinary);
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    private String capturedPublicId() throws IOException {
        verify(uploader).upload(eq(DATA), paramsCaptor.capture());
        return paramsCaptor.getValue().get("public_id").toString();
    }

    @Test
    void uploadReturnsUrlAndPublicId() throws IOException {
        when(uploader.upload(any(), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/x.png",
                        "public_id", "profiles/avatar_1"));

        var result = service.upload(DATA, "avatar.png");

        assertEquals("https://res.cloudinary.com/x.png", result.get("url"));
        assertEquals("profiles/avatar_1", result.get("publicId"));
    }

    @Test
    void uploadSendsExpectedParams() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of());

        service.upload(DATA, "avatar.png");

        verify(uploader).upload(eq(DATA), paramsCaptor.capture());
        var params = paramsCaptor.getValue();
        assertEquals("profiles", params.get("folder"));
        assertEquals("auto", params.get("resource_type"));
        assertTrue(params.get("public_id").toString().matches("avatar_\\d+"));
    }

    @Test
    void uploadKeepsNameWhenThereIsNoExtension() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of());

        service.upload(DATA, "sin-extension");

        assertTrue(capturedPublicId().matches("sin-extension_\\d+"));
    }

    @Test
    void uploadOnlyRemovesTheLastExtension() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of());

        service.upload(DATA, "foto.perfil.png");

        assertTrue(capturedPublicId().matches("foto\\.perfil_\\d+"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void uploadUsesDefaultNameWhenFileNameIsMissing(String fileName) throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of());

        service.upload(DATA, fileName);

        assertTrue(capturedPublicId().matches("file_\\d+"));
    }

    @Test
    void uploadWrapsIOException() throws IOException {
        var cause = new IOException("boom");
        when(uploader.upload(any(), anyMap())).thenThrow(cause);

        var ex = assertThrows(RuntimeException.class, () -> service.upload(DATA, "a.png"));

        assertTrue(ex.getMessage().contains("Error uploading file to Cloudinary"));
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void deleteCallsDestroyWithPublicId() throws IOException {
        service.delete("profiles/a_1");

        verify(uploader).destroy(eq("profiles/a_1"), anyMap());
    }

    @Test
    void deleteWrapsIOException() throws IOException {
        when(uploader.destroy(any(), anyMap())).thenThrow(new IOException("boom"));

        var ex = assertThrows(RuntimeException.class, () -> service.delete("profiles/a_1"));

        assertTrue(ex.getMessage().contains("Error deleting file from Cloudinary"));
        assertInstanceOf(IOException.class, ex.getCause());
    }
}