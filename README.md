# Wooriport Backend

마이데이터 기반 급여 포트폴리오 자동화 서비스의 백엔드 API 서버입니다.
급여 입금을 감지해 포트폴리오 이체 계획을 자동 생성하고, AI가 투자 성향을 분석해 자산 처방전을 제안합니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | PostgreSQL 16 (pgvector) |
| Cache | Redis 7.2 |
| Messaging | Apache Kafka 3.7 |
| Batch | Spring Batch |
| Security | Spring Security + JWT (jjwt 0.12) |
| AI 연동 | WebClient → FastAPI (ML 서버) |
| 모니터링 | Micrometer + Prometheus + Grafana |
| 로깅 | Logstash (ELK Stack) |
| API 문서 | SpringDoc OpenAPI 3 (Swagger UI) |
| 빌드 | Gradle |

---

## 시스템 아키텍처

```
Frontend (React)
    │
    ▼
Spring Boot API Server (8080)
    ├── Spring Security (JWT Filter)
    ├── REST Controllers
    ├── Service Layer
    │   ├── FastAPI ML Server ◄── WebClient (비동기)
    │   ├── Redis ◄──────────── 챌린지 진행도 실시간 캐싱
    │   └── PostgreSQL ◄─────── JPA / Spring Batch
    │
    ├── Kafka Consumer (transaction-events)
    │   └── 배치 poll 처리 → 챌린지 진행 / 급여 감지 → 이체 계획
    │
    └── Spring Batch Schedulers
        ├── 매일 00:00  — 급여 이체 Job
        ├── 매주 월요일 — 자산 스냅샷 Job
        └── 매월 1일   — 월간 리포트 Job

Kafka ◄──────────── 거래 이벤트 발행 (외부 마이데이터)
ELK Stack ◄──────── 구조화 로그 수집
Prometheus/Grafana ◄ 메트릭 수집 및 시각화
```

---

## 핵심 기능

### 1. 마이데이터 자산 연동
- 더미 마이데이터(은행·카드·증권사) 기반 계좌 연동
- 우리은행 / 타행 구분, 급여 통장 지정
- 자동이체 계좌 설정 (타행 급여 → 우리은행 자동 이체)

### 2. AI 에이전트 (porTI)
- **투자 성향 설문(porTI)**: 10문항 답변 → 성향 유형 계산 및 저장
- **AI 진단 리포트**: 3개월 소비 데이터 + 투자 성향 → FastAPI에 전달, AI 코멘트 3개 생성
- **월급 리밸런싱 추천**: 월급·고정지출·소비 패턴을 FastAPI로 전달, 최적 배분 계획 수신
- **AI 자산 처방전**: porTI 유형·보유 자산·상품 카탈로그 → FastAPI, `portfolio_flows` 자동 생성

### 3. 포트폴리오 관리
- 월 투자 금액 및 계좌별 배분 설정 (PATCH 시 전체 삭제 후 재생성)
- 포트폴리오 플로우: 자산 흐름 경로 및 상품 구성 관리
- 리밸런싱: 포트폴리오 비율 변경 시 `portfolio_flows.amount` 자동 재계산

### 4. 급여 감지 → 이체 계획 자동화
- Kafka 거래 이벤트에서 급여 카테고리 감지 (급여/월급/임금/salary)
- 급여 입금 시 `transfer_plans` 자동 생성 (포트폴리오 배분 기반)
- 이체 실패 → Kafka Retry Topic 발행 → DLT(Dead Letter Topic) 격리 → 관리자 재처리

### 5. 미니 챌린지
- AI가 소비 패턴 분석 후 챌린지 제안 (배달·쇼핑·음주 등 절감 목표)
- Redis로 실시간 진행도 증분, 주기적으로 DB 동기화
- 7일 만료 시 스케줄러가 판정 → 성공/실패 알림 발송
- 진행도 임계값(50%, 80%, 90%) 도달 시 중간 알림

### 6. 월간 리포트
- Spring Batch로 매월 1일 전체 활성 사용자 대상 자동 생성
- 카테고리별 소비 집계 + FastAPI AI 코멘트 결합

### 7. 세금 혜택 & 주식 조회
- IRP/연금저축 납입 현황 기반 세액공제 예상액 계산 (16.5% / 13.2%)
- Yahoo Finance API 연동으로 주식 종목 상세 정보 조회

---

## 기술적 도전

