# 사용자 연습 코스 목록 (등록 · 방문 상태 · 미방문 이유)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-06 | Draft | 최초 작성 (2차 업데이트 3번 — 연습 코스 목록) |
| 2026-08-06 | Draft | 리뷰 반영 — 같은 코스 재연습은 행을 늘리지 않고 **`visit_count`로 누적**, 주차장도 담기 가능, 목록은 **상태 필터 없이 최근 방문순**, 방문 판정은 전적으로 클라이언트의 상태 변경 호출로 결정 |

## 배경 / 목적

지금은 마음에 드는 코스를 **북마크**해두는 것 말고는, "이 코스로 연습하겠다"는 의사를 남길 방법이 없다. 그래서 사용자가 실제로 다녀왔는지, 안 갔다면 왜 안 갔는지 서비스가 전혀 모른다.

이번에 코스 상세의 **[연습하기]** 버튼으로 코스를 **내 연습 목록**에 담고, 이후 **다녀왔는지 여부를 표시**하게 한다. 이 기록은 두 곳에 쓰인다:

1. **미방문 이유 수집** — 안 간 이유(멀었다 / 어려워 보였다 / 일정이 안 맞았다 …)를 모아 추천을 개선한다.
2. **후기 방문 인증** — 방문 처리한 코스에 남긴 후기는 **인증된 후기**로 구분 표시한다([스펙 010](010-place-review.md)의 미해결 질문 4번을 여기서 해소).

**북마크와의 차이**: 북마크는 "나중에 볼 것"(장소 저장), 연습 목록은 "가기로 한 것"(행동 의사 + 방문 결과). 테이블·API를 따로 둔다.

### 범위 밖

- **위치 기반 방문 자동 검증** — 방문 여부는 사용자가 직접 표시한다(자기 신고). 좌표 확인은 추후.
- **연습 예정 일시·알림** — 날짜를 잡거나 리마인드하는 기능은 없다.
- 미방문 이유를 실제 **추천 알고리즘에 반영**하는 로직(수집까지만).
- 코스 상세 화면 개편 — 별도 스펙(012 예정). 여기서는 상세의 버튼이 호출할 **등록 API**만 정의한다.

## 요구사항

### 기능 요구사항

1. **연습 목록 등록**: 코스 상세의 [연습하기]로 장소를 내 연습 목록에 담는다. 상태는 `PLANNED`(예정)로 시작한다.
2. **내 연습 목록 조회**: 담아둔 항목을 **최근 방문순 커서 페이지네이션**으로 반환한다. 각 항목에 장소 요약(이름·주소·좌표 등)·**상태**·**연습 횟수**, 미방문이면 사유를 포함한다. 상태 필터는 두지 않는다(한 목록에 상태를 배지로 구분).
3. **방문 여부 상태 변경**: `VISITED`(다녀옴) 또는 `NOT_VISITED`(안 다녀옴)로 바꾼다.
   - **`VISITED`는 사용자가 버튼을 누르는 게 아니라, 클라이언트가 사용자의 이동을 추적하다가 자체 기준(코스 도달·체류 등)을 충족하면 자동으로 호출한다.** 서버는 그 결과를 기록만 한다.
   - `NOT_VISITED`로 바꿀 때는 **미방문 사유가 필수**다. `OTHER`(기타)면 직접 입력도 필수. 언제 물을지도 클라이언트가 판단한다.
   - **미방문 사유는 한 번 저장하면 수정할 수 없다**(나중에 다녀와서 `VISITED`로 바꾸는 것은 가능).
4. **미방문 이유 폼**: 사유 선택지를 **서버가 정의해 내려준다**([스펙 010](010-place-review.md)의 신고 사유 폼과 동일 구조 `global.common.form` 재사용). 앱이 필요할 때 요청한다.
5. **목록에서 제거**: 잘못 담았거나 관심이 없어지면 항목을 삭제한다.
6. **후기 방문 인증**: 후기 작성 시 그 장소에 **`VISITED` 기록이 있으면** `review.is_verified_visit = true`로 저장하고, 후기 목록 응답에 `isVerifiedVisit`을 포함한다.

