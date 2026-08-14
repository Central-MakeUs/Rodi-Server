# 사용자 연습 코스 목록 (등록 · 방문 상태 · 미방문 이유)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-06 | Draft | 최초 작성 (2차 업데이트 3번 — 연습 코스 목록) |
| 2026-08-06 | Draft | 리뷰 반영 — 같은 코스 재연습은 행을 늘리지 않고 **`visit_count`로 누적**, 주차장도 담기 가능, 목록은 **상태 필터 없이 최근 방문순**, 방문 판정은 전적으로 클라이언트의 상태 변경 호출로 결정 |
| 2026-08-06 | Draft | **미방문 사유 제출을 별도 API로 분리**(`POST /practices/{id}/skip-reason`) |
| 2026-08-06 | Draft | **상태를 요청에서 제거** — 방문 기록은 인정 주행거리만 받고 서버가 방문(`VISITED`)으로 정한다. 사유 제출이 상태까지 바꾸므로 사용자의 두 선택(다녀왔어요/안 했어요)이 API 하나씩에 대응하고, **사유 없는 미방문이 생길 수 없다** |
| 2026-08-09 | Implemented | 방문 기록을 **`PATCH /practices/{id}` → `POST /practices/{id}/visits`** 로 변경 — 호출마다 연습 횟수가 오르는 비멱등 연산이라 `PATCH`가 맞지 않고, 미방문 사유(`skip-reason`)와 대칭이 된다 |
| 2026-08-06 | Draft | **GPS 방문 인증 정책 반영** — "다녀왔어요"(연습기록)와 **방문 인증을 분리**한다. 앱이 측정한 **인정 주행거리**(코스 경로선 150m 이내 이동거리)를 보내면 **서버가 필요 거리(`min(코스거리 × 40%, 5km)`)와 비교해 인증을 판정**한다. 측정 세션·150m 판정은 앱이 관리하고 서버는 숫자만 받는다 |
| 2026-08-14 | Implemented | [스펙 013](013-app-integration-refinements.md) 반영 — 방문 인증을 레벨별로 판정(`verified` → `verified_level`, V23), 연습 목록·방문 응답의 `isVerified` 삭제, `visitedAt` → `lastActivityAt`, 주차장 측정값 무시, **연습기록 삭제 API 제거** |

## 배경 / 목적

지금은 마음에 드는 코스를 **북마크**해두는 것 말고는, "이 코스로 연습하겠다"는 의사를 남길 방법이 없다. 그래서 사용자가 실제로 다녀왔는지, 안 갔다면 왜 안 갔는지 서비스가 전혀 모른다.

이번에 코스 상세의 **[연습하기]** 버튼으로 코스를 **내 연습 목록**에 담고, 이후 **다녀왔는지 여부를 표시**하게 한다. 이 기록은 두 곳에 쓰인다:

1. **미방문 이유 수집** — 안 간 이유(멀었다 / 어려워 보였다 / 일정이 안 맞았다 …)를 모아 추천을 개선한다.
2. **후기 방문 인증** — 방문 처리한 코스에 남긴 후기는 **인증된 후기**로 구분 표시한다([스펙 010](010-place-review.md)의 미해결 질문 4번을 여기서 해소).

**북마크와의 차이**: 북마크는 "나중에 볼 것"(장소 저장), 연습 목록은 "가기로 한 것"(행동 의사 + 방문 결과). 테이블·API를 따로 둔다.

### 범위 밖

- **서버 측 좌표 검증** — GPS 측정(150m 판정·거리 누적)은 앱이 하고 서버는 그 숫자를 그대로 신뢰한다. 서버가 좌표를 받아 재검증하지는 않는다.
- **연습 예정 일시·알림** — 날짜를 잡거나 리마인드하는 기능은 없다.
- 미방문 이유를 실제 **추천 알고리즘에 반영**하는 로직(수집까지만).
- 코스 상세 화면 개편 — 별도 스펙. 여기서는 상세의 버튼이 호출할 **등록 API**만 정의한다.

## 요구사항

### 기능 요구사항

