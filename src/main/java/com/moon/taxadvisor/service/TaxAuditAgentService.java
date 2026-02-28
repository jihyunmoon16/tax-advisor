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
            이 서비스의 계산식은 데모용 단순 모델(과세표준 * 22%%)이라는 전제를 유지해라.
            손절 전 과세표준=max(확정손익,0), 손절 후 과세표준=max(확정손익+미실현손실,0) 기준으로 검토해라.
            1차 전략이 이 계산 한계를 명확히 고지했는지 반드시 점검해라.
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
        log.info(
                "Agent 2 입력 요약: questionLength={}, primaryAnswerLength={}",
                normalize(originalQuestion).length(),
                normalize(primaryAnswer).length()
        );

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

            log.info(
                    "Agent 2: 검토 완료 fallbackUsed={}, auditLength={}",
                    AUDIT_FALLBACK.equals(auditResult),
                    auditResult.length()
            );
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
