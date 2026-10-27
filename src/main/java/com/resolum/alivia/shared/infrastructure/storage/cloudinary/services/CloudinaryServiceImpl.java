package com.resolum.alivia.shared.infrastructure.storage.cloudinary.services;


import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.CloudinaryStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
@Service
public class CloudinaryServiceImpl implements CloudinaryStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryServiceImpl(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public Map<String, String> upload(byte[] fileData, String fileName) {
        try {
            String uniqueFileName = generateUniqueFileName(fileName);

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "public_id", uniqueFileName,
                    "resource_type", "auto",
                    "folder", "profiles"
            );

            Map result = cloudinary.uploader().upload(fileData, uploadParams);

            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            response.put("publicId", publicId);

            return response;

        } catch (IOException e) {
            throw new RuntimeException("Error uploading file to Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException("Error deleting file from Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un nombre único para el archivo
     * Combina el nombre original (sin extensión) con timestamp
     */
    private String generateUniqueFileName(String originalFileName) {
        String nameWithoutExtension = removeExtension(originalFileName);
        long timestamp = System.currentTimeMillis();
        return nameWithoutExtension + "_" + timestamp;
    }

    private String removeExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "file";
        }

        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex == -1) {
            return fileName;
        }

        return fileName.substring(0, lastDotIndex);
    }
}