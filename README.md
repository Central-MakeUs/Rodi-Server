# Rodi

**초보 운전자를 위한 맞춤형 연습 장소 및 코스 탐색 서비스**의 백엔드. 위치기반(geospatial) 탐색이 핵심입니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어/빌드 | Java 21, Gradle |
| 프레임워크 | Spring Boot 3.5 |
| 영속성 | Spring Data JPA + Hibernate Spatial, PostgreSQL/PostGIS, Flyway |
| 인증 | OAuth2 Client + JWT |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 품질 | Spotless, SonarCloud, JaCoCo, CodeRabbit |
| 인프라 | Docker Compose, Caddy, GitHub Actions, AWS Lightsail |

## 로컬 실행

**사전 요구**: Docker Desktop, JDK 21

```bash
cp .env.example .env          # 로컬 DB 값 확인/수정
./gradlew bootRun             # spring-boot-docker-compose가 DB 자동 기동·연결
```

- API 문서(Swagger): http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

## 테스트

```bash
./gradlew build   # 컴파일 + 테스트(Testcontainers) + Spotless + JaCoCo
./gradlew spotlessApply   # 포맷 자동 정리
```

## 아키텍처

DDD + package-by-feature로 시작, 도메인이 복잡해지면 feature 단위로 점진적 헥사고날 전환.
핵심 원칙: ① 로직은 service ② repository는 인터페이스 ③ 규칙은 entity.

- 데이터 모델: [docs/erd.md](docs/erd.md)
- 아키텍처 결정: [docs/adr/](docs/adr/)
- 기능 스펙: [docs/specs/](docs/specs/)
- 개발 가이드: [CLAUDE.md](CLAUDE.md)

## 브랜치 & 배포

- 기능 개발: `feat/#이슈번호-기능` → `develop` PR → 리뷰·CI 통과 후 머지
- 릴리즈: `develop` → `main` PR → 머지 시 **GitHub Actions가 이미지 빌드 → GHCR → Lightsail 자동 배포**
- 배포 인프라 결정: [ADR 0005](docs/adr/0005-hosting-aws-lightsail.md)
