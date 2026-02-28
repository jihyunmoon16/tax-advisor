package com.moon.taxadvisor.service;

import com.moon.taxadvisor.client.gemini.GeminiClient;
import com.moon.taxadvisor.client.gemini.GeminiModels.Content;
import com.moon.taxadvisor.client.gemini.GeminiModels.FunctionCall;
import com.moon.taxadvisor.client.gemini.GeminiModels.GenerateContentResponse;
import com.moon.taxadvisor.config.GeminiProperties;
import com.moon.taxadvisor.domain.Portfolio;
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
            너는 대한민국 개인 투자자의 경제적 안정과 금융 문해력 향상을 돕는 사회적 선(Social Good) 지향 주식 절세 전략 어시스턴트다.
            반드시 사용자의 데이터를 도구(function call)로 직접 조회하고 근거 수치를 제시해라.
            중요한 제약:
            - 이 서비스의 세금 계산 엔진은 데모용 단순 모델이다.
            - 예상세액 계산식은 (과세표준 * 22%%) 이다.
            - 손절 전 과세표준 = max(확정손익 합계, 0)
            - 손절 후 과세표준 = max(확정손익 합계 + 미실현 손실 합계, 0)
            - 해외 250만 기본공제, 국내 금투세 5천만 기준, 수수료/환율은 현재 엔진에 반영되지 않는다.
            절세 전략은 반드시 손실 실현(Tax-loss Harvesting) 실행안을 포함해라.
            반드시 아래 형식을 지켜라:
            1) 현재 상황 요약 (KR/US 구분)
            2) 손실 실현(손절) 시 세금 변화 (데모 계산식 기준)
            3) 실행 우선순위 1~3 (종목과 수량 포함)
            4) 계산 한계 1줄 요약
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
        log.info("Agent 1 입력 수신: userId={}, question={}", DEFAULT_USER_ID, normalizedQuestion);

        TaxPreview preview = taxCalculationService.calculatePreview(DEFAULT_USER_ID);
        logUserBaseline(DEFAULT_USER_ID, preview);
        String fallbackAdvice = buildFallbackAdvice(preview);

        if (!geminiClient.isConfigured()) {
            log.warn("Gemini API Key가 없어 로컬 계산 결과로 응답합니다.");
            return new AgentResult(fallbackAdvice, 0, preview, true);
        }

        List<Content> conversation = new ArrayList<>();
        conversation.add(com.moon.taxadvisor.client.gemini.GeminiModels.userText(
                "userId는 me로 고정이다. " + normalizedQuestion));

        return runRecursiveLoop(conversation, preview, fallbackAdvice, 1, false);
    }

    private AgentResult runRecursiveLoop(
            List<Content> conversation,
            TaxPreview preview,
            String fallbackAdvice,
            int iteration,
            boolean toolCalled
    ) {
        int maxIterations = geminiProperties.getMaxIterations();
        if (iteration > maxIterations) {
            log.warn("최대 반복 횟수({})를 초과해 로컬 계산으로 종료합니다.", maxIterations);
            return new AgentResult(fallbackAdvice, maxIterations, preview, true);
        }

        log.info(
                "Agent 1 반복 시작: iteration={}/{}, conversationSize={}, toolCalled={}",
                iteration,
                maxIterations,
                conversation.size(),
                toolCalled
        );

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
        String modelText = modelContent.joinedText();
        log.info(
                "Agent 1 모델 응답: iteration={}, functionCallCount={}, textLength={}, textPreview={}",
                iteration,
                functionCalls.size(),
                modelText.length(),
                abbreviate(modelText, 140)
        );

        if (functionCalls.isEmpty()) {
            String answer = modelText;
            if (!toolCalled) {
                log.warn("함수 호출 없이 답변이 생성되었습니다. 함수 호출을 재요청합니다. iteration={}", iteration);
                conversation.add(com.moon.taxadvisor.client.gemini.GeminiModels.userText(
                        "반드시 최소 1회 이상 getUserPortfolio 또는 getRealizedGains를 호출한 뒤 최종 답변을 작성해라."));
                return runRecursiveLoop(conversation, preview, fallbackAdvice, iteration + 1, false);
            }

            boolean fallbackUsed = answer.isBlank();
            if (fallbackUsed) {
                answer = fallbackAdvice;
            }

            log.info(
                    "Agent 1 최종 답변 확정: iteration={}, fallbackUsed={}, answerLength={}",
                    iteration,
                    fallbackUsed,
                    answer.length()
            );
            return new AgentResult(answer, iteration, preview, fallbackUsed);
        }

        boolean nextToolCalled = toolCalled;
        for (FunctionCall functionCall : functionCalls) {
            log.info("Gemini가 함수 실행을 요청했습니다. name={}, args={}", functionCall.name(), functionCall.args());
            Object result = executeTool(functionCall);
            conversation.add(com.moon.taxadvisor.client.gemini.GeminiModels.userFunctionResponse(
                    functionCall.name(),
                    Map.of("data", result)));
            nextToolCalled = nextToolCalled || isSupportedTool(functionCall.name());
            log.info(
                    "Agent 1 도구 실행 완료: iteration={}, name={}, resultSummary={}",
                    iteration,
                    functionCall.name(),
                    summarizeToolResult(functionCall.name(), result)
            );
        }

        return runRecursiveLoop(conversation, preview, fallbackAdvice, iteration + 1, nextToolCalled);
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

    private boolean isSupportedTool(String functionName) {
        return "getUserPortfolio".equals(functionName) || "getRealizedGains".equals(functionName);
    }

    private String summarizeToolResult(String functionName, Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return "non-map-result";
        }
        if ("getUserPortfolio".equals(functionName)) {
            Object portfolio = map.get("portfolio");
            if (portfolio instanceof List<?> list) {
                return "portfolioCount=" + list.size();
            }
            return "portfolioCount=unknown";
        }
        if ("getRealizedGains".equals(functionName)) {
            Object total = map.get("totalRealizedGain");
            Object items = map.get("items");
            int itemCount = items instanceof List<?> list ? list.size() : -1;
            return "totalRealizedGain=" + total + ", itemCount=" + itemCount;
        }
        return "keys=" + map.keySet();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private void logUserBaseline(String userId, TaxPreview preview) {
        List<Portfolio> portfolios = portfolioQueryService.findPortfolioEntities(userId);

        BigDecimal totalCostBasis = portfolios.stream()
                .map(portfolio -> portfolio.getAveragePrice().multiply(BigDecimal.valueOf(portfolio.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMarketValue = portfolios.stream()
                .map(portfolio -> portfolio.getCurrentPrice().multiply(BigDecimal.valueOf(portfolio.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedPnL = portfolios.stream()
                .map(Portfolio::getUnrealizedGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedLoss = portfolios.stream()
                .map(Portfolio::getUnrealizedGain)
                .filter(gain -> gain.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "사용자 기본 현황(userId={}): 총평가금액={}원, 투자원금={}원, 미실현손익={}원, 미실현손실={}원, 확정손익={}원, 예상 절감세액={}원",
                userId,
                WON_FORMAT.format(totalMarketValue),
                WON_FORMAT.format(totalCostBasis),
                WON_FORMAT.format(totalUnrealizedPnL),
                WON_FORMAT.format(totalUnrealizedLoss),
                WON_FORMAT.format(preview.realizedGain()),
                WON_FORMAT.format(preview.estimatedTaxSavings())
        );
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
                - 계산 한계: 단순 22%% 추정이며 기본공제/수수료/환율은 미반영
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