1. **연습 목록 등록**: 코스 상세의 [연습하기]로 장소를 내 연습 목록에 담는다. 상태는 `PLANNED`(예정)로 시작한다.
2. **내 연습 목록 조회**: 담아둔 항목을 **최근 방문순 커서 페이지네이션**으로 반환한다. 화면에 쓰는 값만 담는다 — **장소명·연습유형·연습 횟수·마지막 방문일·방문 인증 여부·후기 작성 여부** + 동작에 필요한 식별자(`practiceId`·`placeId`)·`status`. 상태 필터는 두지 않는다(한 목록에 상태를 배지로 구분).
3. **방문 기록(RV-01 "다녀왔어요")**: 앱이 **인정 주행거리만** 보내고 **상태는 보내지 않는다** — 이 호출 자체가 방문이므로 서버가 `VISITED`로 정한다. 연습 횟수 +1, 방문 시각 기록. 인증과는 별개라 경로만 보고 왔어도 기록된다.
4. **미방문 사유 제출(RV-01 "안 했어요")**: 선택지는 **서버가 정의해 내려주고**([스펙 010](010-place-review.md)의 신고 사유 폼과 동일 구조 `global.common.form` 재사용), 사용자가 고른 값을 제출하면 **상태(`NOT_VISITED`)와 사유가 한 번에** 저장된다.
   - **사유는 한 번 저장하면 수정할 수 없다**(409). 다시 다녀와 방문을 기록하면 사유가 비워져 새로 남길 수 있다.
   - 사용자가 사유 폼에서 이탈하면 아무것도 저장되지 않고 `PLANNED`로 남는다(연습 예정이 유지되는 것이라 자연스럽다). **사유 없는 `NOT_VISITED`는 생기지 않는다.**
5. **목록에서 제거**: 잘못 담았거나 관심이 없어지면 항목을 삭제한다.
6. **방문 인증 (GPS)**: 앱이 주행을 측정해 **인정 주행거리**(코스 경로선 **150m 이내**에서 이동한 거리)를 누적하고, 방문 처리 시 그 값을 보낸다.
   - **필요 거리 = `min(코스 전체 거리 × 40%, 5km)`** — 12.5km를 넘는 코스는 5km 상한이 걸린다.
   - **인증 판정은 서버가 한다.** 앱은 인증 여부를 보내지 않고 측정값만 보낸다.
   - 측정 없이 "다녀왔어요"만 누른 경우(경로만 보기·알림 거부) 거리를 생략하며 **인증되지 않는다** — 연습기록은 그대로 남는다.
   - 주차장처럼 **주행거리가 없는 장소는 인증 대상이 아니다**(필요 거리 0).
   - 한 번 인증된 항목은 이후 미인증 방문이 있어도 **인증 상태를 유지**한다.
   - **측정 세션(시작·중단·동시 1개 제한·10분 무활동 판정)은 앱이 관리**한다. 서버는 세션을 저장하지 않는다.
7. **후기 방문 인증**: 후기 작성 시 그 장소에 **인증된 연습기록이 있으면** `review.is_verified_visit = true`로 저장하고, 후기 목록 응답에 `isVerifiedVisit`을 포함한다.

### 비기능 요구사항

- **모든 엔드포인트 JWT 필수**(내 목록이라 회원 기준).
- 목록은 **커서 페이지네이션**(ADR 0010), 공통 `CursorPage<T>` 재사용.
- 목록 응답은 **화면이 실제로 쓰는 값만** 담는다. 후기 작성 여부는 후기 도메인을 읽어 채운다(연습 → 후기 단방향 읽기 의존, 마이페이지가 북마크를 읽는 것과 같은 방식).

## 도메인 모델 (마이그레이션 V18·V19)

> V13~V17은 [스펙 010](010-place-review.md)이 사용했다. 연습 코스는 **V18**(테이블), 방문 인증 컬럼은 **V19**다.

