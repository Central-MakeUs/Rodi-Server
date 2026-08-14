# 앱 연동 보완 (후기 편집 · 레벨별 방문 인증 · 로그인 응답)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-13 | Draft | 최초 작성 — 프론트 연동 중 나온 6건 + 주차장 측정값 처리 |
| 2026-08-13 | Draft | 리뷰 반영 — 연습기록 삭제 API 제거, 내부 테스트용 즉시 탈퇴 API 추가, 연습 목록 `visitedAt` → `lastActivityAt`. 미해결 질문 1~4 해소 |

## 배경 / 목적

앱 연동을 진행하며 프론트에서 보완 요청이 왔고, 검토 과정에서 서버가 발의한 정리 건이 붙었다. 새 기능이 아니라 **이미 구현된 스펙 010·011·012·001·003의 결손·불일치를 메우는 작업**이다.

한 스펙으로 묶는 이유는 세 가지다.

1. 절반이 **스키마를 건드린다** — `review.content` NULL 허용(V22), `member_practice` 인증 컬럼 교체(V23).
2. **응답 필드가 삭제되는 변경**이 둘 있어(`isVerified`, 재가입 잠금 응답) 프론트 배포와 순서를 맞춰야 한다. 흩어놓으면 조율이 어렵다.
3. 후기 인증 배지 정책 변경(3번)이 011의 인증 모델과 010의 작성 로직에 동시에 걸쳐 있어, 한 문서에서 봐야 앞뒤가 맞는다.

| # | 요청 | 성격 | 관련 스펙 |
|---|------|------|-----------|

| 1 | 후기 수정 화면에 채울 기존 값을 받을 API가 없다 | 신규 API | 010 |
| 2 | 후기 내용을 필수에서 선택으로 | 검증·스키마 변경 | 010 |
| 3 | 방문 인증을 **레벨마다 새로** 받게 | 정책 변경 | 011 · 010 |
| 4 | 연습 목록·방문 응답의 인증 필드 제거 | 응답 축소 | 011 |
| 5 | 로그인 응답에 온보딩 완료 여부 | 응답 확장 | 001 · 004 |
| 6 | 재가입 잠금 시 가능 시각을 값으로 | 응답 구조 변경 | 003 |
| 7 | 주차장은 측정값을 아예 무시 (서버 발의) | 정리 | 011 · 012 |
| 8 | 연습기록 삭제 API 제거 | 엔드포인트 삭제 | 011 |
| 9 | 내부 테스트용 즉시 탈퇴 API | 신규 API(비운영) | 003 |
| 10 | 연습 목록 `visitedAt` → `lastActivityAt` | 필드 의미 변경 | 011 |

1~6·8·10은 프론트 요청, 7은 서버 발의, 9는 테스트 편의를 위한 비운영 API다.

### 범위 밖

- **장소별 "수정 가능한 내 후기" 판별 API** — 연동 초기에 거론됐으나 **넣지 않기로 확정**했다. 프론트의 요구는 "이 후기를 내가 수정할 수 있는가"를 아는 것이었고, 후기 목록 응답의 `isEditable`이 이미 그 답을 준다.
- **후기 작성·수정 폼 API** — 서버가 폼을 내려주는 것은 신고 사유·미방문 사유 둘뿐이다. 후기의 난이도·혼잡도·연습 방법은 enum이라 클라이언트가 선택지를 갖고 있고, "문구를 앱 배포 없이 바꾼다"는 폼의 도입 이유가 후기에는 없다. 따라서 **수정 화면은 상세 조회 한 번으로 채운다**.
- **Swagger 미방문 사유 폼 예시 오류** — 응답은 정상이고 문서 example만 틀린 것이라 스펙 변경이 없다. 같은 이슈·PR에서 함께 고치되 설계 문서에는 남기지 않는다.
- 후기 좋아요, 서버 측 GPS 좌표 재검증 — 기존대로 범위 밖.

