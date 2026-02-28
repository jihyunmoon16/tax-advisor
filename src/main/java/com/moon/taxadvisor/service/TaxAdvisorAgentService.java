package com.moon.taxadvisor.service;

import com.moon.taxadvisor.client.gemini.GeminiClient;
import com.moon.taxadvisor.client.gemini.GeminiModels.Content;
import com.moon.taxadvisor.client.gemini.GeminiModels.FunctionCall;
import com.moon.taxadvisor.client.gemini.GeminiModels.GenerateContentResponse;
import com.moon.taxadvisor.config.GeminiProperties;
import com.moon.taxadvisor.service.TaxCalculationService.TaxPreview;
import com.moon.taxadvisor.tool.TaxToolDefinitions;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxAdvisorAgentService {

    private static final String DEFAULT_USER_ID = "me";
    private static final DecimalFormat WON_FORMAT = new DecimalFormat("#,###");
    private static final String SYSTEM_PROMPT = """
            너는 2026년 대한민국 세법 전문가다.
            반드시 사용자의 데이터를 도구(function call)로 직접 조회하고 근거 수치를 제시해라.
            해외 주식의 경우 연간 250만 원 양도소득세 기본 공제를 고려하여 전략을 짜라.
            국내 주식의 경우, 2026년 금융투자소득세(금투세)가 시행된다는 가정하에
            수익이 5,000만 원을 초과할 경우 발생할 리스크를 분석에 포함해라.
            해외 주식의 손익 통산(Tax-loss Harvesting)을 통해 최종 납부 세액을 줄이는 구체적인 매도 추천을 포함해라.
            반드시 아래 형식을 지켜라:
            1) 현재 상황 요약 (KR/US 구분)
            2) 손실 실현(손절) 시 세금 변화 (해외 250만 공제 반영)
            3) 실행 우선순위 1~3 (종목과 수량 포함)
            모든 금액은 원화로 표기하고, 숫자를 포함해 구체적으로 답변해라.
            """;

    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final TaxToolDefinitions taxToolDefinitions;
    private final PortfolioQueryService portfolioQueryService;
    private final TaxCalculationService taxCalculationService;

    public AgentResult advise(String question) {
        String normalizedQuestion = (question == null || question.isBlank())
                ? "현재 포트폴리오 기준 절세 전략을 알려줘."
                : question.trim();

        TaxPreview preview = taxCalculationService.calculatePreview(DEFAULT_USER_ID);
        String fallbackAdvice = buildFallbackAdvice(preview);

        if (!geminiClient.isConfigured()) {
            log.warn("Gemini API Key가 없어 로컬 계산 결과로 응답합니다.");
            return new AgentResult(fallbackAdvice, 0, preview, true);
        }

        List<Content> conversation = new ArrayList<>();
        conversation.add(com.moon.taxadvisor.client.gemini.GeminiModels.userText(
                "userId는 me로 고정이다. " + normalizedQuestion));

        return runRecursiveLoop(conversation, preview, fallbackAdvice, 1);
    }

    private AgentResult runRecursiveLoop(
            List<Content> conversation,
            TaxPreview preview,
            String fallbackAdvice,
            int iteration
    ) {
        int maxIterations = geminiProperties.getMaxIterations();
        if (iteration > maxIterations) {
            log.warn("최대 반복 횟수({})를 초과해 로컬 계산으로 종료합니다.", maxIterations);
            return new AgentResult(fallbackAdvice, maxIterations, preview, true);
        }

        log.info("Agentic Workflow 실행: {}/{}", iteration, maxIterations);

        GenerateContentResponse response;
        try {
            response = geminiClient.generateContent(conversation, taxToolDefinitions.buildTools(), SYSTEM_PROMPT);
        } catch (Exception exception) {
            log.error("Gemini 호출 중 오류가 발생했습니다. 로컬 계산 결과를 반환합니다.", exception);
            return new AgentResult(fallbackAdvice, iteration, preview, true);
        }

        Content modelContent = response.firstCandidate()
                .map(candidate -> candidate.content())
                .orElse(null);

        if (modelContent == null) {
            log.warn("Gemini 응답에 candidate가 없어 로컬 계산 결과를 반환합니다.");
            return new AgentResult(fallbackAdvice, iteration, preview, true);
        }

        conversation.add(modelContent);
        List<FunctionCall> functionCalls = modelContent.functionCalls();
        if (functionCalls.isEmpty()) {
            String answer = modelContent.joinedText();
            if (answer.isBlank()) {
                answer = fallbackAdvice;
            }
            return new AgentResult(answer, iteration, preview, false);
        }

        for (FunctionCall functionCall : functionCalls) {
            log.info("Gemini가 함수 실행을 요청했습니다. name={}, args={}", functionCall.name(), functionCall.args());
            Object result = executeTool(functionCall);
            conversation.add(com.moon.taxadvisor.client.gemini.GeminiModels.userFunctionResponse(
                    functionCall.name(),
                    Map.of("data", result)));
        }

        return runRecursiveLoop(conversation, preview, fallbackAdvice, iteration + 1);
    }

    private Object executeTool(FunctionCall functionCall) {
        String functionName = functionCall.name();
        Map<String, Object> args = functionCall.args() == null ? Map.of() : functionCall.args();
        String userId = resolveUserId(args);

        return switch (functionName) {
            case "getUserPortfolio" -> {
                List<PortfolioQueryService.PortfolioView> portfolio = portfolioQueryService.getUserPortfolio(userId);
                Map<String, List<PortfolioQueryService.PortfolioView>> portfolioByMarket = portfolio.stream()
                        .collect(Collectors.groupingBy(
                                PortfolioQueryService.PortfolioView::market,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

                Map<String, Map<String, Object>> marketSummary = portfolioByMarket.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> Map.of(
                                        "positionCount", entry.getValue().size(),
                                        "totalUnrealizedGain", entry.getValue().stream()
                                                .map(PortfolioQueryService.PortfolioView::unrealizedGain)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                ),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));

                yield Map.of(
                        "userId", userId,
                        "portfolio", portfolio,
                        "portfolioByMarket", portfolioByMarket,
                        "marketSummary", marketSummary
                );
            }
            case "getRealizedGains" -> {
                PortfolioQueryService.RealizedGainView view = portfolioQueryService.getRealizedGains(userId);
                yield Map.of(
                        "userId", userId,
                        "totalRealizedGain", view.totalRealizedGain(),
                        "items", view.items()
                );
            }
            default -> {
                log.warn("정의되지 않은 함수 호출이 들어왔습니다. name={}", functionName);
                yield Map.of("error", "Unknown function: " + functionName);
            }
        };
    }

    private String resolveUserId(Map<String, Object> args) {
        Object rawUserId = args.get("userId");
        if (rawUserId instanceof String candidate && !candidate.isBlank()) {
            return candidate;
        }
        return DEFAULT_USER_ID;
    }

    private String buildFallbackAdvice(TaxPreview preview) {
        BigDecimal realizedGain = preview.realizedGain();
        BigDecimal unrealizedLoss = preview.unrealizedLoss();
        BigDecimal taxBefore = preview.estimatedTaxBeforeHarvest();
        BigDecimal taxAfter = preview.estimatedTaxAfterHarvest();
        BigDecimal taxSavings = preview.estimatedTaxSavings();

        return """
                MCP 도구 조회 실패로 로컬 계산 기준 권고안을 제공합니다.
                - 현재 확정 이익: %s원
                - 실현 가능한 미실현 손실: %s원
                - 예상 세금(손절 전, 22%%): %s원
                - 예상 세금(손절 후): %s원
                - 예상 절감 세액: %s원
                결론: 손실 종목 일부를 올해 안에 실현하면 과세표준을 낮춰 세금을 줄일 수 있습니다.
                """
                .formatted(
                        WON_FORMAT.format(realizedGain),
                        WON_FORMAT.format(unrealizedLoss.abs()),
                        WON_FORMAT.format(taxBefore),
                        WON_FORMAT.format(taxAfter),
                        WON_FORMAT.format(taxSavings)
                );
    }

    public record AgentResult(
            String answer,
            int iterations,
            TaxPreview taxPreview,
            boolean fallbackUsed
    ) {
    }
}