### member_practice (회원 ↔ 장소, 연습 의사·방문 결과)

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| id | bigint PK | Y | |
| member_id | bigint FK→member | Y | |
| place_id | bigint FK→place | Y | 담은 장소(**코스·주차장 공통**) |
| status | varchar(20) enum | Y | PLANNED / VISITED / NOT_VISITED |
| visit_count | int | Y (default 0) | **이 코스를 다녀온 횟수**(`VISITED`로 바꿀 때마다 +1) |
| visited_at | timestamp | N | **마지막** 방문 시각(`TIMESTAMP WITHOUT TIME ZONE` — 기존 테이블과 동일) |
| certified_distance_meters | bigint | Y (default 0) | 이 항목에서 누적된 **인정 주행거리**(앱 측정값의 합) — V19 |
| verified | boolean | Y (default false) | 한 번이라도 **방문 인증**에 성공했는지(내려가지 않음) — V19 |
| skip_reason | varchar(30) enum | N | `NOT_VISITED`일 때 사유 |
| skip_detail | varchar(100) | N | 사유가 `OTHER`일 때 직접 입력 |
| created_at·updated_at | timestamp | Y | `BaseEntity` (`TIMESTAMP WITHOUT TIME ZONE`) |

- **unique(member_id, place_id)** — 같은 장소는 목록에 **한 행**만 둔다. 같은 코스를 여러 번 연습해도 행을 늘리지 않고 **`visit_count`를 올린다**(목록이 같은 코스로 도배되지 않고, "이 코스 3번 연습함"을 바로 보여줄 수 있다).
- **재도전**: 이미 담긴 장소를 다시 담으면(`POST`) 상태를 **`PLANNED`로 되돌리고** `skip_*`을 비운다. `visit_count`는 유지된다.
- **상태 전이는 자유**다(`NOT_VISITED`로 표시했다가 나중에 다녀오면 `VISITED`로 바꿀 수 있다). `VISITED`로 바뀔 때마다 `visit_count += 1`, `visited_at = now`.
- `skip_reason`·`skip_detail`은 `NOT_VISITED`일 때만 채워지고, 다른 상태로 바뀌면 비운다.
- FK는 `ON DELETE CASCADE`(회원·장소 하드 삭제 대비).
- 인덱스: unique가 `(member_id, place_id)`를 겸하고, 목록 정렬용으로 `(member_id, visited_at DESC)`.

### review 확장

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| is_verified_visit | boolean | Y (default false) | 작성 시점에 **인증된 연습기록**이 있었는지 **스냅샷** |

- **스냅샷으로 두는 이유**: 조회할 때마다 조인하면 목록 쿼리가 무거워지고, 사용자가 나중에 연습 항목을 지우면 과거 후기의 배지가 사라져 이력이 흔들린다. 작성 시점 판정을 고정한다.

### Enum

| Enum | 값 | 비고 |
|------|-----|------|
| `member_practice.status` | PLANNED(예정) / VISITED(다녀옴) / NOT_VISITED(안 다녀옴) | |
| `member_practice.skip_reason` | CHECK_REALTIME_TRAFFIC / TOO_FAR / ROUTE_SEEMED_DIFFICULT / SCHEDULE_DID_NOT_MATCH / OTHER | 실시간 교통정보를 보려고 했어요 / 생각보다 멀었어요 / 길이 어려워 보여요 / 일정이 맞지 않았어요 / 기타(직접 입력) |

> 사유의 **한글 라벨·순서·직접입력 여부는 서버가 enum에 들고 있다가 폼으로 내려준다**(신고 사유와 동일 방침). 문구를 앱 배포 없이 바꾸기 위함.

## API 명세 (패키지 `domain.practice`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/places/{placeId}/practices | 연습 목록에 담기(멱등) | JWT |
| GET | /api/v1/members/me/practices | 내 연습 목록(상태 필터·커서) | JWT |
| POST | /api/v1/practices/{practiceId}/visits | 방문 기록(다녀왔어요, +GPS 측정값) | JWT |
| POST | /api/v1/practices/{practiceId}/skip-reason | 미방문 사유 제출(안 했어요) | JWT |
| DELETE | /api/v1/practices/{practiceId} | 목록에서 제거(멱등) | JWT |
| GET | /api/v1/practices/skip-reason-form | 미방문 이유 폼 | JWT |