## 요구사항

### 기능 요구사항

1. **후기 상세 조회** — 수정 화면이 폼을 채울 수 있도록 후기 한 건의 전체 값을 돌려주는 API를 추가한다. **본인 후기만** 조회할 수 있다.
2. **후기 내용 선택 입력** — 필수는 `isRecommended`·`difficulty`·`congestion`·`practiceMethod` 넷이고, `content`·`caution`은 선택이다. 넷만 채운 후기가 정상 작성된다.
3. **레벨별 방문 인증** — 인증된 후기 배지는 **작성 당시 레벨에서 인증받았을 때만** 붙는다. 이미 작성된 후기의 값은 그대로다(스냅샷 유지).
4. **인증 필드 정리** — 연습 목록·방문 기록 응답에서 "한 번이라도 인증됨"(`isVerified`)을 내리지 않는다. 인증 표시는 **후기 응답에서만** 한다. 이번 방문의 인증 성공 여부(`isCertifiedNow`)는 결과 화면에 필요하므로 남긴다.
5. **로그인 응답에 온보딩 완료 여부** — 로그인·복구 응답이 온보딩 완료 여부를 함께 내려, 클라이언트가 온보딩 화면 분기를 결정할 수 있게 한다.
6. **재가입 가능 시각 응답** — 재가입 잠금(탈퇴 3~10일) 상태를 에러가 아니라 **200 응답의 상태 분기**로 내리고, 재가입 가능 시각을 값으로 준다.
7. **주차장 측정값 무시** — 주행거리가 없는 장소는 앱이 보낸 인정 주행거리를 0으로 취급한다.
8. **연습기록 삭제 API 제거** — 쓰지 않기로 해 엔드포인트를 없앤다.
9. **즉시 탈퇴 API(내부 테스트용)** — 유예기간 없이 회원을 물리 삭제해, 같은 소셜 계정으로 바로 재가입·온보딩을 다시 테스트할 수 있게 한다. **운영에는 존재하지 않는다.**
10. **연습 목록의 시각 필드 정정** — 방문 전에는 담은 시각, 방문 후에는 마지막 방문 시각을 담아 항상 값이 있게 한다.

### 비기능 요구사항

- 스키마 변경은 **커밋 단위로 나눈다** — V22(`review.content`), V23(`member_practice` 인증 컬럼). 각 커밋이 그 시점에 빌드·기동 가능해야 한다.
- **하위호환을 깨는 변경이 포함된다** — 응답 필드 삭제(4번), 응답 구조 변경(6번), 필드 개명(10번), 엔드포인트 삭제(8번). 앱 업데이트와 함께 나가며 과도기용 필드를 남기지 않는다.
- 즉시 탈퇴(9번)는 **운영 프로파일에서 빈이 등록되지 않아야** 한다. 테스트로 이를 검증한다.
- 인증 판정 경로는 방문 기록 트랜잭션 안에 있다. 기존 잠금 순서(**연습 항목 → 회원**)를 유지한다 — 순서가 바뀌면 데드락이 생긴다(ADR 0012).

## 도메인 모델 (마이그레이션 V22 · V23)

### review (V22)

```sql
ALTER TABLE review ALTER COLUMN content DROP NOT NULL;
```

- `content`가 NULL이면 "내용 없이 평가만 남긴 후기"다. 빈 문자열은 저장하지 않고 **NULL로 정규화**한다(`caution`과 같은 처리).
- 길이 제한(150자)은 그대로다.

### member_practice — 인증 컬럼 교체 (V23)

```sql
ALTER TABLE member_practice ADD COLUMN verified_level varchar(20);

UPDATE member_practice mp
   SET verified_level = m.level
  FROM member m
 WHERE m.id = mp.member_id AND mp.verified AND m.level IS NOT NULL;

ALTER TABLE member_practice DROP COLUMN verified;
```

