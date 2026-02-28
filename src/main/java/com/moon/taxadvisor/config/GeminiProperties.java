package com.moon.taxadvisor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiKey = "";
    private String model = "gemini-3-flash-preview";
    private int maxIterations = 6;
}
