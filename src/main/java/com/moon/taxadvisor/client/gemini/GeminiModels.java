package com.moon.taxadvisor.client.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GeminiModels {

    private GeminiModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateContentRequest(
            List<Content> contents,
            Content systemInstruction,
            List<Tool> tools,
            ToolConfig toolConfig
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(String role, List<Part> parts) {
        public List<FunctionCall> functionCalls() {
            if (parts == null) {
                return Collections.emptyList();
            }
            return parts.stream()
                    .map(Part::functionCall)
                    .filter(Objects::nonNull)
                    .toList();
        }

        public String joinedText() {
            if (parts == null) {
                return "";
            }
            return parts.stream()
                    .map(Part::text)
                    .filter(Objects::nonNull)
                    .reduce("", (a, b) -> a + (a.isBlank() ? "" : "\n") + b)
                    .trim();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, FunctionCall functionCall, FunctionResponse functionResponse, String thoughtSignature) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCall(String name, Map<String, Object> args) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionResponse(String name, Map<String, Object> response) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(List<FunctionDeclaration> functionDeclarations) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDeclaration(String name, String description, Map<String, Object> parameters) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolConfig(FunctionCallingConfig functionCallingConfig) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCallingConfig(String mode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateContentResponse(List<Candidate> candidates) {
        public Optional<Candidate> firstCandidate() {
            return candidates == null || candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {
    }

    public static Content userText(String text) {
        return new Content("user", List.of(new Part(text, null, null, null)));
    }

    public static Content systemText(String text) {
        return new Content(null, List.of(new Part(text, null, null, null)));
    }

    public static Content userFunctionResponse(String functionName, Map<String, Object> payload) {
        return new Content(
                "user",
                List.of(new Part(null, null, new FunctionResponse(functionName, payload), null))
        );
    }
}
