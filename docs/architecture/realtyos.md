# RAG 구현 정리

이 문서는 현재 API 서버에 구현된 RAG 흐름과 관련 컴포넌트를 정리한다.

## 전체 흐름

현재 RAG는 실거래가 문서를 기반으로 검색하고, 사용자 메모리와 모델 라우팅을 결합해 답변을 생성한다.

```text
User Query
-> Query Rewrite
-> User Memory Load
-> Memory merge into search condition
-> Embedding Search
-> Prompt Composition
-> Model Routing
-> LLM Generation
-> Memory Update
```

스트리밍 답변은 같은 흐름을 사용하되, 중간 상태와 토큰을 SSE 이벤트로 내려준다.

```text
User Query
-> memory_loaded
-> retrieval_started
-> retrieval_completed
-> model_selected
-> token*
-> completed
```

## 주요 API

### RAG 검색

```http
POST /api/v1/rag/search
```

역할:
- 질문을 query rewrite로 정규화한다.
- 로그인 사용자라면 memory를 검색 조건에 병합한다.
- embedding 기반 pgvector 검색을 수행한다.
- 검색 후 사용자 memory event를 저장한다.

비로그인 사용자는 기존 RAG 검색처럼 동작한다.

### RAG 답변 생성

```http
POST /api/v1/rag/ask
```

역할:
- RAG 검색 결과를 기반으로 프롬프트를 만든다.
- 사용자 memory를 `[사용자 메모리]` 섹션으로 주입한다.
- 모델 라우터가 provider/model을 선택한다.
- LLM 답변과 근거 문서 목록을 반환한다.
- 답변 후 사용자 memory를 갱신한다.

### RAG 답변 스트리밍

```http
POST /api/v1/rag/ask/stream
```

역할:
- `/rag/ask`와 같은 RAG/memory/router 흐름을 사용한다.
- `SseEmitter`로 진행 상태와 답변 chunk를 이벤트로 전송한다.

이벤트:
- `memory_loaded`
- `retrieval_started`
- `retrieval_completed`
- `model_selected`
- `token`
- `completed`
- `error`

현재 Ollama는 실제 streaming chunk를 전송한다. OpenAI/Gemini는 client-level streaming 구현이 아직 없어서 기본 인터페이스 동작상 전체 답변이 하나의 `token` 이벤트로 내려갈 수 있다.

### 사용자 AI 메모리 관리

```http
GET /api/v1/rag/memory/me
GET /api/v1/rag/memory/me/events?limit=20
PUT /api/v1/rag/memory/me
DELETE /api/v1/rag/memory/me
```

역할:
- 사용자가 자기 AI memory를 조회한다.
- 최근 RAG 조회 이벤트를 확인한다.
- 관심 지역과 가격대를 직접 수정한다.
- memory와 이벤트를 초기화한다.

## Query Rewrite

`RagQueryRewritePolicy`가 자연어 질문에서 검색 조건을 추론한다.

추론 대상:
- 지역: 강남, 서초, 송파, 마포 등 주요 키워드
- 가격대: `10억`, `10억 이상`, `80000만원` 등
- 면적: `84제곱`, `84㎡`, `84m2` 등
- 기간: `2025년`, `2025년 3월`, `올해`, `작년`
- 최신순 의도: `최근`, `최신`, `요즘`, `최근순`

명시 조건이 있으면 명시 조건이 우선한다. 질문에서 추론된 조건은 비어 있는 조건을 채운다.

## Retrieval

`RagSearchService`가 검색을 담당한다.

흐름:
1. `topK`를 기본값/최댓값 기준으로 보정한다.
2. Query Rewrite를 수행한다.
3. embedding provider/model profile을 결정한다.
4. query embedding을 만든다.
5. `RagDocumentRepository.searchByEmbedding(...)`으로 pgvector 검색을 수행한다.

검색 조건:
- region
- apartmentName
- fromYear/fromMonth
- toYear/toMonth
- minPrice/maxPrice
- minArea/maxArea
- recentFirst

검색 결과는 유사도와 최신성 기반 점수를 포함한다.

## Document and Embedding Build

