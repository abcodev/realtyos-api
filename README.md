# RealtyOS API

부동산 실거래 데이터를 기반으로 자연어 검색, RAG 답변, 후보 비교 분석을 제공하는 AI 부동산 의사결정 백엔드 프로젝트입니다.

사용자는 “개포동 시세 어때”, “대치동과 역삼동 비교해줘”, “마포에서 갈아타기 후보를 찾아줘”처럼 자연어로 질문합니다. RealtyOS는 실거래 데이터를 검색 가능한 문서와 임베딩으로 변환하고, 질문 의도에 따라 RAG 검색 또는 실거래 기반 의사결정 엔진으로 라우팅해 답변과 근거 거래를 함께 제공합니다.

<img width="3688" height="2645" alt="image" src="https://github.com/user-attachments/assets/5658ba46-ae95-4c2c-b3e1-25279171d6cd" />

---

## Project Summary

| 항목 | 내용 |
| --- | --- |
| 프로젝트 성격 | AI 부동산 검색/분석 백엔드 |
| 핵심 도메인 | 아파트 실거래가, 지역/단지 비교, 매수 후보 분석 |
| 주요 기술 | Java 21, Spring Boot, PostgreSQL, pgvector, OpenAI, Ollama, SSE |
| 인증 | Kakao/Google OAuth2, JWT |
| 주요 구현 | RAG 파이프라인, 임베딩 적재, AI 라우팅, 의사결정 엔진, 사용자 AI 메모리 |

---

## Why This Project

부동산 실거래 데이터는 양이 많고, 사용자가 원하는 질문은 정형 검색 조건으로만 표현되지 않습니다.

예를 들어 사용자는 다음처럼 묻습니다.

```text
강남구 30평대 20억 이하 아파트
개포동 시세 어때
대치동과 역삼동 시세 비교
마포에서 갈아타기 후보 추천
```

이 프로젝트는 이런 자연어 질문을 실제 거래 데이터 기반의 검색 조건과 분석 결과로 변환하는 것을 목표로 했습니다. 단순히 LLM에게 답변을 맡기는 방식이 아니라, 서버가 실거래 데이터를 직접 검색하고 집계한 뒤 LLM은 설명과 요약을 보조하도록 설계했습니다.

---

## Core Features

### 1. 실거래 기반 RAG 검색

국토교통부 실거래 데이터를 RAG 문서로 변환하고, provider/model별 임베딩을 생성해 PostgreSQL pgvector에 저장합니다.

- 실거래 데이터 수집
- RAG 문서 생성
- OpenAI/Ollama 임베딩 생성
- pgvector cosine distance 기반 검색
- 검색 결과 부족 시 실거래 테이블 기반 fallback 검색

### 2. 자연어 조건 해석

사용자 질문에서 부동산 도메인 조건을 추출합니다.

- 지역: `강남구`, `개포동`, `잠실`, `마포`
- 면적: `20평대`, `30평대`, `10평 이상`
- 가격: `20억 이하`, `4억 미만`
- 기간/정렬: `최근`, `거래 흐름`
- 의도: 시세, 비교, 추천, 단순 검색

전국 지역을 코드에 alias로 하드코딩하지 않고, 법정동/시군구 코드 테이블 기반 `RegionResolver`로 해석하도록 개선했습니다.

### 3. 단일 API 내부 라우팅

프론트엔드는 하나의 질문 API만 호출합니다.

```text
POST /api/v1/rag/ask
POST /api/v1/rag/ask/stream
```

서버 내부에서는 `RagAnswerRouter`가 질문 의도를 분류합니다.

| Route | 처리 방식 |
| --- | --- |
| `SEARCH` | RAG 검색 후 LLM 답변 |
| `MARKET_PRICE` | 실거래 기반 시세 분석 |
| `COMPARISON` | 대상별 거래 요약/비교 |
| `RECOMMENDATION` | 후보 추천 및 점수화 |

API를 여러 개로 나누지 않고 내부 라우팅으로 처리해, 클라이언트 복잡도를 줄였습니다.

### 4. 실거래 의사결정 엔진

비교/추천/시세 질문은 LLM 자유 생성에만 의존하지 않고, 서버가 실거래 후보를 직접 집계하고 점수화합니다.

점수화 기준:

- 예산 적합도
- 면적 적합도
- 최근 거래 여부
- 거래 건수
- 평균 평당가

비교 질문은 각 대상별로 후보를 분리하고, 평균가/중위가/최근가/거래량/평당가를 표 형태로 제공합니다.

### 5. Multi AI Provider

OpenAI와 Ollama를 모두 사용할 수 있도록 provider/model 선택 구조를 분리했습니다.

- OpenAI 임베딩
- Ollama 임베딩
- OpenAI 답변 생성
- Ollama 답변 생성
- 로컬 테스트용 Ollama와 고품질 답변용 OpenAI 전환 가능

임베딩과 답변 provider를 분리해 운영 비용과 품질을 상황에 맞게 조절할 수 있습니다.

### 6. SSE 스트리밍

LLM 응답 시간이 길어질 수 있어 SSE 기반 스트리밍 API를 구현했습니다.

스트리밍 이벤트:

```text
memory_loaded
route_selected
retrieval_started
retrieval_completed
model_selected
token
completed
```

프론트엔드는 `token` 이벤트를 누적해 답변을 실시간으로 표시할 수 있습니다.

### 7. 사용자 AI 메모리