### 비기능 요구사항

- **모든 엔드포인트 JWT 필수**(내 목록이라 회원 기준).
- 목록은 **커서 페이지네이션**(ADR 0010), 공통 `CursorPage<T>` 재사용.
- 장소 요약 아이템은 기존 **`PlaceListItem`을 재사용**한다(저장 목록·검색과 동일 구성) — 클라이언트가 목록 렌더 코드를 공유할 수 있다.

## 도메인 모델 (마이그레이션 V16)

> V13~V15는 [스펙 010](010-place-review.md)이 사용했다. 연습 코스는 **V16**부터.

### member_practice (회원 ↔ 장소, 연습 의사·방문 결과)

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| id | bigint PK | Y | |
| member_id | bigint FK→member | Y | |
| place_id | bigint FK→place | Y | 담은 장소(**코스·주차장 공통**) |
| status | varchar(20) enum | Y | PLANNED / VISITED / NOT_VISITED |
| visit_count | int | Y (default 0) | **이 코스를 다녀온 횟수**(`VISITED`로 바꿀 때마다 +1) |
| visited_at | timestamptz | N | **마지막** 방문 시각 |
| skip_reason | varchar(30) enum | N | `NOT_VISITED`일 때 사유 |
| skip_detail | varchar(100) | N | 사유가 `OTHER`일 때 직접 입력 |
| created_at·updated_at | timestamptz | Y | `BaseEntity` |

- **unique(member_id, place_id)** — 같은 장소는 목록에 **한 행**만 둔다. 같은 코스를 여러 번 연습해도 행을 늘리지 않고 **`visit_count`를 올린다**(목록이 같은 코스로 도배되지 않고, "이 코스 3번 연습함"을 바로 보여줄 수 있다).
- **재도전**: 이미 담긴 장소를 다시 담으면(`POST`) 상태를 **`PLANNED`로 되돌리고** `skip_*`을 비운다. `visit_count`는 유지된다.
- **상태 전이는 자유**다(`NOT_VISITED`로 표시했다가 나중에 다녀오면 `VISITED`로 바꿀 수 있다). `VISITED`로 바뀔 때마다 `visit_count += 1`, `visited_at = now`.
- `skip_reason`·`skip_detail`은 `NOT_VISITED`일 때만 채워지고, 다른 상태로 바뀌면 비운다.
- FK는 `ON DELETE CASCADE`(회원·장소 하드 삭제 대비).
- 인덱스: unique가 `(member_id, place_id)`를 겸하고, 목록 정렬용으로 `(member_id, visited_at DESC)`.

### review 확장

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| is_verified_visit | boolean | Y (default false) | 작성 시점에 `VISITED` 기록이 있었는지 **스냅샷** |

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
| PATCH | /api/v1/practices/{practiceId} | 방문 여부 상태 변경 | JWT |
| DELETE | /api/v1/practices/{practiceId} | 목록에서 제거(멱등) | JWT |
| GET | /api/v1/practices/skip-reason-form | 미방문 이유 폼 | JWT |

**컨트롤러 배치**(CLAUDE.md 기준): `practices`는 자체 오퍼레이션 묶음(담기·목록·상태변경·삭제)이라 **`PracticeController` 전용**. 담기는 장소 하위(`/places/{placeId}/practices`), 개별 조작은 `/practices/{practiceId}`. 목록은 회원 소유라 `/members/me/practices`.

### 1. 연습 목록에 담기

```json
// POST /api/v1/places/1/practices   (JWT)
// Response data
{ "practiceId": 12, "status": "PLANNED", "visitCount": 2 }
```

