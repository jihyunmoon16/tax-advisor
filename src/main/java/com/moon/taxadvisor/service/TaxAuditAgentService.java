package com.moon.taxadvisor.service;

import com.moon.taxadvisor.client.gemini.GeminiClient;
import com.moon.taxadvisor.client.gemini.GeminiModels;
import com.moon.taxadvisor.client.gemini.GeminiModels.Content;
import com.moon.taxadvisor.client.gemini.GeminiModels.GenerateContentResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxAuditAgentService {

    private static final String AUDITOR_SYSTEM_PROMPT = """
            너는 깐깐한 수석 세무 감사관(Auditor)이다.
            너는 2026년 대한민국 세법 전문가다. 해외 주식의 경우 연간 250만 원 양도소득세 기본 공제를 고려하여 전략을 검토해라.
            국내 주식의 경우, 2026년 금융투자소득세(금투세)가 시행된다는 가정하에 수익이 5,000만 원을 초과할 경우 발생할 리스크를 분석에 포함해라.
            해외 주식의 손익 통산(Tax-loss Harvesting)을 통해 최종 납부 세액을 줄이는 구체적인 매도 추천이 빠졌는지 반드시 점검해라.
            1차 AI가 작성한 절세 전략을 읽고, 논리적 오류나 고객이 놓칠 수 있는 잠재적 리스크
            (예: 거래 수수료, 재매수 타이밍, 주택 대출을 위한 소득금액증명원 감소 등)를 날카롭게 지적해라.
            반드시 3가지 불릿 포인트로 짧고 명확하게 요약할 것.
            """;

    private static final String AUDIT_FALLBACK = """
            - 거래 수수료/세금 외 부대비용이 예상보다 커질 수 있으니 실현 손익과 순효과를 함께 계산하세요.
            - 손절 후 재매수 타이밍이 늦어지면 반등 구간을 놓칠 수 있어 분할 재진입 계획이 필요합니다.
            - 절세를 위해 소득이 낮아지면 대출 심사 시 소득금액증명원 기준에서 불리해질 수 있습니다.
            """;

    private final GeminiClient geminiClient;

    public String audit(String originalQuestion, String primaryAnswer) {
        log.info("Agent 2 (Auditor): 1차 전략 리스크 검토 시작...");

        if (!geminiClient.isConfigured()) {
            log.warn("Agent 2 (Auditor): Gemini API Key 미설정으로 기본 리스크 검토안을 반환합니다.");
            return AUDIT_FALLBACK;
        }

        String auditInput = """
                [사용자 원문 질문]
                %s

                [1차 AI 절세 전략]
                %s
                """
                .formatted(
                        normalize(originalQuestion),
                        normalize(primaryAnswer)
                );

        List<Content> conversation = List.of(GeminiModels.userText(auditInput));

        try {
            GenerateContentResponse response = geminiClient.generateContent(
                    conversation,
                    List.of(),
                    AUDITOR_SYSTEM_PROMPT
            );

            String auditResult = response.firstCandidate()
                    .map(candidate -> candidate.content())
                    .map(Content::joinedText)
                    .filter(text -> !text.isBlank())
                    .orElse(AUDIT_FALLBACK);

            log.info("Agent 2: 검토 완료");
            return auditResult;
        } catch (Exception exception) {
            log.error("Agent 2 (Auditor): 검토 중 오류가 발생해 기본 리스크 검토안을 반환합니다.", exception);
            return AUDIT_FALLBACK;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "(내용 없음)" : value.trim();
    }
}
