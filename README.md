# RealtyOS API

부동산 실거래 데이터를 RAG와 LLM으로 검색하고 분석하는 AI 부동산 백엔드 프로젝트입니다.

RealtyOS는 국토교통부 실거래 데이터를 수집해 PostgreSQL에 저장하고, 거래 데이터를 RAG 문서와 임베딩으로 변환한 뒤 OpenAI 또는 Ollama 기반 LLM 답변으로 제공하는 서비스입니다. 사용자는 “강남구 30평대 20억 이하 아파트”, “대치동과 잠실 20평대 비교”처럼 자연어로 질문하고, 시스템은 실제 거래 근거를 검색해 답변과 근거 문서를 함께 반환합니다.

---

## Project Highlights

- 실거래 원천 데이터 수집부터 RAG 문서화, 임베딩, 검색, 답변 생성까지 하나의 흐름으로 구현
- OpenAI와 Ollama를 모두 지원하는 provider/model 분리 구조
- PostgreSQL + pgvector 기반 벡터 검색과 키워드 fallback 검색 구현
- 자연어 질문에서 지역, 가격, 면적, 기간 조건을 추론하는 Query Rewrite 적용
- SSE 기반 답변 스트리밍으로 LLM 응답을 token 단위로 전달
- 사용자 AI 메모리를 저장해 반복 질문의 검색 맥락을 개인화
- Kakao / Google OAuth2 로그인과 JWT 인증 흐름 구현

---

## Tech Stack

| Area | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot |
| Auth | Spring Security, OAuth2, JWT |
| Database | PostgreSQL, pgvector |
| Migration | Flyway |
| AI | OpenAI, Ollama |
| Streaming | Server-Sent Events |
| Frontend | React, Vite |

---

## Architecture

```text
Real Estate Open Data
        |
        v
Real Estate Sync
        |
        v
PostgreSQL
        |
        v
RAG Document Builder
        |
        v
Embedding Builder
OpenAI / Ollama
        |
        v
pgvector Search
        |
        v
RAG Answer
LLM / Evidence Summary / SSE
```

---

## RAG Pipeline

1. 실거래 데이터를 수집해 거래 테이블에 저장합니다.
2. 거래 데이터를 검색 가능한 RAG 문서로 변환합니다.
3. provider/model 조합별로 임베딩을 생성합니다.
4. 사용자 질문을 분석해 지역, 가격, 면적, 기간 조건을 추론합니다.
5. pgvector 기반 벡터 검색으로 관련 거래 문서를 찾습니다.
6. 벡터 검색 결과가 부족하면 실제 거래 테이블 기준 키워드 fallback 검색을 수행합니다.
7. 검색된 근거 문서를 기반으로 LLM 답변을 생성하거나, 비교 질문은 서버가 근거 요약을 직접 생성합니다.
8. 일반 응답 또는 SSE 스트리밍으로 답변과 근거를 반환합니다.

---

## Key Features

### AI 부동산 검색

자연어 질문을 실거래 데이터 검색 조건으로 변환합니다.

예시:

```text
강남구 30평대 20억 이하 아파트
최근 강남구 거래 흐름
대치동과 잠실 20평대 비교
```

가격 표현, 평형 표현, 지역명, 최근 거래 조건을 추론해 검색 품질을 높였습니다.

### Multi Provider AI

임베딩과 답변 생성을 분리했습니다.

- OpenAI 임베딩 / OpenAI 답변
- Ollama 임베딩 / Ollama 답변
- Ollama 임베딩 / OpenAI 답변

로컬 테스트에서는 Ollama를 사용하고, 필요 시 OpenAI로 전환할 수 있도록 provider/model을 요청 단위로 선택할 수 있습니다.

### pgvector 기반 검색

RAG 문서 임베딩을 PostgreSQL pgvector에 저장하고, 질문 임베딩과의 cosine distance 기반으로 관련 문서를 검색합니다.

검색 결과에는 벡터 검색인지 키워드 fallback인지 구분값을 포함해, 화면에서 “유사도”와 “키워드 매칭”을 다르게 표시할 수 있도록 했습니다.

### Query Rewrite

사용자 질문을 그대로 임베딩 검색에 넘기지 않고, 도메인 조건으로 변환합니다.

- `30평대` -> 전용면적 약 99㎡ ~ 132㎡
- `20억 이하` -> 거래금액 상한 조건
- `최근` -> 최신 거래 우선 정렬
- `강남`, `대치`, `잠실` -> 실제 거래 소재지 기준 지역 필터

비교 질문은 단일 지역 필터로 좁히지 않고, 각 지역별 거래 근거를 따로 확보하도록 처리했습니다.

### SSE Streaming

`/api/v1/rag/ask/stream`은 답변 생성 과정을 이벤트로 전달합니다.