**컨트롤러 배치**(CLAUDE.md 기준): `practices`는 자체 오퍼레이션 묶음(담기·목록·방문 기록·삭제)이라 **`PracticeController` 전용**. 담기는 장소 하위(`/places/{placeId}/practices`), 개별 조작은 `/practices/{practiceId}`. 목록은 회원 소유라 `/members/me/practices`.

**메서드**: 사용자의 두 선택은 **하위 리소스 `POST`로 대칭**을 맞춘다 — 다녀왔어요는 `visits`, 안 했어요는 `skip-reason`. 둘 다 부를 때마다 기록이 쌓이는(연습 횟수 +1) **비멱등 연산**이라 `PATCH`가 아니다.

### 1. 연습 목록에 담기

```json
// POST /api/v1/places/1/practices   (JWT)
// Response data
{ "practiceId": 12, "status": "PLANNED", "visitCount": 2, "requiredDistanceMeters": 2000 }
```

- 이미 담긴 장소면 **행을 새로 만들지 않고 상태를 `PLANNED`로 되돌린다**(재도전). `visitCount`는 지난 연습 횟수 그대로 유지되고 `skip_*`은 비워진다.
- 없는 `placeId`면 404. 코스·주차장 모두 담을 수 있다.
- `requiredDistanceMeters`는 이 코스의 **방문 인증 필요 거리**(`min(코스거리 × 40%, 5km)`)다. 앱이 연습 진행률(`인정 주행거리 ÷ 필요 거리`)을 계산하는 데 쓴다. 주차장 등 주행거리가 없는 장소는 0.

### 2. 내 연습 목록

```
GET /api/v1/members/me/practices?size=20&cursor=   (JWT)
```

- 상태 필터는 없다. 한 목록에 전부 내려주고 클라이언트가 상태 배지로 구분한다.

```json
// Response data — CursorPage<PracticeItem>
{
  "items": [
    {
      "practiceId": 12,
      "placeId": 1,
      "placeName": "한강 코스",
      "practiceTypes": ["STRAIGHT", "LANE_CHANGE"],
      "status": "VISITED",
      "visitCount": 2,
      "visitedAt": "2026-08-05T18:20:00",
      "isVerified": true,
      "hasReview": false
    }
  ],
  "hasNext": false,
  "nextCursor": null,
  "totalCount": 3
}
```

- 정렬/커서: **최근 방문순** — `COALESCE(visited_at, created_at) DESC, id DESC`. 아직 안 다녀온 항목은 담은 시각을 기준값으로 써서 같은 축에 섞인다(방문 이력이 없다고 목록 맨 아래로 밀리지 않는다). 커서는 `(정렬시각, id)` base64(`CursorCodec`).
- `totalCount`는 **첫 페이지에서만**(공통 `CursorPage` 규칙) 내 연습 항목 총 개수.
- `hasReview`는 **그 장소에 내가 쓴 후기가 있는지**다("후기 쓰기" 버튼 노출 판단). 페이지의 장소 id로 한 번에 조회한다(항목별 조회 금지). 신고 누적으로 비공개된 내 후기도 작성한 것이므로 `true`.
- **미방문 사유(`skipReason`·`skipDetail`)는 응답에 없다** — 수집·분석 목적이라 화면에 쓰지 않는다.
- 장소 요약을 `PlaceListItem`으로 통째로 내리지 않는다. 이 화면은 주소·좌표·주차면수를 쓰지 않아 이름과 연습유형만 준다.

### 3. 방문 기록 (다녀왔어요)

```json
// POST /api/v1/practices/12/visits   (JWT)

// GPS 측정값과 함께 — 인증 판정은 서버가
{ "certifiedDistanceMeters": 2100 }

// 측정 없음(경로만 보기·알림 거부) → 방문은 기록되지만 인증 안 됨
{ }

// Response data
{
  "visitCount": 2,
  "addedCertifiedDistanceMeters": 2100,
  "requiredDistanceMeters": 2000,   // min(코스거리 × 40%, 5km). 주차장 등은 0
  "isCertifiedNow": true,           // 이번 방문으로 인증됐는지
  "isVerified": true                // 이 항목이 한 번이라도 인증된 적 있는지
}
```

