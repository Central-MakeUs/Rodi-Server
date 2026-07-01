# ADR 0004: 회원 탈퇴 — soft delete + PII 익명화

## 상태 (Status)

Accepted (2026-07-01)

## 배경

회원이 남긴 콘텐츠(운전 기록, 추후 리뷰)와의 참조 무결성을 유지하면서, **개인정보보호법상 탈퇴 시 개인정보(PII)는 파기**해야 한다. 두 요구를 동시에 만족할 탈퇴 정책이 필요하다.

## 결정

**soft delete(`member.deleted_at`) + 탈퇴 시 PII 익명화/파기를 병행한다.**

- `member.deleted_at`(nullable, null=활성)로 논리 삭제 — member 행을 남겨 FK 무결성 유지.
- 탈퇴 처리 시 개인정보(nickname·email·profile_image·oauth·driving_goal 등)는 익명화/파기(애플리케이션 로직 책임).
- 조회는 기본적으로 `deleted_at IS NULL` 필터.
- 추후 리뷰는 **유지 + 작성자 "탈퇴한 회원"으로 익명 표시**.

## 결과

### 긍정적

- 리뷰·기록의 참조 무결성 유지, 통계 보존.
- 실수 탈퇴 복구·어뷰징 대응 여지.

### 부정적

- 모든 조회에 `deleted_at` 필터 필요(누락 시 유령 데이터 노출).
- 재가입 시 `oauth` 유니크 충돌 처리 필요(익명화 시 값 치환 등).

### 중립적

- PII 익명화 규칙은 DB가 아닌 애플리케이션 계층에서 관리.

## 검토한 대안

### 대안 1 — hard delete

물리 삭제. 리뷰·기록이 함께 사라지거나 FK가 깨지고 통계가 손실돼 선택하지 않음.

## 참조

- [docs/erd.md](../erd.md)
- [ADR 0002](0002-place-joined-inheritance.md) (찜·리뷰 참조 대상)