- **`verified_level`** = 이 항목에서 **가장 마지막으로 인증에 성공했을 때의 회원 레벨**. NULL이면 인증 이력 없음.
- 기존 `verified` boolean을 **대체**한다. `verified_level IS NOT NULL`이 곧 "한 번이라도 인증됨"이라 두 컬럼을 함께 두면 같은 사실이 두 곳에 나뉘고 `verified=true / verified_level=NULL` 같은 모순 상태가 만들어질 수 있다.
- **백필은 회원의 현재 레벨**로 한다 — 기존 인증자가 마이그레이션 직후 배지를 잃지 않게 한다.
- 레벨은 내려가지 않으므로 `verified_level`은 항상 회원의 현재 레벨 **이하**다.

### ERD 갱신 필요

[docs/erd.md](../erd.md)의 `member_practice`(`verified` → `verified_level`)와 `review`(`content` NULL 허용)를 반영한다.

## API 명세

| Method | Path | 설명 | 인증 | 변경 |
|--------|------|------|------|------|
| GET | /api/v1/reviews/{reviewId} | 후기 상세(수정 폼 프리필) | JWT | **신규** |
| POST | /api/v1/places/{placeId}/reviews | 후기 작성 | JWT | 검증 완화 |
| PUT | /api/v1/reviews/{reviewId} | 후기 수정 | JWT | 검증 완화 |
| GET | /api/v1/members/me/practices | 내 연습 목록 | JWT | 필드 삭제·개명 |
| POST | /api/v1/practices/{practiceId}/visits | 방문 기록 | JWT | 필드 삭제 |
| ~~DELETE~~ | ~~/api/v1/practices/{practiceId}~~ | 연습 목록에서 제거 | JWT | **삭제** |
| POST | /api/v1/auth/oauth/{provider} | 소셜 로그인 | X | 필드 추가·상태 추가 |
| POST | /api/v1/auth/oauth/{provider}/restore | 계정 복구 | X | 필드 추가·상태 추가 |
| DELETE | /api/v1/members/me/hard | 즉시 탈퇴(내부 테스트용, 비운영) | JWT | **신규** |

**컨트롤러 배치** — 후기 상세는 `ReviewController`. `reviews`는 이미 자체 오퍼레이션 묶음을 가진 전용 컨트롤러이고, 개별 조작 경로(`/reviews/{reviewId}`)를 수정·삭제와 공유한다(스펙 010의 배치 그대로).

### 1. 후기 상세 조회 (신규)

```json
// GET /api/v1/reviews/31   (JWT)
// Response data
{
  "reviewId": 31,
  "placeId": 1,
  "placeName": "한강 코스",
  "isRecommended": true,
  "difficulty": "EASY",
  "congestion": "NORMAL",
  "practiceMethod": "ACCOMPANIED",
  "content": "차선이 넓고 신호가 단순해서 처음 도로 나갈 때 딱이었어요.",
  "caution": "주말 오후엔 자전거 통행이 많습니다.",
  "isEditable": true,
  "isHidden": false,
  "isVerifiedVisit": true,
  "createdAt": "2026-08-06T14:02:11"
}
```

- **본인 후기만** — 타인 후기는 **403 `REVIEW_403_1`**(수정·삭제와 같은 판단). 목록에 없는 `caution`을 주는 API라 공개할 수 없다.
- **비공개 처리된 내 후기도 조회된다**(`isHidden: true`). 작성자 본인에게는 보이는 것이 기존 정책이다.
- `isEditable`은 목록과 같은 기준 — 작성 당시 레벨 = 현재 레벨.
- `placeName`은 수정 화면 헤더용. 없는 `reviewId` → 404.

### 2. 후기 작성·수정 — 내용 선택 입력

```json
// POST /api/v1/places/1/reviews   (JWT)
// 평가만 남기는 최소 요청
{
  "isRecommended": true,
  "difficulty": "EASY",
  "congestion": "NORMAL",
  "practiceMethod": "ACCOMPANIED"
}
```

