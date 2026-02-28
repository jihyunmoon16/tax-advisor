# tax-advisor

Spring Boot 기반의 MCP-style Agentic Tax Advisor 백엔드입니다.  
단순 질의응답 챗봇이 아니라, LLM이 함수 호출(`getUserPortfolio`, `getRealizedGains`)로 사용자 데이터를 직접 조회한 뒤 절세 전략을 수치로 제시합니다.

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

## DB 시연 데이터

- `realized_gain` 합계가 `2,500,000원`
- `portfolio`에 `-30%` 손실 종목 포함 (`LOSS_BIO`)
- `WIN_AI` 종목은 `+5,000,000원` 미실현 수익으로 로그 시연 포인트 제공

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
  "answer": "...절세 전략...",
  "iterations": 3,
  "fallbackUsed": false,
  "taxPreview": {
    "realizedGain": 2500000,
    "unrealizedLoss": -3000000,
    "estimatedTaxBeforeHarvest": 550000,
    "estimatedTaxAfterHarvest": 0,
    "estimatedTaxSavings": 550000
  }
}
```

### 2) MCP Tool 스키마 확인

`GET /api/tools`

Gemini에 전달하는 함수 정의(JSON 규격)를 확인할 수 있습니다.

## 발표 포인트(로그)

실행 중 아래 로그가 출력됩니다.

- `AI가 사용자의 포트폴리오 조회를 요청했습니다`
- `DB에서 실시간 수익 5,000,000 원 감지`
- `AI가 확정 손익 조회를 요청했습니다`
- `DB에서 확정 손익 합계 2,500,000 원 조회`

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