- 이미 담긴 장소면 **행을 새로 만들지 않고 상태를 `PLANNED`로 되돌린다**(재도전). `visitCount`는 지난 연습 횟수 그대로 유지되고 `skip_*`은 비워진다.
- 없는 `placeId`면 404. 코스·주차장 모두 담을 수 있다.

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
      "status": "VISITED",
      "visitCount": 2,
      "visitedAt": "2026-08-05T18:20:00",
      "skipReason": null,
      "skipDetail": null,
      "createdAt": "2026-08-01T14:02:11",
      "place": {
        "id": 1, "type": "COURSE", "name": "한강 코스", "address": "서울특별시 영등포구",
        "lat": 37.51, "lng": 127.03, "distanceFromMe": null,
        "practiceTypes": ["STRAIGHT","LANE_CHANGE"],
        "description": "…", "distanceMeters": 2100, "capacity": null, "openTime": null
      }
    }
  ],
  "hasNext": false,
  "nextCursor": null,
  "totalCount": 3
}
```

- 정렬/커서: **최근 방문순** — `COALESCE(visited_at, created_at) DESC, id DESC`. 아직 안 다녀온 항목은 담은 시각을 기준값으로 써서 같은 축에 섞인다(방문 이력이 없다고 목록 맨 아래로 밀리지 않는다). 커서는 `(정렬시각, id)` base64(`CursorCodec`).
- `totalCount`는 **첫 페이지에서만**(공통 `CursorPage` 규칙) 내 연습 항목 총 개수.
- `place`는 **`PlaceListItem` 재사용**(저장 목록과 동일). 현위치를 안 받으므로 `distanceFromMe`는 `null`.

### 3. 방문 여부 상태 변경

```json
// PATCH /api/v1/practices/12   (JWT)

// 다녀옴
{ "status": "VISITED" }

// 안 다녀옴 — 사유 필수
{ "status": "NOT_VISITED", "skipReason": "TOO_FAR" }

// 안 다녀옴 + 기타 — 직접 입력 필수
{ "status": "NOT_VISITED", "skipReason": "OTHER", "skipDetail": "차가 정비 중이었어요" }

// Response data: null (200)
```

- `status` 필수. `NOT_VISITED`면 `skipReason` 필수, 그 사유가 `OTHER`면 `skipDetail` 필수(최대 100자) — 폼의 `textInputMaxLength`와 동일.
- **`VISITED`로 바꾸면 그 자리에서 방문 처리**된다 — `visit_count += 1`, `visited_at = now`, `skip_*` 비움. 이 호출은 **클라이언트가 이동 추적으로 자동 판정해** 보내므로 서버는 별도 검증을 하지 않는다.
- 이미 `VISITED`인 항목에 다시 `VISITED`가 오면 **횟수가 또 올라간다**(같은 코스 재연습). 한 번의 방문에서 중복 호출하지 않는 것은 클라이언트의 판정 로직 몫이다.
- **미방문 사유는 덮어쓸 수 없다** — 이미 `skip_reason`이 있는 항목에 다시 `NOT_VISITED`를 보내면 **409 `PRACTICE_409_1`**. (`VISITED`로 바꾸는 것은 언제든 가능하고, 이때 사유는 비워진다.)
- 본인 항목이 아니면 **403**, 없는 `practiceId`는 404.

### 4. 목록에서 제거

```
DELETE /api/v1/practices/12   (JWT)
```

- 응답 데이터 없음(200). 본인 항목만(타인 403), 없으면 멱등 200.
- **이미 작성한 후기의 `is_verified_visit`은 영향받지 않는다**(작성 시점 스냅샷이라).

### 5. 미방문 이유 폼

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
      "textInputPlaceholder": "이유를 입력해주세요", "textInputMaxLength": 100 }
  ]
}
```

- 구조·직렬화 규칙은 신고 사유 폼과 동일(`global.common.form`). 텍스트 입력이 없는 선택지는 `textInput*` 키를 내려보내지 않는다.
- `code`가 그대로 상태 변경 요청의 `skipReason` 값이다.

### 6. 후기 방문 인증 (스펙 010 연계)

- **후기 작성 시**: 그 회원·장소의 `member_practice.visit_count > 0`이면(= 한 번이라도 방문 처리했으면) `review.is_verified_visit = true`로 저장한다. 지금 상태가 `PLANNED`(재도전 중)여도 지난 방문 이력이 있으면 인증으로 본다.
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