- 필수: `isRecommended`·`difficulty`·`congestion`·`practiceMethod`. 누락 시 400.
- `content`·`caution` 선택. `content`는 있으면 **150자 이하**, 빈 문자열·공백만이면 NULL로 저장한다.
- 수정(PUT)도 같은 바디를 쓰므로 **내용을 지우는 수정**이 가능해진다(전체 교체이므로 `content`를 빼거나 null로 보내면 지워진다).
- 목록·상세 응답에서 `content`는 **키를 유지하고 값만 null**로 내려간다. 카드가 항상 같은 모양을 그리도록 키를 빼지 않는다.
- 요약(난이도 분포·추천 수)은 건수 집계라 영향 없다. 내용 없는 후기도 그대로 집계된다.

### 3. 레벨별 방문 인증

**판정 규칙**

- 방문 기록에서 인증에 성공하면 `member_practice.verified_level`에 **그 방문을 시작한 시점의 회원 레벨**을 기록한다(이미 값이 있으면 덮어쓴다).
- 후기 작성 시 `verified_level == member.level`이면 `review.is_verified_visit = true`.
- **이미 작성된 후기는 바뀌지 않는다** — `is_verified_visit`은 작성 시점 스냅샷이라는 기존 성질을 유지한다.

**승급을 일으킨 방문의 처리**

`recordVisit`은 [인증 판정 → 거리 누적 → 승급]을 한 트랜잭션에서 처리한다. SEED로 코스를 완주해 인증받고 그 거리로 ROOKIE가 되면, 이 인증은 **SEED(승급 전 레벨)로 기록**한다. 결과적으로 그 주행으로 승급한 회원은 새 레벨에서 다시 인증해야 인증 후기를 쓸 수 있다.

구현상 회원 레벨을 `addDistance` **이전에** 읽어야 하므로, 회원 행 잠금이 방문 기록보다 앞으로 온다. 잠금 순서(연습 항목 → 회원)는 그대로다.

**레벨이 없는 회원(온보딩 미완료)**

`verified_level`에 채울 값이 없어 인증 이력을 남기지 않는다(NULL 유지). 온보딩을 마쳐야 후기를 쓸 수 있으므로 잃는 것이 없고, 온보딩 후 첫 레벨에서 다시 인증하는 것이 "레벨마다 새로"라는 규칙과도 맞는다.

**예시** — SEED에서 인증 후 ROOKIE로 승급한 회원

| 시점 | verified_level | 새 후기의 isVerifiedVisit |
|------|----------------|---------------------------|
| SEED에서 인증 | SEED | ✅ (SEED 후기) |
| ROOKIE 승급 직후 | SEED | ❌ |
| ROOKIE로 재인증 | ROOKIE | ✅ |

SEED 때 쓴 후기의 배지는 세 시점 모두 `true`로 남는다.

### 4. 인증 필드 정리

**연습 목록** — `isVerified` 삭제(`visitedAt` → `lastActivityAt`은 10번).

```json
{
  "practiceId": 12, "placeId": 1, "placeName": "한강 코스",
  "practiceTypes": ["STRAIGHT", "LANE_CHANGE"],
  "status": "VISITED", "visitCount": 2, "lastActivityAt": "2026-08-05T18:20:00",
  "hasReview": false
}
```

**방문 기록** — `isVerified` 삭제, `isCertifiedNow` 유지.

```json
{
  "visitCount": 2,
  "addedCertifiedDistanceMeters": 2100,
  "requiredDistanceMeters": 2000,
  "isCertifiedNow": true,
  "totalDistanceKm": 12.4,
  "levelUp": false
}
```

