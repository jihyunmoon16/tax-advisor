package com.moon.taxadvisor.controller;

import com.moon.taxadvisor.service.TaxAuditAgentService;
import com.moon.taxadvisor.service.TaxAdvisorAgentService;
import com.moon.taxadvisor.service.TaxAdvisorAgentService.AgentResult;
import com.moon.taxadvisor.service.TaxCalculationService.TaxPreview;
import com.moon.taxadvisor.service.TaxGraphicAgentService;
import com.moon.taxadvisor.tool.TaxToolDefinitions;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TaxAdvisorController {

    private final TaxAdvisorAgentService taxAdvisorAgentService;
    private final TaxAuditAgentService taxAuditAgentService;
    private final TaxGraphicAgentService taxGraphicAgentService;
    private final TaxToolDefinitions taxToolDefinitions;

    @PostMapping("/advice")
    public AdviceResponse getAdvice(@RequestBody AdviceRequest request) {
        log.info("Advice Pipeline 시작: Agent 1 -> Agent 2 -> Agent 3");
        PipelineResult pipelineResult = executePipeline(request.question());
        return AdviceResponse.from(
                request.question(),
                pipelineResult.primaryResult(),
                pipelineResult.auditReview(),
                pipelineResult.base64Image()
        );
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> getTools() {
        return taxToolDefinitions.asJsonSpec();
    }

    public record AdviceRequest(String question) {
    }

    public record AdviceResponse(
            String userId,
            String question,
            String primaryStrategy,
            String auditReview,
            String base64Image,
            int iterations,
            boolean fallbackUsed,
            TaxPreview taxPreview
    ) {
        private static AdviceResponse from(
                String question,
                AgentResult result,
                String auditReview,
                String base64Image
        ) {
            return new AdviceResponse(
                    "me",
                    question,
                    result.answer(),
                    auditReview,
                    base64Image,
                    result.iterations(),
                    result.fallbackUsed(),
                    result.taxPreview()
            );
        }
    }

    private PipelineResult executePipeline(String question) {
        long pipelineStartedAt = System.currentTimeMillis();

        log.info("Pipeline Stage 1: Agent 1 전략 수립 시작");
        long stage1StartedAt = System.currentTimeMillis();
        AgentResult primaryResult = taxAdvisorAgentService.advise(question);
        log.info(
                "Pipeline Stage 1 완료: answerLength={}, iterations={}, fallbackUsed={}, elapsedMs={}",
                primaryResult.answer().length(),
                primaryResult.iterations(),
                primaryResult.fallbackUsed(),
                System.currentTimeMillis() - stage1StartedAt
        );

        log.info("Pipeline Stage 2: Agent 2 리스크 감사 시작");
        long stage2StartedAt = System.currentTimeMillis();
        String auditReview = taxAuditAgentService.audit(question, primaryResult.answer());
        log.info(
                "Pipeline Stage 2 완료: auditLength={}, elapsedMs={}",
                auditReview.length(),
                System.currentTimeMillis() - stage2StartedAt
        );

        log.info("Pipeline Stage 3: Agent 3 인포그래픽 생성 시작");
        long stage3StartedAt = System.currentTimeMillis();
        String base64Image = taxGraphicAgentService.createInfographic(
                question,
                primaryResult.answer(),
                auditReview
        );
        log.info(
                "Pipeline Stage 3 완료: imageGenerated={}, elapsedMs={}",
                !base64Image.isBlank(),
                System.currentTimeMillis() - stage3StartedAt
        );
        log.info("Advice Pipeline 완료: totalElapsedMs={}", System.currentTimeMillis() - pipelineStartedAt);

        return new PipelineResult(primaryResult, auditReview, base64Image);
    }

    private record PipelineResult(
            AgentResult primaryResult,
            String auditReview,
            String base64Image
    ) {
    }
}
