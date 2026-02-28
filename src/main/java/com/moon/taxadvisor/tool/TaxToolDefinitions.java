package com.moon.taxadvisor.tool;

import com.moon.taxadvisor.client.gemini.GeminiModels.FunctionDeclaration;
import com.moon.taxadvisor.client.gemini.GeminiModels.Tool;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaxToolDefinitions {

    public List<Tool> buildTools() {
        return List.of(new Tool(List.of(getUserPortfolioDeclaration(), getRealizedGainsDeclaration())));
    }

    public List<Map<String, Object>> asJsonSpec() {
        return List.of(
                Map.of(
                        "name", "getUserPortfolio",
                        "description", "사용자의 현재 보유 종목을 시장(KR/US) 구분과 함께 조회하고 미실현 손익을 반환한다.",
                        "parameters", buildCommonUserIdParameters()
                ),
                Map.of(
                        "name", "getRealizedGains",
                        "description", "사용자의 확정 손익 내역과 합계를 조회한다.",
                        "parameters", buildCommonUserIdParameters()
                )
        );
    }

    private FunctionDeclaration getUserPortfolioDeclaration() {
        return new FunctionDeclaration(
                "getUserPortfolio",
                "사용자의 현재 보유 종목을 시장(KR/US) 구분과 함께 조회하고 미실현 손익을 반환한다.",
                buildCommonUserIdParameters()
        );
    }

    private FunctionDeclaration getRealizedGainsDeclaration() {
        return new FunctionDeclaration(
                "getRealizedGains",
                "사용자의 확정 손익 내역과 합계를 조회한다.",
                buildCommonUserIdParameters()
        );
    }

    private Map<String, Object> buildCommonUserIdParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of(
                                "type", "string",
                                "description", "조회 대상 사용자 ID. 이 서비스는 기본적으로 me를 사용한다."
                        )
                ),
                "required", List.of("userId")
        );
    }
}