- 인증 표시는 **후기 응답의 `isVerifiedVisit` 한 곳**으로 모은다. 연습 목록에 누적 인증 배지를 남겨두면, 레벨별 판정으로 바뀐 뒤 "목록엔 인증됨인데 새 후기엔 배지가 안 붙는" 어긋남이 생긴다.
- 이 삭제로 `member_practice.verified`를 읽는 코드가 전부 사라져 컬럼을 지울 수 있다.

### 5. 주차장 측정값 무시

주행거리가 없는 장소(주차장)는 `certifiedDistanceMeters`를 **0으로 취급**한다.

- `certified_distance_meters`에 누적하지 않는다. 지금은 인증·레벨 어디에도 반영되지 않으면서 컬럼에만 쌓인다.
- 응답 `addedCertifiedDistanceMeters`도 0으로 나간다.
- 인증(항상 실패)·레벨 누적(항상 0)은 기존과 동일하다. 판정은 `Place.drivingDistanceMeters() == null`로 하며, 코스인데 거리가 0인 데이터 오류도 같이 걸린다.

### 6. 로그인 응답 — 온보딩 완료 여부

```json
{ "status": "SUCCESS", "accessToken": "...", "refreshToken": "...",
  "isNewMember": false, "isOnboarded": true, "nickname": "차근차근 토끼" }
```

- 판정 기준은 **`member_onboarding` 행 존재**. 온보딩 재제출 거부에 쓰는 값과 같은 기준을 쓴다.
- **기존 `isNewMember`로는 부족하다** — 가입 후 온보딩 중 이탈한 회원이 재로그인하면 `isNewMember=false`라 온보딩 화면으로 보낼 수 없다.
- 신규 가입은 항상 `false`(조회 없이 확정). 토큰이 없는 상태(`WITHDRAWAL_PENDING`·`WITHDRAWAL_LOCKED`)도 `false`.
- **계정 복구 응답도 같은 필드를 채운다** — 로그인과 같은 응답 타입을 쓰고, 복구 직후에도 온보딩 분기가 필요하다.

### 7. 재가입 잠금 — 200 + 재가입 가능 시각

탈퇴 3~10일 구간(복구 불가, 재가입 대기)을 **에러에서 정상 응답으로** 옮긴다.

```json
// POST /api/v1/auth/oauth/kakao  또는  .../restore
{ "isSuccess": true, "code": "COMMON_200",
  "data": { "status": "WITHDRAWAL_LOCKED",
            "accessToken": null, "refreshToken": null,
            "isNewMember": false, "isOnboarded": false,
            "withdrawalRequestedAt": "2026-08-01T10:00:00",
            "reRegisterableAt": "2026-08-11T10:00:00" } }
```

- `reRegisterableAt` = `deletedAt + 10일`(`WithdrawalPolicy.RE_REGISTERABLE_WINDOW`).
- **로그인·복구 양쪽 모두** 같은 상태를 낼 수 있으므로 함께 바꾼다. 복구만 고치면 로그인에서 여전히 날짜를 못 받는다.
- 기존 **409 `MEMBER_409_1`(`WITHDRAWAL_LOCKED`)은 제거한다** — 던지는 곳이 없는 에러 코드가 목록에 남으면 그 응답이 올 수 있다고 오해하게 된다.

**왜 에러가 아니라 200인가** — 전역 예외 핸들러가 `ApiResponse<Void>`를 돌려줘 **에러 응답에는 데이터를 실을 수 없다**. 이미 `WITHDRAWAL_PENDING`을 200으로 내리고 있어 상태 분기가 기존 구조와 일관되다. 검토한 대안과 함께 [ADR 0013](../adr/0013-withdrawal-locked-as-status.md)에 기록했다.

**안내 시각이 최대 하루 어긋날 수 있다** — 소셜 식별자 해제는 매일 04:00(KST) 배치가 하므로, `deletedAt + 10일`이 지나도 다음 배치 전까지는 `LOCKED`이고 `reRegisterableAt`이 과거가 된다. 서버는 보정하지 않고, 과거면 "지금 가능"으로 표시하는 것은 클라이언트 몫이다(ADR 0013).