### Kafka 배치 처리 최적화

거래 이벤트 대용량 처리를 단계적으로 개선했습니다.

| Phase | 방식 | 문제점 |
|-------|------|--------|
| Phase 1 | 메시지 1건당 SELECT + saveAndFlush | DB 병목 |
| Phase 2 | `@KafkaListener(batch=true)` — poll 단위 배치 | ✅ 채택 |
| Phase 3 | 파티션 6 × 컨슈머 6 병렬 처리 | ✅ 채택 |

**Phase 2 배치 처리 흐름:**
1. Intra-batch dedup — poll 내 중복 첫 번째만 통과
2. DB dedup — `findExistingEventIds` 1쿼리
3. Asset 일괄 조회 — `findByAssetNumberIn` 1쿼리
4. `saveAll` — Hibernate `jdbc.batch_size: 100` 배치 INSERT

**후속 처리:** userId별 그룹핑 후 parallelStream — 같은 사용자 내 순서 보장, 다른 사용자 간 병렬

### Kafka DLT 급여 재처리

급여 처리 실패 시 즉시 재시도 대신 별도 토픽으로 격리해 안정성 확보:

```
급여 처리 실패
    → salary-retry-topic 발행 (attempt 1)
    → SalaryRetryConsumer: 최대 3회 재시도
    → 3회 초과 → salary-dlt 격리
    → 관리자 API로 조회 및 수동 재처리
```

`handleIfSalary`는 미확인 플랜 delete + 재생성 구조로 멱등 보장

### Redis + DB 이중 저장 챌린지

- **Redis**: 거래 이벤트 수신 시 즉시 진행도 증분 (빠른 응답)
- **DB**: 스케줄러 또는 만료 시점에 최종 동기화 (내구성)
- Redis 장애 시 `recomputeFromDb`로 DB에서 재계산 fallback

### Prometheus 메트릭

거래 파이프라인 전 구간을 p50/p95/p99 퍼센타일로 관찰:

| 메트릭 | 설명 |
|--------|------|
| `tx.e2e.latency` | Kafka 발행 → 컨슈머 처리 완료 E2E 지연 |
| `tx.persist` | DB 배치 INSERT 소요 시간 |
| `tx.challenge` | 챌린지 진행도 업데이트 소요 시간 |
| `tx.salary` | 급여 감지 → 이체 계획 생성 소요 시간 |
| `tx.batch.size` | poll 단위 배치 크기 분포 |

---

## 주요 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 (JWT 발급) |
| POST | `/api/v1/assets/sync` | 마이데이터 자산 연동 |
| POST | `/api/v1/agent/profile` | AI 진단 리포트 생성 (porTI) |
| POST | `/api/v1/agent/rebalance` | 월급 리밸런싱 추천 |
| POST | `/api/v1/agent/prescriptions` | AI 자산 처방전 생성 |
| GET | `/api/v1/portfolios` | 포트폴리오 조회 |
| PATCH | `/api/v1/portfolios` | 포트폴리오 수정 |
| POST | `/api/v1/transfer-plans/generate` | 이체 계획 수동 생성 |
| GET | `/api/v1/transfer-plans` | 이체 계획 조회 |
| POST | `/api/v1/challenges` | 챌린지 생성 |
| GET | `/api/v1/dashboard` | 통합 자산 대시보드 |
| GET | `/api/v1/reports` | 월간 리포트 목록 |

> Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 실행 방법

### 1. 인프라 실행 (Docker)

```bash
cd infra
docker-compose up -d postgres redis kafka
```

### 2. 환경 변수 설정

`src/main/resources/application-secret.yml` 생성:

```yaml
spring:
  datasource:
    username: wooriport
    password: wooriport1234
  security:
    jwt:
      secret: <your-jwt-secret>
```

### 3. 앱 실행

```bash
./gradlew bootRun
```

---

## 프로젝트 구조

```
src/main/java/com/wooriport/core_api/
├── config/          # Security, Kafka, Redis, WebClient, Swagger 설정
├── controller/      # REST 컨트롤러 (14개 도메인)
├── service/         # 비즈니스 로직
├── domain/          # JPA 엔티티
├── repository/      # Spring Data JPA
└── base/
    ├── batch/       # Spring Batch Job/Scheduler/Tasklet
    ├── dto/         # 요청·응답 DTO
    └── exception/   # 커스텀 예외 및 GlobalExceptionHandler
```
