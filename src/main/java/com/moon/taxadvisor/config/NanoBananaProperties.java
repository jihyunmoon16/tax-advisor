package com.moon.taxadvisor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "nano-banana")
public class NanoBananaProperties {
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiKey = "";
    private String model = "gemini-3.1-flash-image-preview";
    private int timeoutSeconds = 60;
}
