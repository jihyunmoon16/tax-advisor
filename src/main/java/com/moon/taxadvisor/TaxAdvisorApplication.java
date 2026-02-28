package com.moon.taxadvisor;

import com.moon.taxadvisor.config.GeminiProperties;
import com.moon.taxadvisor.config.NanoBananaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GeminiProperties.class, NanoBananaProperties.class})
public class TaxAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxAdvisorApplication.class, args);
    }

}
