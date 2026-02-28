package com.moon.taxadvisor.client.nanobanana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.moon.taxadvisor.config.NanoBananaProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class NanoBananaClient {

    private static final List<String> IMAGE_MODALITY = List.of("IMAGE");
    private static final int MAX_IN_MEMORY_IMAGE_RESPONSE_BYTES = 50 * 1024 * 1024;

    private final WebClient.Builder webClientBuilder;
    private final NanoBananaProperties nanoBananaProperties;

    public boolean isConfigured() {
        return nanoBananaProperties.getApiKey() != null && !nanoBananaProperties.getApiKey().isBlank();
    }

    public Mono<String> generateImageBase64(String prompt) {
        if (!isConfigured()) {
            return Mono.empty();
        }

        WebClient webClient = webClientBuilder
                .baseUrl(nanoBananaProperties.getBaseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_IMAGE_RESPONSE_BYTES))
                .build();
        GenerateImageRequest request = new GenerateImageRequest(
                List.of(new RequestContent(List.of(new RequestPart(prompt)))),
                new GenerationConfig(IMAGE_MODALITY)
        );

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", nanoBananaProperties.getApiKey())
                        .build(nanoBananaProperties.getModel()))
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new IllegalStateException("Nano Banana API error: " + body))))
                .bodyToMono(GenerateImageResponse.class)
                .flatMap(this::extractImageAsDataUri)
                .timeout(Duration.ofSeconds(nanoBananaProperties.getTimeoutSeconds()));
    }

    private Mono<String> extractImageAsDataUri(GenerateImageResponse response) {
        return Flux.fromIterable(Optional.ofNullable(response.candidates()).orElse(List.of()))
                .flatMap(candidate -> Flux.fromIterable(Optional.ofNullable(candidate.content())
                        .map(ResponseContent::parts)
                        .orElse(List.of())))
                .flatMap(part -> Mono.justOrEmpty(part.inlineData())
                        .flatMap(this::toDataUri))
                .next();
    }

    private Mono<String> toDataUri(InlineData inlineData) {
        return switch (inlineData) {
            case InlineData(String mimeType, String data) when data != null && !data.isBlank() -> {
                String effectiveMimeType = switch (mimeType) {
                    case null -> "image/png";
                    case "" -> "image/png";
                    default -> mimeType;
                };
                yield Mono.just("data:" + effectiveMimeType + ";base64," + data);
            }
            case InlineData ignored -> Mono.empty();
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerateImageRequest(
            List<RequestContent> contents,
            GenerationConfig generationConfig
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record RequestContent(List<RequestPart> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record RequestPart(String text) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(List<String> responseModalities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateImageResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(ResponseContent content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseContent(List<ResponsePart> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponsePart(InlineData inlineData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InlineData(String mimeType, String data) {
    }
}
