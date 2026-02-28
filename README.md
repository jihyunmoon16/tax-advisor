# tax-advisor

Spring Boot 기반의 MCP-style Agentic Tax Advisor 백엔드입니다.  
단순 질의응답 챗봇이 아니라, LLM이 함수 호출(`getUserPortfolio`, `getRealizedGains`)로 사용자 데이터를 직접 조회한 뒤 절세 전략을 수치로 제시합니다.
세금 계산은 해커톤 데모용 단순 모델(`과세표준 x 22%`)을 사용합니다.

## Project Description

### English

Tax Advisor is a Gemini-powered, tool-calling backend that helps individual investors make pre-trade tax decisions.  
Instead of acting like a generic chatbot, it retrieves portfolio and realized-gain data through structured functions, then generates a quantified tax-loss-harvesting strategy.  
A second audit agent reviews risks, and a third agent creates an infographic for clear communication.  
Built with Spring Boot and deployed on Cloud Run, it demonstrates an end-to-end agent workflow: data retrieval, reasoning, auditing, and visualization in one API.

### 한국어

Tax Advisor는 Gemini 기반 함수 호출(Function Calling) 백엔드로, 개인 투자자의 매도 전 절세 의사결정을 돕는 서비스입니다.  
단순 챗봇이 아니라 포트폴리오와 확정손익 데이터를 실제 툴로 조회한 뒤, 손실 실현(Tax-loss Harvesting) 중심 전략을 수치로 제시합니다.  
이어 감사 에이전트가 리스크를 점검하고, 디자이너 에이전트가 인포그래픽을 생성해 결과를 직관적으로 보여줍니다.  
Spring Boot + Cloud Run 기반으로, 데이터 조회부터 전략 생성·감사·시각화까지 하나의 API에서 처리하는 엔드투엔드 에이전트 워크플로우를 구현했습니다.

## 기술 스택

- Java 21 (Java 17+ 호환 구조)
- Spring Boot
- Spring Web / WebFlux(WebClient)
- Spring Data JPA
- H2
- Lombok
- Gemini API(Function Calling)

## 실행 전 준비

1. 루트에 `.env` 파일 생성 (`.env.example` 참고)

```bash
cp .env.example .env
```

`.env` 내용:

```bash
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
GEMINI_MODEL=gemini-3-flash-preview
NANO_BANANA_API_KEY=YOUR_GEMINI_API_KEY
NANO_BANANA_MODEL=gemini-3.1-flash-image-preview
```

2. 애플리케이션 실행

```bash
./gradlew bootRun
```

선택: 로컬에서 H2 콘솔이 필요하면 실행 시 아래 값을 추가하세요.

```bash
H2_CONSOLE_ENABLED=true ./gradlew bootRun
```

## DB 시연 데이터

- `realized_gain` 합계가 `6,600,000원`
- `portfolio`에 손실 종목 포함 (`TSLA`, 미실현 `-2,000,000원`)
- `SAMSUNG_ELEC` 종목은 `+54,000,000원` 미실현 수익으로 로그 시연 포인트 제공

## API

### 1) 절세 분석 요청

`POST /api/advice`

요청 예시:

```json
{
  "question": "올해 세금을 줄이려면 어떤 종목을 정리하면 좋아?"
}
```

응답 예시(요약):

```json
{
  "userId": "me",
  "question": "올해 세금을 줄이려면 어떤 종목을 정리하면 좋아?",
  "primaryStrategy": "...절세 전략...",
  "auditReview": "...리스크 검토...",
  "base64Image": "",
  "iterations": 3,
  "fallbackUsed": false,
  "taxPreview": {
    "realizedGain": 6600000,
    "unrealizedLoss": -2000000,
    "estimatedTaxBeforeHarvest": 1452000,
    "estimatedTaxAfterHarvest": 1012000,
    "estimatedTaxSavings": 440000
  }
}
```

참고:
- `fallbackUsed=true`는 Gemini 미설정/호출 실패 시 로컬 계산 기반 응답을 의미합니다.
- `taxPreview`는 데모 계산식(`max(확정손익,0)`, `max(확정손익+미실현손실,0)`, `22%`) 기준입니다.

### 2) MCP Tool 스키마 확인

`GET /api/tools`

Gemini에 전달하는 함수 정의(JSON 규격)를 확인할 수 있습니다.

## 발표 포인트(로그)

실행 중 아래 로그가 출력됩니다.

- `Advice Pipeline 시작: Agent 1 -> Agent 2 -> Agent 3`
- `Gemini가 함수 실행을 요청했습니다. name=getUserPortfolio`
- `DB에서 실시간 수익 54,000,000 원 감지 (종목: SAMSUNG_ELEC)`
- `Gemini가 함수 실행을 요청했습니다. name=getRealizedGains`
- `DB에서 확정 손익 합계 6,600,000 원 조회`

## Postman

`postman/tax-advisor.postman_collection.json` 파일을 import해서 바로 시연할 수 있습니다.

## Cloud Run 배포

사전 조건:

- `gcloud auth login` 완료
- Docker Desktop 실행 중
- `.env`에 `GEMINI_API_KEY` 설정 완료

원클릭 배포:

```bash
./deploy.sh
```

선택 옵션(환경 변수):

```bash
GCP_PROJECT_ID=your-project-id \
GCP_REGION=us-central1 \
CLOUD_RUN_SERVICE=tax-advisor \
ARTIFACT_REPOSITORY=tax-advisor \
./deploy.sh
```

배포 완료 후 출력되는 `SERVICE_URL`로 바로 호출할 수 있습니다.