- **상태는 요청에 없다.** 이 호출이 곧 "다녀왔어요"이므로 서버가 `VISITED`로 정한다.
- `certifiedDistanceMeters`(선택, 0 이상)는 앱이 측정한 인정 주행거리다. 생략하면 0으로 보고 인증되지 않는다.
- 호출할 때마다 **연습 횟수가 오른다**(같은 코스 재연습). 한 번의 방문에서 중복 호출하지 않는 것은 클라이언트 몫이다.
- 미방문 사유가 남아 있었다면 **비워진다**(다시 다녀왔으므로).
- 본인 항목이 아니면 **403**, 없는 `practiceId`는 404.

### 4. 미방문 사유 제출

```json
// POST /api/v1/practices/12/skip-reason   (JWT)
// Request — 폼(#5)의 code를 그대로 보낸다
{ "reason": "TOO_FAR" }

// 기타 — 직접 입력 필수
{ "reason": "OTHER", "detail": "차가 정비 중이었어요" }

// Response data: null (200)
```

- **상태와 사유를 한 번에** 저장한다 — 이 호출이 곧 "안 했어요"이므로 서버가 `NOT_VISITED`로 바꾸고 사유를 남긴다.
- 이미 사유가 있으면 **409 `PRACTICE_409_1`**(수정 불가). 다시 방문을 기록하면 사유가 비워지므로 새로 남길 수 있다.
- `OTHER`면 `detail` 필수(최대 100자), 다른 사유의 `detail`은 저장하지 않는다.
- 본인 항목이 아니면 403, 없는 `practiceId`는 404.

### 5. 목록에서 제거

```
DELETE /api/v1/practices/12   (JWT)
```

- 응답 데이터 없음(200). 본인 항목만(타인 403), 없으면 멱등 200.
- **이미 작성한 후기의 `is_verified_visit`은 영향받지 않는다**(작성 시점 스냅샷이라).

### 6. 미방문 이유 폼

```json
// GET /api/v1/practices/skip-reason-form   (JWT)
// Response data
{
  "questionId": "WHY_NOT_PRACTICED",
  "type": "SINGLE_SELECT",
  "title": "왜 연습을 다녀오지 않았나요?",
  "description": "이유를 알려주시면 더 나은 코스를 추천해드릴게요!",
  "required": true,
  "options": [
    { "code": "CHECK_REALTIME_TRAFFIC",   "label": "실시간 교통정보를 보려고 했어요", "order": 1, "requiresTextInput": false },
    { "code": "TOO_FAR",                  "label": "생각보다 멀었어요",             "order": 2, "requiresTextInput": false },
    { "code": "ROUTE_SEEMED_DIFFICULT",   "label": "길이 어려워 보여요",            "order": 3, "requiresTextInput": false },
    { "code": "SCHEDULE_DID_NOT_MATCH",   "label": "일정이 맞지 않았어요",          "order": 4, "requiresTextInput": false },
    { "code": "OTHER",                    "label": "기타",                        "order": 5, "requiresTextInput": true,
      "textInputPlaceholder": "이유를 작성해주세요", "textInputMaxLength": 100 }
  ]
}
```