### 8. 연습기록 삭제 API 제거

`DELETE /api/v1/practices/{practiceId}`를 없앤다. 엔드포인트·서비스·테스트를 함께 걷어낸다.

- **결과**: 한 번 담은 장소는 목록에서 뺄 수 없다. 재등록(`replan`)은 상태만 예정으로 되돌릴 뿐 행을 지우지 않으므로, 잘못 담은 항목도 목록에 남는다. 화면에서 쓰지 않는 기능이라 감수한다.
- `MemberPractice`는 회원 삭제 시 CASCADE로 정리되므로 고아 행 문제는 없다.

### 9. 즉시 탈퇴 (내부 테스트용)

```
DELETE /api/v1/members/me/hard   (JWT)
→ 200 { isSuccess: true, code: "COMMON_200", data: null }
```

**운영에는 존재하지 않는다.** 컨트롤러 빈을 `@Profile("!prod")`로 묶어, 운영 컨테이너에서는 엔드포인트 자체가 등록되지 않는다. 내부 토큰으로 막고 운영에 두는 방식은 운영 데이터를 물리 삭제할 수 있는 문을 상시로 여는 것이라 택하지 않는다.

- **대상은 토큰의 본인 계정뿐**이다. 회원 id를 파라미터로 받지 않아 남의 계정을 지울 수 없다.
- 유예기간·익명화를 거치지 않고 **행을 물리 삭제**한다. 같은 소셜 계정으로 즉시 재가입할 수 있어야 재가입·온보딩 반복 테스트가 된다.

**삭제 순서** — FK에 CASCADE가 없는 네 테이블만 직접 지우면 나머지는 DB가 함께 지운다. 한 트랜잭션에서 네이티브 쿼리로 처리한다(JPA `deleteById`만 부르면 아래 네 곳에서 FK 위반이 난다).

```sql
DELETE FROM bookmark          WHERE member_id = :memberId;
DELETE FROM refresh_token     WHERE member_id = :memberId;
DELETE FROM social_account    WHERE member_id = :memberId;
DELETE FROM member_onboarding WHERE member_id = :memberId;
DELETE FROM member            WHERE id = :memberId;
```

`review`·`recent_search`·`review_report`·`member_block`·`member_practice`는 `ON DELETE CASCADE`라 마지막 구문에서 함께 삭제된다. **누락된 FK에 CASCADE를 추가하지는 않는다** — 운영에서 회원을 물리 삭제할 일이 없는데 실수의 파급만 커진다.

### 10. 연습 목록 — `visitedAt` → `lastActivityAt`

담기만 하고 안 다녀온 항목도 화면에 날짜를 하나 보여줘야 하는데, 지금 `visitedAt`은 그 경우 `null`이다.

- **`lastActivityAt`** = 방문 이력이 있으면 마지막 방문 시각, 없으면 **담은 시각**. 항상 값이 있다.
- 정렬 기준(`COALESCE(visited_at, created_at)`)과 같은 값이라 엔티티의 `recentActivityAt()`을 그대로 쓴다. 쿼리 변경은 없다.
- **이름을 바꾸는 이유**: 방문한 적 없는 항목에 `visitedAt`이 채워져 있으면 반드시 오해를 부른다. 방문 여부는 `status`로 구분되므로 정보 손실도 없다.

## 완료 조건 (Acceptance Criteria)

**후기 상세·내용 선택**

- [ ] `GET /reviews/{reviewId}`가 PUT 요청에 필요한 6개 필드를 모두 돌려준다.
- [ ] 타인 후기 상세 조회는 403, 없는 후기는 404.
- [ ] 비공개된 내 후기도 상세 조회되고 `isHidden: true`로 표시된다.
- [ ] `content` 없이 필수 4개만 보낸 후기 작성이 성공한다.
- [ ] 내용 있는 후기를 `content` 없이 수정하면 내용이 지워진다.
- [ ] 공백만 보낸 `content`는 NULL로 저장된다.
- [ ] 151자 `content`는 여전히 400.

