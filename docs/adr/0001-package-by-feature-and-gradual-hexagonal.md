# ADR 0001: 패키지 구조 — package-by-feature에서 점진적 헥사고날로

## 상태 (Status)

Accepted (2026-07-01)

## 배경

Rodi는 위치기반(geospatial) + 인증(OAuth2/JWT) 등으로 도메인이 커질 백엔드다.

- 레이어형(`controller`/`service`/`repository`를 최상위)은 기능이 늘면 한 기능 수정에 여러 패키지를 오가야 하고 도메인 경계가 흐려진다.
- 초기부터 풀 헥사고날/클린 아키텍처를 도입하면 port·mapper·usecase 보일러플레이트가 과도해 오버엔지니어링이 된다.

## 결정

**DDD 개념 + package-by-feature로 시작하고, 도메인이 복잡해지는 시점에 feature 단위로 점진적으로 헥사고날/클린 아키텍처로 전환한다.**

- 1단계: `global`(횡단 관심사: config·common·exception·security) + `domain/<feature>`(controller·service·repository·entity·dto).
- 전환 원칙: ① 비즈니스 로직은 service(추후 usecase)에 ② repository는 인터페이스로(추후 port) ③ 핵심 규칙은 entity에.
- 2단계(복잡해지면): feature별 `domain`(순수 모델) / `application`(usecase·port) / `adapter`(in·out) 로 분리.

## 결과

### 긍정적

- 초기 보일러플레이트를 최소화하면서 도메인 경계를 명확히 유지.
- 무거워지는 도메인부터 하나씩 점진 전환 가능(재배치+인터페이스 추출 수준).

### 부정적

- "언제 2단계로 전환할지" 판단을 사람이 해야 한다.

### 중립적

- 1단계와 2단계 도메인이 한동안 공존할 수 있다.

## 검토한 대안

### 대안 1 — 레이어형(전통적 3계층)

`controller`/`service`/`repository`를 최상위 패키지로. 초기엔 단순하나 기능 증가 시 경계가 흐려지고 한 기능이 여러 패키지에 흩어져 선택하지 않음.

### 대안 2 — 초기부터 풀 헥사고날

port/adapter/usecase를 처음부터 전면 적용. 초기 단계에 보일러플레이트가 과도해 오버엔지니어링이라 선택하지 않음.

## 참조

- [docs/erd.md](../erd.md)
- CLAUDE.md "아키텍처" 섹션
