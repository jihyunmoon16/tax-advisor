package com.moon.taxadvisor.client.gemini;

import com.moon.taxadvisor.client.gemini.GeminiModels.Content;
import com.moon.taxadvisor.client.gemini.GeminiModels.FunctionCallingConfig;
import com.moon.taxadvisor.client.gemini.GeminiModels.GenerateContentRequest;
import com.moon.taxadvisor.client.gemini.GeminiModels.GenerateContentResponse;
import com.moon.taxadvisor.client.gemini.GeminiModels.Tool;
import com.moon.taxadvisor.client.gemini.GeminiModels.ToolConfig;
import com.moon.taxadvisor.config.GeminiProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final GeminiProperties geminiProperties;

    public boolean isConfigured() {
        return geminiProperties.getApiKey() != null && !geminiProperties.getApiKey().isBlank();
    }

    public GenerateContentResponse generateContent(
            List<Content> conversation,
            List<Tool> tools,
            String systemPrompt
    ) {
        WebClient webClient = webClientBuilder.baseUrl(geminiProperties.getBaseUrl()).build();

        GenerateContentRequest request = new GenerateContentRequest(
                conversation,
                GeminiModels.systemText(systemPrompt),
                tools,
                new ToolConfig(new FunctionCallingConfig("AUTO"))
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", geminiProperties.getApiKey())
                        .build(geminiProperties.getModel()))
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new IllegalStateException("Gemini API error: " + body))))
                .bodyToMono(GenerateContentResponse.class)
                .block();
    }
}