로그인 사용자의 질문 이력과 선호 조건을 저장해 다음 검색에 활용합니다.

- 최근 질문
- 관심 지역
- 가격대
- 검색 조건
- 당시 답변과 근거 거래 snapshot

최근 질문을 클릭했을 때 단순 재요청이 아니라, 당시 답변을 그대로 확인할 수 있도록 설계했습니다.

---

## Architecture

```text
Open Real Estate Data
        |
        v
Deal Sync / Normalize
        |
        v
PostgreSQL
        |
        +----------------------+
        |                      |
        v                      v
RAG Document Builder     Decision Engine
        |                      |
        v                      |
Embedding Builder              |
OpenAI / Ollama                |
        |                      |
        v                      |
pgvector Search                |
        |                      |
        +----------+-----------+
                   v
            Answer Router
                   |
                   v
         RAG Answer / SSE Response
```

---

## Backend Design

### Layered Structure

프로젝트는 도메인 중심으로 패키지를 나누고, application/domain/infrastructure/interfaces 책임을 분리했습니다.

```text
auth
OAuth2, JWT, 로그인, 토큰 재발급

rag
RAG 문서, 임베딩, 검색, 답변 생성, SSE, 사용자 AI 메모리

realestate
실거래 데이터 처리, 지역 해석, 후보 분석, 의사결정 점수화

user
사용자 정보와 설정
```

### Architecture Improvements

개발 과정에서 다음 구조 개선을 적용했습니다.

- application service가 infrastructure 구현체에 직접 의존하지 않도록 port 분리
- OAuth 응답 DTO와 application command/result 분리
- JPA Entity에서 JSON 직렬화 책임 제거
- 지역명 해석을 `RegionResolver`로 집중화
- 의사결정 답변 포맷과 통계 요약 계산을 별도 컴포넌트로 분리
- RAG 답변 흐름에 내부 router를 추가해 질문 의도별 처리 분기 명확화

---

## Main APIs

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/rag/search` | RAG 문서 검색 |
| POST | `/api/v1/rag/ask` | 자연어 질문 답변 |
| POST | `/api/v1/rag/ask/stream` | SSE 기반 자연어 질문 답변 |
| GET | `/api/v1/rag/documents/stats` | RAG 인덱스 상태 조회 |
| GET | `/api/v1/rag/memory/me` | 사용자 AI 메모리 조회 |
| GET | `/api/v1/rag/memory/me/events` | 사용자 질문/답변 히스토리 조회 |
| GET | `/api/v1/auth/login/{provider}` | OAuth2 로그인 시작 |
| GET | `/api/v1/auth/token/exchange` | OAuth code를 JWT로 교환 |
| POST | `/api/v1/auth/reissue` | 토큰 재발급 |

---

## Problem Solving

### 대량 임베딩 처리

실거래 데이터가 계속 증가하는 상황을 고려해 RAG 문서와 임베딩을 분리했습니다. 문서 변경 시 기존 임베딩을 무효화하고, 누락 임베딩만 다시 생성할 수 있도록 구성했습니다.

### OpenAI 요청 크기 제한 대응

임베딩 요청이 token 제한을 넘지 않도록 문서 단위 입력을 정제하고 batch 단위로 나눠 처리했습니다.

### 지역 검색 품질 개선

초기에는 `역삼`, `대치`, `마포` 같은 입력에서 엉뚱한 지역이나 단지가 섞이는 문제가 있었습니다. 이를 해결하기 위해 법정동/시군구 코드 기반 지역 해석을 추가하고, 비교 질문은 대상별로 검색 후보를 분리하도록 개선했습니다.

### LLM Hallucination 방어

LLM이 근거에 없는 지역이나 단지를 만들어내지 않도록 guardrail을 추가했습니다. 근거가 부족한 경우에는 답변 생성을 제한하고, 비교/추천 질문은 서버가 실거래 기반 표를 직접 생성하도록 했습니다.

### SSE 실시간 응답 복구

공통 response wrapper가 SSE 응답을 버퍼링해 토큰이 한 번에 내려오는 문제가 있었습니다. 스트리밍 endpoint는 wrapper 대상에서 제외해 실시간 전송을 복구했습니다.

---

## Result

현재 RealtyOS API는 다음 흐름을 제공합니다.

- 실거래 데이터를 RAG 문서와 임베딩으로 변환
- OpenAI/Ollama provider 기반 검색 및 답변 생성
- 자연어 질문에서 지역/가격/면적/기간/의도 추출
- 단일 API 내부 라우팅으로 검색/시세/비교/추천 처리
- 실거래 기반 후보 점수화와 비교표 생성
- SSE 기반 답변 스트리밍
- 사용자별 AI 메모리와 답변 히스토리 저장
- Kakao/Google OAuth2 로그인과 JWT 인증

---

## Portfolio Focus

이 프로젝트에서 중점적으로 보여주고자 한 역량은 다음과 같습니다.

- RAG를 단순 데모가 아니라 실제 도메인 데이터 검색/분석 흐름으로 구성
- LLM 답변 품질 문제를 deterministic 분석 엔진과 guardrail로 보완
- 대량 데이터, 임베딩, provider 선택, 비용/성능 trade-off를 고려한 설계
- Spring Boot 기반 인증, API, persistence, streaming 기능의 end-to-end 구현
- 기능 추가 이후에도 계층 의존과 책임 분리를 지속적으로 개선하는 리팩터링
