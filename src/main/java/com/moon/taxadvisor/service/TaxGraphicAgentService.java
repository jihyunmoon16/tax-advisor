package com.moon.taxadvisor.service;

import com.moon.taxadvisor.client.nanobanana.NanoBananaClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxGraphicAgentService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(70);

    private final NanoBananaClient nanoBananaClient;

    public String createInfographic(String originalQuestion, String primaryStrategy, String auditReview) {
        log.info("Agent 3 (Designer): 인포그래픽 이미지 생성 요청 중...");

        if (!nanoBananaClient.isConfigured()) {
            log.warn("Agent 3 (Designer): Nano Banana API Key 미설정으로 이미지 생성을 건너뜁니다.");
            return "";
        }

        GraphicPromptInput promptInput = new GraphicPromptInput(
                normalize(originalQuestion),
                normalize(primaryStrategy),
                normalize(auditReview)
        );

        String prompt = buildEnglishPrompt(promptInput);

        try {
            String base64Image = nanoBananaClient.generateImageBase64(prompt)
                    .blockOptional(DEFAULT_TIMEOUT)
                    .orElse("");

            if (base64Image.isBlank()) {
                log.warn("Agent 3 (Designer): 이미지 데이터가 비어 있습니다.");
                return "";
            }

            log.info("Agent 3: 나노 바나나 이미지 렌더링 완료!");
            return base64Image;
        } catch (Exception exception) {
            log.error("Agent 3 (Designer): 이미지 생성 중 오류가 발생했습니다.", exception);
            return "";
        }
    }

    private String buildEnglishPrompt(GraphicPromptInput input) {
        return switch (input) {
            case GraphicPromptInput(String question, String primary, String audit) -> """
                    A modern 3D financial infographic showing tax savings for an individual investor.
                    Visual style: premium fintech dashboard, clean composition, blue and emerald palette, soft shadows.
                    Layout: one large headline area + three card sections.
                    Required cards:
                    1) Tax savings scenario with clear numeric emphasis.
                    2) Loss harvesting flow and expected tax impact.
                    3) Critical risk warnings from an auditor.
                    Add icons for calculator, warning triangle, and portfolio chart.
                    Keep text concise in English, high readability, 16:9 aspect ratio.

                    User question summary: %s
                    Primary strategy summary: %s
                    Auditor risk summary: %s
                    """.formatted(
                    summarize(question),
                    summarize(primary),
                    summarize(audit)
            );
        };
    }

    private String normalize(String value) {
        return switch (value) {
            case null -> "(empty)";
            case "" -> "(empty)";
            default -> value.trim();
        };
    }

    private String summarize(Object rawText) {
        return switch (rawText) {
            case null -> "(none)";
            case String text when text.length() > 600 -> text.substring(0, 600) + "...";
            case String text -> text;
            default -> rawText.toString();
        };
    }

    private record GraphicPromptInput(
            String question,
            String primaryStrategy,
            String auditReview
    ) {
    }
}