**레벨별 방문 인증**

- [ ] SEED에서 인증한 회원이 ROOKIE 승급 후 새로 쓴 후기는 `isVerifiedVisit=false`.
- [ ] SEED 때 쓴 후기의 `isVerifiedVisit`은 승급 후에도 `true`로 남는다.
- [ ] ROOKIE로 재인증하면 그 뒤 후기는 `isVerifiedVisit=true`.
- [ ] 인증과 동시에 승급한 방문은 `verified_level`이 **승급 전** 레벨로 기록된다.
- [ ] 레벨 없는 회원의 인증은 `verified_level`을 남기지 않는다.
- [ ] V23 백필 후 기존 인증자의 후기 작성이 `isVerifiedVisit=true`로 유지된다.

**응답 정리**

- [ ] 연습 목록·방문 기록 응답에 `isVerified`가 없다.
- [ ] 방문 기록 응답의 `isCertifiedNow`는 그대로 동작한다.
- [ ] `member_practice.verified` 컬럼이 제거되고 참조하는 코드가 없다.
- [ ] 주차장에 `certifiedDistanceMeters`를 보내도 `certified_distance_meters`가 늘지 않고 응답이 0이다.

**인증 응답**

- [ ] 온보딩 미완료 회원이 재로그인하면 `isOnboarded=false`.
- [ ] 온보딩 완료 회원은 로그인·복구 모두 `isOnboarded=true`.
- [ ] 탈퇴 5일 경과 회원의 로그인·복구가 200 `WITHDRAWAL_LOCKED` + `reRegisterableAt`(탈퇴+10일)로 응답한다.

**엔드포인트 추가·삭제**

- [ ] `DELETE /practices/{practiceId}` 호출이 404다(매핑이 사라졌다).
- [ ] 즉시 탈퇴 후 같은 소셜 계정으로 **신규 가입**(`isNewMember=true`)이 된다.
- [ ] 즉시 탈퇴로 `bookmark`·`refresh_token`·`social_account`·`member_onboarding`·`member`와 CASCADE 대상 행이 모두 사라진다.
- [ ] `prod` 프로파일에서는 즉시 탈퇴 엔드포인트가 등록되지 않는다.
- [ ] 연습 목록의 `lastActivityAt`이 미방문 항목은 담은 시각, 방문 항목은 마지막 방문 시각으로 채워진다(항상 non-null).

**공통**

- [ ] `./gradlew test` 통과.
- [ ] 스펙 010·011·001·003의 Status 표에 이 문서를 가리키는 갱신 행을 추가한다(구현 완료 시점).
- [ ] [docs/erd.md](../erd.md) 반영.

## 확정된 결정 (리뷰 반영)

1. **호환성 깨지는 변경을 감수한다** — 재가입 잠금의 409 → 200 전환(7번), `isVerified` 삭제(4번), `visitedAt` 개명(10번) 모두 앱 업데이트와 함께 나간다. 하위호환 필드를 남기지 않는다.
2. **장소별 "수정 가능한 내 후기" 판별 API는 만들지 않는다** — 후기 목록의 `isEditable`이 프론트가 실제로 필요로 한 답을 이미 준다.
3. **내용 없는 후기의 화면 표현은 프론트 결정** — 서버는 `content: null`로 내려주는 것까지만 한다.
4. **연습 항목을 목록에서 뺄 방법이 없어진다**(8번) — 화면에서 쓰지 않는 기능이라 감수한다.
5. **즉시 탈퇴는 물리 삭제 + 비운영 한정** — 익명화 배치를 즉시 실행하는 방식은 결과가 비슷하면서 구현만 복잡해 택하지 않았다.

## 미해결 질문

- 없음. 구현 착수 가능.
