# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개요

Rodi는 **초보 운전자를 위한 맞춤형 연습 장소 및 코스 탐색 서비스**의 백엔드입니다. 위치기반(geospatial) 탐색이 핵심입니다.

## 기술 스택

| 분류 | 사용 기술 |
|------|-----------|
| 언어/빌드 | Java 21, Gradle (wrapper) |
| 프레임워크 | Spring Boot 3.5 |
| 영속성 | Spring Data JPA + Hibernate Spatial, PostgreSQL/PostGIS |
| 인증 | OAuth2 Client + JWT |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 기타 | Lombok, DevTools, Actuator, Bean Validation |

## 명령어

```bash
./gradlew bootRun                                    # 로컬 실행
./gradlew build                                      # 빌드 (테스트 포함)
./gradlew test                                       # 전체 테스트
./gradlew test --tests "cmc.rodi.SomeTest.method"    # 단일 테스트
```

## 아키텍처

DDD + **package-by-feature**로 시작, 도메인이 복잡해지면 feature 단위로 **점진적 헥사고날** 전환. 핵심 원칙: ① 로직은 service에 ② repository는 인터페이스로 ③ 규칙은 entity에.

## 작업 순서

새 기능·도메인 변경은 다음 순서를 따른다 (작은 수정은 생략 가능):

1. **탐색** — 관련 `docs/`·코드를 먼저 읽어 맥락 파악
2. **계획** — plan 모드로 접근법을 제시하고 합의
3. **스펙** — `docs/specs/<기능>.md`에 요구사항·API·도메인·완료 조건 작성
4. **확인** — 스펙을 사용자가 리뷰·승인 (이 전에 구현 시작 금지)
5. **구현** — 스펙대로 코드 + 테스트 작성
6. **검증** — `./gradlew test` 통과 확인, 필요 시 실행으로 동작 검증

## 작업 원칙

- **확실한 설계 이전에 코드를 짜지 않는다.** 코드/설정 작성 전 plan모드로 접근법을 먼저 제시하고 승인받은 뒤 구현한다. 불명확하면 코드보다 설계/질문을 먼저.
- **테스트를 거쳐 통합한다.** `./gradlew test` 통과 + 관련 테스트 작성 후 머지.
- **커밋 전 커밋 메시지를 사용자에게 보여주고 승인받은 뒤 커밋한다.** 제목은 간결히, 상세는 바디에.

## 참고

- 루트 패키지 `cmc.rodi`, 진입점 [RodiApplication.java](src/main/java/cmc/rodi/RodiApplication.java).
- 문서 — 데이터 모델: [docs/erd.md](docs/erd.md) · 아키텍처 결정: [docs/adr/](docs/adr/) · 기능 스펙: [docs/specs/](docs/specs/) ([템플릿](docs/specs/TEMPLATE.md)). 작업 전 먼저 확인.
- 시크릿(DB·OAuth2·JWT)은 환경변수/프로파일로 주입, 커밋 금지.
