package com.example.house_moving_service.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {
    private String provider;

    // Thời hạn của URL xem / tải file (phút). 
    private int urlTtlMinutes = 15;

    private Minio minio = new Minio();

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "house-moving";
    }
}