- 이 배지는 **클라이언트가 사용자의 이동을 추적해 자동 판정한 결과**에 근거한다. 사용자가 임의로 누르는 값은 아니지만 **서버가 검증한 것도 아니다** — 판정 기준·정확도는 클라이언트에 달려 있고, 서버 측 좌표 검증은 추후 과제다.

### 에러 코드 (`PracticeErrorCode` 신규)

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `PRACTICE_403_1` | 403 | 본인의 연습 항목만 변경·삭제할 수 있습니다. |
| `PRACTICE_400_1` | 400 | 미방문으로 변경하려면 사유가 필요합니다. |
| `PRACTICE_409_1` | 409 | 이미 등록한 미방문 사유는 변경할 수 없습니다. |

## 완료 조건 (Acceptance Criteria)

- [ ] 코스 상세에서 담으면 `PLANNED` 상태로 저장되고, 같은 장소를 다시 담아도 **행이 늘지 않는다**(상태만 `PLANNED`로 되돌아가고 `visitCount`는 유지).
- [ ] 코스·주차장 모두 담을 수 있고, 없는 장소를 담으면 404다.
- [ ] 내 목록이 **최근 방문순**(방문 이력 없으면 담은 시각 기준)으로 반환되고 `size`만큼 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성·중복 없음).
- [ ] `totalCount`는 첫 페이지에서만 내 연습 항목 총계로 채워진다.
- [ ] 목록 항목의 `place`가 `PlaceListItem`과 같은 구조이며 `distanceFromMe`는 `null`이다.
- [ ] `VISITED`로 바꾸면 `visitCount`가 1 오르고 `visitedAt`이 기록되며 `skipReason`·`skipDetail`이 비워진다. 다시 `VISITED`를 보내면 횟수가 또 오른다.
- [ ] `NOT_VISITED`인데 `skipReason`이 없으면 400, `OTHER`인데 `skipDetail`이 없으면 400이다.
- [ ] 이미 미방문 사유가 있는 항목에 다시 `NOT_VISITED`를 보내면 409이고, `VISITED`로는 바꿀 수 있다(이때 사유가 비워진다).
- [ ] 타인 항목의 상태 변경·삭제는 403이다.
- [ ] 미방문 이유 폼이 5개 선택지를 order 순으로 반환하고, `OTHER`만 `requiresTextInput=true`·placeholder·최대 길이를 갖는다.
- [ ] `visitCount > 0`인 장소에 후기를 쓰면 `isVerifiedVisit=true`, 한 번도 안 다녀왔으면 `false`로 저장된다.
- [ ] 연습 항목을 삭제해도 이미 작성한 후기의 `isVerifiedVisit`은 그대로다.
- [ ] 모든 엔드포인트가 미인증 시 401이다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 확정된 결정 (리뷰 반영)

- **같은 코스는 한 행** — 재연습은 `visit_count`로 누적한다(행을 늘리지 않음).
- **주차장도 담을 수 있다** — `place` 기준(코스·주차장 공통).
- **목록은 상태 필터 없이 최근 방문순** 한 줄기로 내려준다.
- **방문 판정은 클라이언트가 이동 추적으로 자동 수행** — 사용자가 버튼을 누르는 방식이 아니며, 서버는 호출 결과를 기록만 한다(중복 호출 방지도 클라이언트 몫, 서버 가드 없음).
- **상태 전이 자유** — `NOT_VISITED` → `VISITED`도 가능. 단 **미방문 사유는 한 번 저장하면 수정 불가**(409).
- **연습 횟수는 노출한다** — 목록뿐 아니라 코스 상세·마이페이지에도 쓸 예정(표시 위치·문구는 스펙 012에서 확정).

## 미해결 질문

- (없음 — 위 결정으로 모두 해소)

## 범위 밖 / 다음

- 위치 기반 방문 검증(좌표·체류 시간), 연습 예정 일시·알림.
- 미방문 이유 통계 대시보드, 추천 알고리즘 반영.
- 코스 상세 개편(스펙 012 예정).
