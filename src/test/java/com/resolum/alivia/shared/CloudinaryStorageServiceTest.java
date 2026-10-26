package com.resolum.alivia.shared;


import com.resolum.alivia.shared.application.internal.outboundservices.storage.StorageService;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.CloudinaryStorageService;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.services.CloudinaryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryStorageServiceTest {

    @Test
    void cloudinaryStorageServiceExtendsStorageService() {
        assertTrue(CloudinaryStorageService.class.isInterface());
        assertTrue(StorageService.class.isAssignableFrom(CloudinaryStorageService.class));
    }

    @Test
    void cloudinaryServiceImplImplementsCloudinaryStorageService() {
        assertTrue(CloudinaryStorageService.class.isAssignableFrom(CloudinaryServiceImpl.class));
    }

    @Test
    void storageServiceDeclaresUploadAndDelete() throws NoSuchMethodException {
        var upload = StorageService.class.getMethod("upload", byte[].class, String.class);
        var delete = StorageService.class.getMethod("delete", String.class);

        assertEquals(Map.class, upload.getReturnType());
        assertEquals(void.class, delete.getReturnType());
    }
}