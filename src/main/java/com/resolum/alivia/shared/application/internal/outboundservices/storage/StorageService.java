package com.resolum.alivia.shared.application.internal.outboundservices.storage;


import java.util.Map;

public interface StorageService {
    Map<String, String> upload(byte[] fileData, String fileName);
    void delete(String publicId);
}