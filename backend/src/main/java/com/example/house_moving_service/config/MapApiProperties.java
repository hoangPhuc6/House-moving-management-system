package com.example.house_moving_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.map")
@Getter
@Setter
public class MapApiProperties {

    private boolean enabled = false;
    private String provider = "GOONG";
    private String apiKey = "";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;
}

