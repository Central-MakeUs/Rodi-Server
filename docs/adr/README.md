# Architecture Decision Records (ADR)

이 디렉토리는 Rodi의 **아키텍처 결정 기록(ADR)**을 보관합니다. 중요한 아키텍처 결정을 *왜 그렇게 했는지*와 함께 남깁니다.

## 규칙

- 파일명: `NNNN-제목-슬러그.md` (예: `0001-package-by-feature.md`)
- 번호는 4자리, 1씩 증가하며 재사용하지 않는다.
- 새 ADR은 [TEMPLATE.md](TEMPLATE.md)를 복사해 작성한다.
- 한 번 `Accepted`된 ADR은 거의 수정하지 않는다. 결정이 바뀌면 새 ADR을 작성하고 기존 ADR의 상태를 `Superseded`로 바꾼다.

## 상태 (Status) 값

- `Proposed` — 논의 중
- `Accepted` — 채택, 현재 유효
- `Deprecated` — 더 이상 유효하지 않음
- `Superseded` — 다른 ADR로 대체됨

## 목록

| 번호 | 제목 | 상태 |
|------|------|------|
| [0001](0001-package-by-feature-and-gradual-hexagonal.md) | 패키지 구조 — package-by-feature에서 점진적 헥사고날로 | Accepted |
| [0002](0002-place-joined-inheritance.md) | Place 상속 전략 — JOINED | Accepted |
| [0003](0003-region-hierarchy-and-postgis.md) | 지역 처리 — 계층 region 테이블 + PostGIS | Accepted |
| [0004](0004-member-soft-delete.md) | 회원 탈퇴 — soft delete + PII 익명화 | Accepted |
| [0005](0005-hosting-aws-lightsail.md) | 배포 인프라 및 CI/CD | Accepted |
| [0006](0006-code-quality-tools.md) | 코드 품질·의존성 도구 | Accepted |
| [0007](0007-flyway-db-migration.md) | DB 스키마 마이그레이션 — Flyway | Accepted |
| [0008](0008-social-login.md) | 소셜 로그인 전략 | Accepted |
| [0009](0009-authentication-authorization.md) | 사용자 인증/인가 전략 | Accepted |
