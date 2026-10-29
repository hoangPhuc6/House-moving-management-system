package com.example.house_moving_service.common.storage;

import java.io.InputStream;
import java.time.Duration;


public interface FileStorageService {

    StoredFile upload(String key, InputStream data, long size, String contentType);

    String generateAccessUrl(String key, Duration ttl);

    void delete(String key);
}
