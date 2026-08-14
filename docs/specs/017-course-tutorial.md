# 코스 등록 튜토리얼 완료 저장

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-14 | Draft | 최초 작성 (3차 업데이트 7번 — 튜토리얼 완료 여부). 저장(PATCH)·컬럼은 확정, **완료 여부를 어디서 조회할지는 미해결** |
| 2026-08-14 | Draft (후순위) | **등록 폼 응답에 싣는 A안 탈락** — 튜토리얼은 등록 폼을 *요청하기 전에* 띄울지 판단해야 한다. 전달 경로가 정해질 때까지 **구현을 후순위로 미룬다**([014](014-course-registration.md)~[016](016-course-approval.md) 먼저) |
| 2026-08-14 | Draft (후순위) | develop 병합 — 번호 016→**017**, 마이그레이션 V23→**V25**. [스펙 013](013-app-integration-refinements.md)에서 **로그인·토큰 재발급 응답에 `isOnboarded`가 추가**돼, 회원 화면 상태를 인증 응답으로 내리는 선례가 생겼다(미해결 질문 1의 B안 근거) |
| 2026-08-14 | Draft | **전달 경로 확정 — 로그인·토큰 재발급 응답에 포함**(B안). 다른 화면 튜토리얼은 없어 `member` 컬럼 1개 유지. **미해결이 모두 해소돼 후순위 사유가 사라졌다**(014~016 다음 순서로 구현) |
| 2026-08-14 | Implemented | 구현 완료 — V25 `member.course_tutorial_completed_at`, 완료 저장 PATCH, 로그인·토큰 재발급 응답 `isCourseTutorialCompleted` 추가 |

> **구현 순서상 마지막.** [014](014-course-registration.md)~[016](016-course-approval.md)를 먼저 구현하고 이 문서를 붙인다. 후순위 사유였던 "완료 여부 전달 경로"는 **로그인·토큰 재발급 응답에 포함**으로 확정돼 더 이상 대기 사유는 없다.

## 배경 / 목적

**코스 등록 버튼을 처음 눌렀을 때** 등록 방법을 설명하는 튜토리얼을 한 번 보여준다. 한 번 본 사용자에게 다시 뜨면 안 되고, **재설치·기기 변경에도 유지**돼야 하므로 앱 로컬 저장이 아니라 서버에 남긴다.

가입 직후가 아니라 **코스 등록을 처음 시도하는 시점**이라 온보딩([004](004-onboarding.md))과는 시점이 한참 떨어져 있다. 그래서 온보딩 테이블이 아니라 `member`에 컬럼 하나로 둔다.

### 범위 밖

- 튜토리얼 **콘텐츠**(문구·이미지·페이지 수) — 앱이 갖는다. 서버는 봤는지 여부만 안다.
- 다른 화면의 튜토리얼·코치마크 — 생기면 컬럼을 추가하거나 별도 테이블로 분리한다.
- 튜토리얼 다시 보기(완료 취소).

## 요구사항

### 기능 요구사항

1. **완료 저장**: 사용자가 튜토리얼을 끝내면 완료 시각을 저장한다. 여러 번 호출해도 **최초 완료 시각이 유지**된다.
2. **완료 여부 확인**: 앱이 코스 등록 버튼을 눌렀을 때 튜토리얼을 띄울지 판단할 수 있어야 한다. 튜토리얼이 등록 폼보다 먼저 뜨므로, 판단은 [등록 폼 API](014-course-registration.md)를 부르기 **전에** 끝나 있어야 한다 → **로그인·토큰 재발급 응답으로 미리 내려준다**.

### 비기능 요구사항

- **JWT 필수**(회원 기준 상태).
- 되돌리기가 없으므로 **멱등**한 단방향 연산이다.

## 도메인 모델 (마이그레이션 V25)

### member 확장

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| course_tutorial_completed_at | timestamp | N | 코스 등록 튜토리얼 완료 시각. **NULL = 아직 안 봄** |

```sql
ALTER TABLE member ADD COLUMN course_tutorial_completed_at TIMESTAMP;
```

- **boolean이 아니라 시각으로 둔다** — "언제 봤는지"가 남아 튜토리얼 개편 시점 전후를 구분할 수 있고, 비용은 같다. 기존 컬럼과 같은 `TIMESTAMP WITHOUT TIME ZONE`.
- 기존 회원은 NULL(안 본 것)으로 남는다. backfill 없음 — 이미 코스를 등록해본 사람이 없으므로 한 번 보는 게 맞다.
- **컬럼명을 코스 전용(`course_tutorial_...`)으로 좁힌다.** 지금은 튜토리얼이 하나뿐이라 범용 이름(`tutorial_completed_at`)도 가능하지만, 나중에 다른 화면 튜토리얼이 생기면 어느 것인지 알 수 없게 된다. 늘어나면 그때 별도 테이블로 분리한다.

## API 명세 (패키지 `domain.member`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| PATCH | /api/v1/members/me/course-tutorial | 코스 등록 튜토리얼 완료 저장(멱등) | JWT |

**조회 전용 엔드포인트는 만들지 않는다** — 완료 여부는 아래 2번처럼 **로그인·토큰 재발급 응답에 실어** 미리 내려준다.

**컨트롤러 배치**(CLAUDE.md 기준): 자체 오퍼레이션 묶음이 없는 **단발 엔드포인트**라 소유 컨트롤러인 **`MemberController`**에 둔다(`filter-tags` PUT과 같은 판단).

**메서드**: 회원의 여러 속성 중 하나만 바꾸는 부분 변경이라 `PATCH`. 호출을 반복해도 결과가 같아 `POST`가 아니다.

