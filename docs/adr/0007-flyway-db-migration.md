# ADR 0007: DB 스키마 마이그레이션 — Flyway

## 상태 (Status)

Accepted (2026-07-03)

## 배경

기능 개발이 시작되면 엔티티가 추가되며 스키마가 계속 바뀐다. 로컬과 운영의 스키마를 일치시키고 변경 이력을 코드로 관리해야 한다. Hibernate `ddl-auto`에만 의존하면 환경 간 드리프트·운영 사고 위험이 크다.

## 결정

**Flyway로 스키마를 버전 관리하고, Hibernate는 검증만 한다.**

- 의존성: `flyway-core` + `flyway-database-postgresql`.
- `ddl-auto: validate`(로컬·운영 공통) → 스키마의 단일 소스는 Flyway.
- 마이그레이션: `src/main/resources/db/migration/V{n}__*.sql`. 첫 마이그레이션 `V1__enable_postgis.sql`(PostGIS 확장, 멱등).
- **baseline 설정**: `baseline-on-migrate: true`, `baseline-version: 0`.
  - PostGIS 이미지가 만든 기존 객체(`spatial_ref_sys` 등)로 스키마가 완전히 비어있지 않아도 안전하게 시작.
  - 기본 `baseline-version`은 1이라 V1이 스킵되므로 0으로 낮춰 V1부터 적용.

## 결과

### 긍정적

- 스키마 변경이 코드로 추적·리뷰됨. 로컬·운영 스키마 일관성.
- 새 엔티티 추가 시 마이그레이션을 함께 작성하지 않으면 로컬 `validate` 실패 → 규율 강제.
- 앱 시작 시 자동 적용(로컬·운영 동일 경로), 테스트(Testcontainers)에서도 실행돼 마이그레이션이 검증됨.

### 부정적

- 마이그레이션 파일 관리 부담. 한 번 머지된 마이그레이션은 수정 불가(새 버전으로).

### 중립적

- PostGIS 확장은 이미지가 선반영하는 경우가 많아 V1은 `IF NOT EXISTS`로 멱등 처리.

## 검토한 대안

### 대안 1 — Hibernate `ddl-auto=update`

간편하나 변경 이력·리뷰 불가, 환경 간 드리프트·운영 데이터 사고 위험으로 선택하지 않음.

### 대안 2 — Liquibase

기능은 충분하나 XML/YAML 변경셋보다 순수 SQL 기반 Flyway가 더 단순·직관적이라 Flyway 선택.

## 참조

- [ADR 0003](0003-region-hierarchy-and-postgis.md) (PostGIS)
- [build.gradle](../../build.gradle) · [db/migration](../../src/main/resources/db/migration/)