RAG 문서는 실거래가 데이터를 기반으로 생성된다.

관련 서비스:
- `RagDocumentBuildService`
- `RagEmbeddingBuildService`
- `RagSyncService`
- `RagSyncScheduler`

관련 테이블:
- `rag_document`
- `rag_embedding`

지원하는 embedding provider:
- OpenAI
- Ollama

여러 embedding model을 동시에 지원하기 위해 `rag_embedding`은 provider/model 정보를 가진다.

## User Memory

Memory는 로그인 사용자의 RAG 사용 패턴을 저장하고 다음 질문에 반영한다.

관련 테이블:
- `user_ai_memory`
- `user_ai_memory_event`

`user_ai_memory` 역할:
- user_id별 현재 요약 memory 저장
- preferred_region
- recent_region
- min_price
- max_price
- query_count
- last_query

`user_ai_memory_event` 역할:
- RAG 검색/답변마다 이벤트 저장
- query
- region
- apartment_name
- min_price
- max_price
- created_at

Memory 갱신 방식:
1. 질문과 조건을 query rewrite로 다시 정규화한다.
2. memory event를 저장한다.
3. 최근 50개 이벤트에서 region 빈도를 집계한다.
4. 가장 자주 나온 region을 `preferredRegion`으로 갱신한다.
5. 가장 최근 명시 region을 `recentRegion`으로 저장한다.
6. 가격대는 최근 명시값을 저장한다.

Memory merge 정책:
- 이번 요청에서 명시된 조건이 memory보다 우선한다.
- memory는 비어 있는 region/price 조건만 보조로 채운다.
- 비로그인 사용자는 memory load/update 없이 기존 RAG 흐름을 사용한다.

Prompt 주입 정책:
- 사용자 memory는 답변의 관점과 우선순위에만 사용한다.
- 사실 근거는 반드시 RAG 문서로 제한한다.
- 프롬프트에는 `[사용자 메모리]`와 `[RAG 문서]`가 분리되어 들어간다.

## Model Routing

`AiModelRouter`가 provider/model을 선택한다.

현재 RAG 라우팅 정책:
- 명시적으로 `answerProvider`가 들어오면 해당 provider를 사용한다.
- 명시 provider가 없고 local-first가 켜져 있으면 짧은 RAG 요청은 Ollama를 우선 사용한다.
- 입력이 길거나 high-quality 경로가 필요하면 OpenAI를 사용한다.
- primary 실패 시 fallback provider/model을 사용할 수 있다.

설정:

```yaml
ai:
  router:
    enabled: true
    local-first: true
    local-max-input-chars: 6000
    default-provider: OPENAI
    default-model: gpt-4o-mini
    local-provider: OLLAMA
    local-model: qwen3:8b
    high-quality-provider: OPENAI
    high-quality-model: gpt-4o-mini
    fallback-provider: OPENAI
    fallback-model: gpt-4o-mini
```

## Streaming

AI client 공통 인터페이스에 `streamChat(...)`이 추가되어 있다.

현재 구현:
- `OllamaClient.streamChat(...)`
  - Ollama `/api/chat`의 `stream: true` 응답을 NDJSON 라인 단위로 파싱한다.
  - 각 chunk의 `message.content`를 `token` 이벤트로 전송한다.

기본 fallback:
- streaming을 구현하지 않은 client는 기본 메서드가 일반 `chat(...)` 결과를 한 번에 chunk로 넘긴다.

## 주요 컴포넌트

Application:
- `RagSearchService`
- `RagAnswerService`
- `RagAnswerStreamingService`
- `RagAnswerPromptBuilder`
- `RagDocumentBuildService`
- `RagEmbeddingBuildService`
- `RagSyncService`
- `RagIndexStatsService`

Memory:
- `UserAiMemoryService`
- `UserAiMemory`
- `UserAiMemoryJpaEntity`
- `UserAiMemoryEventJpaEntity`

AI:
- `AiService`
- `AiModelRouter`
- `OpenAiClient`
- `OllamaClient`
- `GeminiClient`

Interfaces:
- `RagSearchController`
- `RagAnswerController`
- `RagDocumentController`
- `UserAiMemoryController`