```text
memory_loaded
retrieval_started
retrieval_completed
model_selected
token
completed
```

프론트엔드는 `token` 이벤트를 누적해 답변을 실시간으로 표시합니다.

### Evidence Guardrail

LLM이 근거 문서와 다른 지역이나 아파트를 만들어내지 않도록 서버 단에서 방어 로직을 추가했습니다.

- 검색 근거가 없으면 답변 생성 차단
- LLM이 “문서에 없는 지역”처럼 잘못 판단하면 근거 요약으로 대체
- 비교 질문은 LLM 자유 생성 대신 서버가 실거래 근거 기반 표를 직접 생성
- 지역 alias와 실제 법정동 기준 필터링 적용

### User AI Memory

로그인 사용자의 질문과 검색 조건을 저장합니다.

- 선호 지역
- 최근 조회 지역
- 가격대
- 최근 질문 이벤트

이 정보는 다음 RAG 검색 시 조건 보강에 사용됩니다.

---

## Main APIs

### RAG

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/rag/search` | RAG 문서 검색 |
| POST | `/api/v1/rag/ask` | RAG 기반 답변 생성 |
| POST | `/api/v1/rag/ask/stream` | SSE 기반 스트리밍 답변 |
| POST | `/api/v1/rag/documents/deals` | 실거래 데이터를 RAG 문서로 변환 |
| POST | `/api/v1/rag/documents/embeddings` | 누락 임베딩 생성 |
| POST | `/api/v1/rag/documents/sync` | 문서와 임베딩 동기화 |
| GET | `/api/v1/rag/documents/stats` | RAG 인덱스 상태 조회 |

### AI Memory

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/rag/memory/me` | 사용자 AI 메모리 조회 |
| GET | `/api/v1/rag/memory/me/events` | 최근 RAG 사용 이벤트 조회 |
| PUT | `/api/v1/rag/memory/me` | 사용자 AI 메모리 수정 |
| DELETE | `/api/v1/rag/memory/me` | 사용자 AI 메모리 초기화 |

### Auth

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/v1/auth/login/{provider}` | OAuth2 로그인 시작 |
| GET | `/api/v1/auth/token/exchange` | OAuth 임시 code를 JWT로 교환 |
| POST | `/api/v1/auth/reissue` | access token 재발급 |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| DELETE | `/api/v1/users` | 회원 탈퇴 |

---

## Problem Solving

### 1. 대량 데이터와 임베딩 처리

실거래 데이터가 수만 건 이상 쌓이는 상황을 고려해 RAG 문서와 임베딩을 분리했습니다. 문서 변경 시 기존 임베딩을 무효화하고, 누락 임베딩만 다시 생성할 수 있도록 구성했습니다.

### 2. OpenAI 요청 크기 제한

임베딩 생성 시 입력이 과도하게 커지면 OpenAI token 제한에 걸릴 수 있습니다. 이를 방지하기 위해 문서 단위 입력을 정제하고 batch 단위로 나누어 요청하도록 처리했습니다.

### 3. Ollama 로컬 LLM 지연

로컬 모델은 응답이 느릴 수 있어 WebClient timeout과 Ollama options를 조정하고, SSE 스트리밍을 통해 사용자가 응답 진행 상태를 볼 수 있도록 했습니다.

### 4. SSE 응답 버퍼링 문제

공통 응답 로깅 필터가 `ContentCachingResponseWrapper`로 SSE 응답을 감싸면서 token이 실시간으로 전달되지 않는 문제가 있었습니다. SSE endpoint는 해당 wrapper를 타지 않도록 제외해 스트리밍을 복구했습니다.

### 5. 지역 비교 검색 품질

“대치동과 잠실 비교” 같은 질문에서 단일 지역 필터가 적용되면 한쪽 지역만 검색되는 문제가 있었습니다. 비교 질문은 다중 지역을 감지하고, 각 지역별 근거 거래를 별도로 확보한 뒤 비교 요약을 생성하도록 개선했습니다.

---

## Modules

```text
auth
OAuth2, JWT, 로그인, 토큰 재발급

common
공통 설정, AI client, model router, web filter

rag
RAG 문서, 임베딩, 검색, 답변 생성, SSE, 사용자 AI 메모리

realestate
실거래 및 분양 데이터 수집

user
사용자 정보와 설정
```

---

## Current Status

- RAG 문서와 임베딩은 PostgreSQL/pgvector에 저장
- OpenAI와 Ollama 임베딩을 provider/model 단위로 동시 운영 가능
- Ollama `nomic-embed-text` 기반 로컬 임베딩 검색 지원
- Ollama / OpenAI 답변 provider 선택 가능
- SSE 스트리밍 답변 지원
- 사용자 AI 메모리 기반 검색 조건 보강 지원
- Kakao / Google OAuth2 로그인과 JWT 인증 구현