### 1. 튜토리얼 완료 저장

```json
// PATCH /api/v1/members/me/course-tutorial   (JWT)
// Request body 없음

// Response data
{ "courseTutorialCompletedAt": "2026-08-14T14:02:11" }
```

- **요청 본문이 없다.** 완료를 되돌리는 시나리오가 없어 `{ "completed": true/false }`를 받을 이유가 없다. 되돌리기가 생기면 그때 본문을 추가한다(하위 호환).
- **이미 완료한 회원이 다시 호출하면 기존 시각을 그대로 반환**한다(덮어쓰지 않음).

### 2. 완료 여부 전달 (기존 인증 응답 확장)

앱은 **코스 등록 버튼을 누르기 전에** 튜토리얼을 띄울지 알아야 한다. 그래서 조회 API를 새로 만들지 않고, 이미 부르는 인증 응답에 boolean 하나를 얹는다 — [스펙 013](013-app-integration-refinements.md)이 온보딩 완료 여부(`isOnboarded`)를 같은 자리에 실은 것과 같은 방식이다.

| DTO | 엔드포인트 | 추가 필드 |
|-----|------------|-----------|
| [SocialLoginResponse](../../src/main/java/cmc/rodi/global/auth/dto/SocialLoginResponse.java) | 소셜 로그인·복구 | `isCourseTutorialCompleted` |
| [TokenResponse](../../src/main/java/cmc/rodi/global/auth/dto/TokenResponse.java) | 토큰 재발급 | `isCourseTutorialCompleted` |

```json
// 소셜 로그인 응답 (발췌)
{
  "status": "SUCCESS",
  "accessToken": "...", "refreshToken": "...",
  "isNewMember": false,
  "isOnboarded": true,
  "isCourseTutorialCompleted": false,
  "nickname": "차근차근 토끼"
}
```

- **토큰이 없는 상태**(`WITHDRAWAL_PENDING`·`WITHDRAWAL_LOCKED`)와 **신규 가입**은 `false`다(`isOnboarded`와 같은 규칙).
- 재발급 응답에도 실리므로 앱이 토큰만 갱신하고 들어와도 값이 최신이다.
- 완료 처리(1번)는 그 기기에서 일어나므로, 앱은 **응답을 로컬에 캐시하고 완료 시 스스로 갱신**하면 된다. 재설치·기기 변경은 다음 로그인에서 동기화된다.

## 완료 조건 (Acceptance Criteria)

- [ ] 처음 호출하면 `course_tutorial_completed_at`이 현재 시각으로 저장되고 응답에 그 값이 담긴다.
- [ ] 다시 호출해도 **최초 완료 시각이 바뀌지 않고** 200이다.
- [ ] 미인증 요청은 401이다.
- [ ] 기존 회원은 마이그레이션 후 NULL(미완료)이다.
- [ ] 미완료 회원의 로그인·토큰 재발급 응답이 `isCourseTutorialCompleted=false`, 완료 후에는 `true`다.
- [ ] `WITHDRAWAL_PENDING`·`WITHDRAWAL_LOCKED` 응답과 신규 가입은 `false`다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 확정된 결정

- **`member.course_tutorial_completed_at`(timestamp)** — boolean 대신 시각, 코스 전용 이름. 다른 화면 튜토리얼은 예정에 없어 **컬럼 1개로 간다**.
- **`PATCH /api/v1/members/me/course-tutorial`, 본문 없음, 멱등** — 최초 완료 시각 보존.
- **`MemberController`에 배치** — 단발 엔드포인트.
- **완료 여부는 로그인·토큰 재발급 응답에 실어 미리 내린다** — 조회 전용 엔드포인트를 만들지 않는다. 등록 폼 응답에 싣는 안은 순서가 맞지 않아 탈락했다(폼을 부른 뒤에야 알게 된다).

## 검토했던 대안 (기록)

완료 여부를 앱에 전달하는 방식으로 네 가지를 놓고 비교했다. **제약**은 튜토리얼이 등록 폼 화면보다 먼저 뜬다는 것 — 완료 여부는 **등록 폼 API를 요청하기 전에** 앱이 알고 있어야 한다.

| 안 | 방식 | 추가 왕복 | 결론 |
|----|------|-----------|------|
| A | 별도 `GET /members/me/course-tutorial` | 등록 버튼마다 1회 | 완료 이후에도 계속 호출돼 아깝다 |
| **B** | **로그인·토큰 재발급 응답에 포함** + 앱 로컬 캐시 | **0회** | **채택** |
| C | 등록 버튼이 있는 화면의 기존 응답에 포함 | 0회 | B와 같은 성격이나 진입 화면 확정이 선행돼야 한다 |
| D | 등록 폼 응답에 포함 | 0회 | **탈락** — 폼을 부른 뒤에야 알게 돼 순서가 맞지 않는다 |

B를 고른 이유: 왕복이 늘지 않으면서 제약을 만족하고, [스펙 013](013-app-integration-refinements.md)이 온보딩 완료 여부(`isOnboarded`)를 같은 자리에 실은 선례가 있다 — 성격이 같은 값(회원별 1회성 화면 분기 플래그)이라 한곳에 모으는 게 일관적이다.

## 미해결 질문

- (없음 — 전달 경로 확정, 다른 화면 튜토리얼 없음)

## 범위 밖 / 다음

- 튜토리얼 다시 보기, 튜토리얼 버전 관리(개편 시 재노출).
- 다른 화면 튜토리얼 → 늘어나면 `member_tutorial` 테이블로 분리.