- 구조·직렬화 규칙은 신고 사유 폼과 동일(`global.common.form`). 텍스트 입력이 없는 선택지는 `textInput*` 키를 내려보내지 않는다.
- `code`가 그대로 사유 제출 요청(#4)의 `reason` 값이다.

### 7. 후기 방문 인증 (스펙 010 연계)

- **후기 작성 시**: 그 회원·장소의 `member_practice.verified = true`이면(= GPS 인증에 성공한 이력이 있으면) `review.is_verified_visit = true`로 저장한다. 단순히 "다녀왔어요"만 누른 기록은 인증으로 보지 않는다.
- **후기 목록 응답**에 `isVerifiedVisit` 추가:

```json
{
  "reviewId": 31, "memberId": 7, "nickname": "차근차근 토끼", "memberLevel": "ROOKIE",
  "isRecommended": true, "difficulty": "EASY", "congestion": "NORMAL",
  "practiceMethod": "ACCOMPANIED", "content": "…", "caution": null,
  "isMine": false, "isEditable": false, "isHidden": false,
  "isVerifiedVisit": true,
  "createdAt": "2026-08-06T14:02:11"
}
```

- 이 배지는 **앱이 GPS로 측정한 인정 주행거리**에 근거하며, 도달 판정은 서버가 한다. 다만 측정값 자체는 앱을 신뢰하므로 **서버가 좌표로 재검증한 것은 아니다**.

### 에러 코드 (`PracticeErrorCode` 신규)

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `PRACTICE_403_1` | 403 | 본인의 연습 항목만 변경·삭제할 수 있습니다. |
| `PRACTICE_409_1` | 409 | 이미 등록한 미방문 사유는 변경할 수 없습니다. |

## 완료 조건 (Acceptance Criteria)

- [x] 코스 상세에서 담으면 `PLANNED` 상태로 저장되고, 같은 장소를 다시 담아도 **행이 늘지 않는다**(상태만 `PLANNED`로 되돌아가고 `visitCount`는 유지).
- [x] 코스·주차장 모두 담을 수 있고, 없는 장소를 담으면 404다.
- [x] 내 목록이 **최근 방문순**(방문 이력 없으면 담은 시각 기준)으로 반환되고 `size`만큼 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성·중복 없음).
- [x] `totalCount`는 첫 페이지에서만 내 연습 항목 총계로 채워진다.
- [x] 목록 항목이 `placeName`·`practiceTypes`를 평탄한 필드로 담는다(코스는 등록 태그, 주차장은 `[PARKING]`).
- [x] 방문을 기록하면 상태가 `VISITED`가 되고 `visitCount`가 1 오르며 `visitedAt` 기록·사유가 비워진다. 다시 보내면 횟수가 또 오른다.
- [x] 사유 제출 한 번으로 상태가 `NOT_VISITED`가 되고 사유가 저장된다. `OTHER`인데 직접 입력이 없으면 400이다.
- [x] 이미 사유가 있는 항목에 다시 제출하면 409이고, 방문을 기록하면 사유가 비워져 다시 남길 수 있다.
- [x] 타인 항목의 방문 기록·사유 제출·삭제는 403이다.
- [x] 미방문 이유 폼이 5개 선택지를 order 순으로 반환하고, `OTHER`만 `requiresTextInput=true`·placeholder·최대 길이를 갖는다.
- [x] GPS 인증에 성공한 장소에 후기를 쓰면 `isVerifiedVisit=true`, 다녀왔어요만 눌렀거나 한 번도 안 갔으면 `false`로 저장된다.
- [x] 연습 항목을 삭제해도 이미 작성한 후기의 `isVerifiedVisit`은 그대로다.
- [x] 모든 엔드포인트가 미인증 시 401이다.
- [x] 관련 테스트 통과 (`./gradlew test`).

## 확정된 결정 (리뷰 반영)

- **같은 코스는 한 행** — 재연습은 `visit_count`로 누적한다(행을 늘리지 않음).
- **주차장도 담을 수 있다** — `place` 기준(코스·주차장 공통).
- **목록은 상태 필터 없이 최근 방문순** 한 줄기로 내려준다.
- **"다녀왔어요"와 방문 인증은 분리** — 연습기록은 사용자가 남기고, 인증은 GPS 인정 주행거리가 `min(코스거리 × 40%, 5km)`에 도달해야 성립한다. 측정 세션·150m 판정은 앱이 하고 서버는 숫자만 받아 판정한다(중복 호출 가드 없음).
- **상태 전이 자유** — `NOT_VISITED` → `VISITED`도 가능. 단 **미방문 사유는 한 번 저장하면 수정 불가**(409).
- **연습 횟수는 노출한다** — 목록뿐 아니라 코스 상세·마이페이지에도 쓸 예정(표시 위치·문구는 스펙 012에서 확정).

## 미해결 질문

- (없음 — 위 결정으로 모두 해소)

## 범위 밖 / 다음

- 위치 기반 방문 검증(좌표·체류 시간), 연습 예정 일시·알림.
- 미방문 이유 통계 대시보드, 추천 알고리즘 반영.
- 코스 상세 개편(스펙 012 예정).
