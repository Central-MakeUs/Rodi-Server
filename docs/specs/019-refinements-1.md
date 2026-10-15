# 1차 보완 (레벨별 연습 기록 · 유형 필터 · 온보딩 폼 · '없음' 배타 선택)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-09-27 | Draft | 최초 작성 — 데모데이 후 수정 4건. 온보딩 폼 **문구는 대기**(사용자 제공 예정), 구조만 확정 대상 |
| 2026-09-28 | Draft | 1차 리뷰 반영 — 방문 검사 순서(쿨다운 → 레벨)·담기 쿼리의 레벨 조건, `hasReview` 레벨 기준 확정, 중복 값 400 확정, 온보딩 문항은 구현 시 제공 |
| 2026-09-28 | Draft | 2차 리뷰 반영 — 지난 레벨 기록으로 방문 시 현재 레벨 기록에 기록(200)·방문 응답 `practiceId`, `level` CHECK 제약, 레벨 점수·Q4 조건 검증 제외 확정 |
| 2026-09-28 | Approved | 미방문 사유는 레벨 검사 없이 요청한 기록에 저장(현재 동작 유지, `PRACTICE_409_3` 제거 — 잠정, 미해결 질문 2). 스펙 승인 |
| 2026-09-29 | Approved | 온보딩 폼 문구·구조 확정 — 2단계(steps)·2단계 건너뛰기, Q1 `YEARS_3_9` = "3~9년", 연습유형 라벨을 온보딩 화면 표기로 통일(코스 등록 폼 공용), 연습유형은 평면 목록. 유형 필터 적용 범위 조정 여지(미해결 질문 3) |
| 2026-10-01 | Approved | **1번(레벨별 연습 기록) 보류** — 화면 도출 후 재확인·구현. #74는 2·3·4번만 |

## 배경 / 목적

데모데이 이후 나온 수정 요청 4건을 반영하는 **1차 보완**이다. 새 도메인은 없고 **이미 구현된 스펙 004·007·011의 동작을 바꾸는 작업**이라 [스펙 013](013-app-integration-refinements.md)처럼 한 문서로 묶는다. 온보딩 폼(3번)과 '없음' 배타 선택(4번)은 같은 문항 정의를 공유해 한 곳에서 봐야 앞뒤가 맞는다.

| # | 요청 | 성격 | 관련 스펙 | 진행 |
|---|------|------|-----------|------|
| 1 | 연습 기록을 **레벨별로** 저장·표시 | 스키마·정책 변경 | 011 · 012 · 013 | **보류** — 화면 도출 후 |
| 2 | 유형 필터를 정렬에서 **진짜 필터**로(매칭 장소만) | 동작 변경 | 007 | 구현 |
| 3 | 온보딩 폼을 서버에서 내려주기 | 신규 API | 004 | 구현 |
| 4 | 온보딩 '없음' 선택 시 복수 응답 금지 | 검증 추가 | 004 | 구현 |

> **1번은 보류한다(2026-10-01).** 레벨별 연습 기록 화면이 아직 도출되지 않아, 화면이 나온 뒤 이 스펙의 1번 설계(도메인 모델 1절·API 1절·완료 조건 1)를 다시 확인하고 구현한다. 이번 이슈(#74)는 2·3·4번만 담는다.

### 범위 밖

- **레벨 점수 계산의 서버 이관** — 레벨 변환(점수→레벨)은 지금처럼 클라이언트가 한다. 폼은 문항·선택지만 내려주고 점수는 담지 않는다(리뷰에서 제외 확정).
- 필터 저장 방식(`member.filter_tags`, `PUT /members/me/filter-tags`)과 카테고리 UX — 그대로다.
- 신고 사유·미방문 사유 폼(`FormResponse`) — 변경 없다.
- **Q4 값의 조건 검증** — `roadExperiences`에 `SOLO`가 없는데 `soloDrivingRange`·`soloParkingLevel`이 오는 경우를 400으로 막지 않는다. 폼의 `showWhen`으로 클라이언트가 막는다(리뷰에서 제외 확정).

## 요구사항

### 기능 요구사항

1. **레벨별 연습 기록**
   - 연습 항목은 **(회원, 장소, 레벨)마다 한 행**이다. 레벨이 오르면 같은 코스를 다시 [연습하기]해도 **새 기록**(연습 횟수 0, `PLANNED`)으로 시작하고, 지난 레벨의 기록은 그대로 남는다.
   - 내 연습 목록은 **레벨을 골라 조회**한다(`level` 파라미터). 생략하면 **현재 레벨**이다.
   - **지난 레벨 기록으로 방문이 오면 현재 레벨 기록에 남긴다.** 레벨이 오른 뒤 예전 기록(`practiceId`)으로 같은 장소를 다녀와도 200이다. 서버가 그 장소의 현재 레벨 기록을 찾거나 새로 만들어 방문을 기록하고, 지난 레벨 기록은 바뀌지 않는다.
   - **미방문 사유는 레벨과 관계없이 요청한 기록에 저장한다**(현재 동작 유지). 사유는 레벨·거리·인증에 영향이 없고, "그때 왜 못 갔나"라는 그 기록의 계획에 대한 답이라 그 레벨 기록에 남는 것이 맞다. 지난 레벨 기록은 다시 방문·[연습하기]되지 않으므로 거기 남긴 사유는 지워지지 않는다. **잠정 결정** — 409로 막을지는 화면 도출 후 정한다(미해결 질문 2, 보류).
   - 담기는 온보딩(레벨 배정)을 마친 회원만 할 수 있다.
2. **유형 필터**
   - 인증된 회원이 저장한 `filter_tags`가 있으면 **매칭되는 장소만** 반환한다. 비매칭 장소는 더 이상 후순위로도 나오지 않는다.
   - 적용 대상: **현위치 목록(`GET /places`) · 검색(`GET /places/search`) · 지도 마커(`GET /places/coordinates`)**.
   - 매칭 규칙은 기존과 같다 — 코스는 태그 중 하나라도 필터에 있으면(합집합), 주차장은 필터에 `PARKING`이 있으면.
   - 필터가 없으면(비로그인·빈 필터) 지금과 같이 전부 반환한다.
3. **온보딩 폼 조회** — 온보딩 문항·선택지·선택 규칙을 서버가 내려준다. 문구·순서를 앱 배포 없이 바꿀 수 있게 하는 것이 목적이다(코스 등록 폼과 같은 이유).
4. **'없음' 배타 선택** — 복수 선택 문항에서 '없음'은 **단독으로만** 고를 수 있다. 서버는 '없음'과 다른 선택지가 함께 오면 **400**으로 거부하고, 폼 응답에 배타 선택지임을 표시(`exclusive: true`)해 클라이언트가 UI에서도 막을 수 있게 한다.
   - 현재 해당 문항은 **Q3 도로 주행 경험(`roadExperiences`)의 `NONE`** 하나다. Q4-2의 `NONE`은 단일 선택이라 해당 없다.

### 비기능 요구사항

- **하위호환을 깨는 변경이 있다** — 목록·검색에서 비매칭 장소가 사라지고(2번), 필터 적용 시 `totalCount`가 줄어든다. 연습 목록은 기본이 현재 레벨이라 레벨업한 회원의 지난 기록이 기본 목록에서 빠진다(1번). 앱 업데이트와 함께 나간다.
- 방문 기록의 잠금 순서(**연습 항목 → 회원**, ADR 0012)는 그대로 유지한다.
- 스키마 변경(V27)은 한 커밋에 담고, 그 커밋 시점에 빌드·기동 가능해야 한다.

## 도메인 모델

### 1. member_practice — 레벨 컬럼 추가 (V27) — 보류

```sql
ALTER TABLE member_practice ADD COLUMN level VARCHAR(20);

-- 기존 기록은 회원의 현재 레벨 기록으로 본다(레벨 없는 회원은 SEED)
UPDATE member_practice mp
   SET level = COALESCE(m.level, 'SEED')
  FROM member m
 WHERE m.id = mp.member_id;

ALTER TABLE member_practice ALTER COLUMN level SET NOT NULL;

ALTER TABLE member_practice DROP CONSTRAINT uq_member_practice;
ALTER TABLE member_practice
    ADD CONSTRAINT uq_member_practice UNIQUE (member_id, place_id, level);

-- 레벨 값은 enum 밖의 값을 DB에서 막는다(후기 member_level의 ck_review_member_level과 같은 방식)
ALTER TABLE member_practice
    ADD CONSTRAINT ck_member_practice_level
        CHECK (level IN ('SEED', 'ROOKIE', 'OWNER', 'EXPLORER', 'NAVIGATOR'));

DROP INDEX idx_member_practice_recent;
CREATE INDEX idx_member_practice_recent
    ON member_practice (member_id, level, COALESCE(visited_at, created_at) DESC, id DESC);
```

- **`level`** = 이 기록이 속한 레벨. 담을 때 회원의 현재 레벨로 정해지고 이후 바뀌지 않는다(후기의 `member_level` 스냅샷과 같은 성질).
- **백필은 회원의 현재 레벨**로 한다. 기존 기록이 마이그레이션 직후 기본 목록(현재 레벨)에서 사라지지 않게 하기 위함이다. 과거에 어느 레벨에서 방문했는지는 남아 있지 않아 복원할 수 없다.
- 레벨 없는 회원(온보딩 미완료)의 기록은 SEED로 채운다. 온보딩을 마친 회원은 원래 레벨이 비어 있으면 안 되므로 해당 행은 없거나 드물다고 보고 따로 처리하지 않는다.
- **`verified_level`은 유지한다.** 새 기록에서는 항상 `null` 또는 `level`과 같은 값이 된다(방문은 항상 현재 레벨 기록에 남고, 인증 레벨로 **기록의 `level`**을 쓴다 — 승급을 일으킨 방문도 승급 전 레벨 = 기록의 레벨). 백필된 기존 행은 `level`보다 낮은 `verified_level`을 가질 수 있어, 후기 배지 판정(`existsByMemberIdAndPlaceIdAndVerifiedLevel`)을 바꾸지 않으려면 이 컬럼이 필요하다.

**승급을 일으킨 방문** — 스펙 013 규칙 그대로다. SEED 기록으로 방문해 ROOKIE가 되면 그 방문과 인증은 SEED 기록에 남고, ROOKIE에서는 다시 [연습하기]로 새 기록을 만든다.

**엔티티 규칙**

| 메서드 | 규칙 |
|--------|------|
| `MemberPractice.builder().level(...)` | 담을 때 회원 현재 레벨 필수 |
| `isCurrentFor(Level memberLevel)` | `level == memberLevel`. 방문 대상 결정에 쓴다 |
| `markVisited(...)` | 인증 시 `verified_level`에 회원 레벨 대신 **기록의 `level`**을 넣는다(값은 같지만, 드문 동시 승급에서도 `verified_level ≤ level`을 지킨다) |

**서비스 규칙**

- **담기** — 기존 기록 조회(`findByMemberIdAndPlaceId`)와 동시 담기 대비 INSERT(`insertIfAbsent`)에 모두 `level`을 조건으로 더한다. `insertIfAbsent`의 충돌 기준은 새 유니크 `(member_id, place_id, level)`이다.
- **방문 기록의 처리 순서** — 잠금은 **요청 기록 → 현재 레벨 기록 → 회원** 순이다.
  1. 요청한 기록을 잠근다(소유 확인).
  2. 요청한 기록이 **쿨다운**(10분) 안이면 현재 상태로 200. 승급을 일으킨 방문을 앱이 재시도하는 경우다.
  3. 회원 레벨을 읽는다. 요청한 기록이 현재 레벨이 아니면 그 장소의 **현재 레벨 기록을 찾거나 만들고**(`insertIfAbsent` 후 조회) 잠가 방문 대상으로 삼는다. 이 기록도 쿨다운 안이면 200(옮겨 기록한 방문의 재시도).
  4. 회원을 잠그고 방문을 기록한다(누적 거리·승급).
  - 레벨이 없는 회원(온보딩 미완료, 백필된 SEED 기록만 있음)은 옮길 레벨이 없으므로 요청한 기록에 그대로 남긴다.
  - 교착이 없는 이유: 옮기는 방향은 항상 낮은 레벨 → 현재 레벨이고, 연습 항목을 모두 잠근 뒤에 회원을 잠그므로 ADR 0012의 순서(연습 항목 → 회원)를 지킨다.
  - 3에서 읽은 레벨이 4의 잠금 전에 다른 방문으로 오를 수 있다(서로 다른 장소를 동시에 완주한 경우). 이때는 대상 기록에 그대로 기록한다 — 승급 직전 레벨의 방문이라 "승급을 일으킨 방문은 승급 전 레벨 기록에 남는다"는 규칙과 같다.
- **방문 응답** — 실제로 방문이 남은 기록의 `practiceId`를 응답에 추가한다. 옮겨 기록했으면 요청한 id와 다르므로, 앱은 이 값으로 목록을 갱신한다.
- **미방문 사유** — 변경 없다. 레벨을 검사하지 않고 요청한 기록에 저장한다.

### 2. 필터 — 쿼리 변경

- `findInViewportFiltered`·`searchByKeywordFiltered`의 `matched` 정렬을 **`WHERE` 조건**으로 바꾼다. 정렬·커서는 필터 없는 쿼리와 같은 `(distance, id)`가 되므로 **커서 형식이 하나로 합쳐진다**(`matched|distance` 합성 커서 제거).
- `totalCount`는 필터를 적용한 개수다(`countInViewportFiltered`·`countByKeywordFiltered` 추가).
- 마커(`findAllVisible`)에 같은 매칭 조건을 건 조회를 추가한다.

### 3. 온보딩 폼 — 응답 구조

공용 `FormResponse`는 단일 선택 문항 하나만 담아 쓰지 않는다. 코스 등록 폼처럼 **전용 응답**(`OnboardingFormResponse`)을 둔다.

- 화면이 **2단계**라 응답도 **단계(steps) → 문항(questions)** 구조다. 단계마다 제목·설명과 건너뛰기 가능 여부(`skippable`)를 내린다.
  - 1단계 "운전 경험": Q1~Q4-2, 건너뛰기 불가.
  - 2단계 "추가 정보": 선호 연습유형·차종·운전 목표, **건너뛰기 가능**.
- 문항 `code`는 **제출 요청의 필드명**과 1:1이다(`drivingPeriod` → 요청의 `drivingPeriod`). 문항 `order`는 단계를 넘어 1~8로 이어진다.
- 선택지 `code`는 **enum 값 그대로**, 순서는 enum 선언 순서다.
- **문구 위치**
  - 선택지 라벨: 각 enum의 `label` 필드(`DrivingPeriod`·`RecentFrequency`·`RoadExperience`·`SoloDrivingRange`·`SoloParkingLevel`·`CarType`에 추가). 기존 `PracticeType`·`SkipReason`과 같은 방식.
  - 단계·문항 제목·설명·placeholder: `OnboardingFormResponse.of()` 한 곳(코스 등록 폼과 같은 방식).
- **연습유형 라벨을 온보딩 화면 표기로 바꾼다.** `PracticeType` 라벨은 코스 등록 폼·코스 태그 표시와 공용이라 그쪽 표기도 함께 바뀐다(앱 전체 표기 통일).

  | enum | 기존 | 변경 |
  |------|------|------|
  | `LEFT_RIGHT_TURN` | 좌우회전 | 좌우 회전 |
  | `ROUNDABOUT` | 회전교차로 | 회전 교차로 |
  | `UNPROTECTED_LEFT_TURN` | 비보호좌회전 | 비보호 좌회전 |
  | `NARROW_ROAD` | 좁은 도로 | 좁은 도로 주행 |
  | `MULTILANE` | 다차로주행 | 다차로 주행 |

- 선호 연습유형은 화면이 카테고리 없이 13개를 나열하므로 **평면 목록**으로 내린다(`PracticeCategory` 트리 아님).
- 문항 유형: `SINGLE_SELECT` · `MULTI_SELECT` · `RANKED_SELECT`(선택 순서=우선순위) · `TEXT`.
- **흐름 규칙**도 함께 내린다. 지금 클라이언트에 하드코딩된 두 규칙이다.
  - Q1이 `YEARS_3_9`·`OVER_10_YEARS`면 후속 문항을 건너뛴다 → 해당 선택지에 `skipsFollowUps: true`(`DrivingPeriod.skipsFollowUps()`)
  - Q4-1·Q4-2는 Q3에서 `SOLO`를 골랐을 때만 → 문항에 `showWhen: { question: "roadExperiences", anyOf: ["SOLO"] }`

### 4. '없음' 배타 선택 — 검증

- `RoadExperience.NONE`에 배타 표시를 두고(예: `isExclusive()`), `OnboardingRequest`에 `@AssertTrue` 검증을 추가한다. 배타 값이 다른 값과 함께 있으면 400 `COMMON_400`(기존 검증 실패와 같은 코드).
- 폼 응답의 선택지 `exclusive`도 같은 enum 값에서 만든다. 검증과 폼이 **한 출처**를 본다.
- **같은 값 중복**(`["SOLO","SOLO"]`)도 400으로 막는다. 원래 요청 범위 밖이지만 비용이 작고 중복 값은 저장해도 의미가 없어 함께 막는다(확정).
- 원소 `null`(`[null]`)은 `List<@NotNull RoadExperience>`로 400. 배열 자체의 `null`·`[]`는 선택 문항이라 허용한다.

## API 명세

| Method | Path | 설명 | 인증 | 변경 |
|--------|------|------|------|------|
| POST | /api/v1/places/{placeId}/practices | 연습 담기 | JWT | 현재 레벨 기록으로 담기, 레벨 없으면 409 |
| GET | /api/v1/members/me/practices | 내 연습 목록 | JWT | `level` 파라미터·응답 `level` 추가 |
| POST | /api/v1/practices/{practiceId}/visits | 방문 기록 | JWT | 지난 레벨 기록이면 현재 레벨 기록에 기록, 응답 `practiceId` 추가 |
| GET | /api/v1/places | 현위치 목록 | 옵셔널 JWT | 정렬 → 필터 |
| GET | /api/v1/places/search | 검색 | JWT | 정렬 → 필터 |
| GET | /api/v1/places/coordinates | 지도 마커 | **옵셔널 JWT** | 필터 적용(인증 시) |
| GET | /api/v1/members/me/onboarding-form | 온보딩 폼 | JWT | **신규** |
| POST | /api/v1/members/me/onboarding | 온보딩 제출 | JWT | '없음' 배타·중복 검증 |

**컨트롤러 배치** — 온보딩 폼은 단발 엔드포인트라 소유 컨트롤러인 `MemberController`에 둔다(온보딩 제출과 같은 곳).

### 1. 레벨별 연습 기록 — 보류

```json
// GET /api/v1/members/me/practices?level=SEED&size=20   (level 생략 시 현재 레벨)
{
  "items": [
    {
      "practiceId": 12, "placeId": 1, "placeName": "한강 코스",
      "practiceTypes": ["STRAIGHT", "LANE_CHANGE"],
      "level": "SEED",
      "status": "VISITED", "visitCount": 2, "lastActivityAt": "2026-08-05T18:20:00",
      "hasReview": true, "isDeleted": false
    }
  ],
  "hasNext": false, "nextCursor": null, "totalCount": 1
}
```

- `level`은 유효한 레벨 enum. 잘못된 값이면 400. 레벨 없는 회원이 생략하면 빈 목록이다.
- `totalCount`는 그 레벨의 기록 수다.
- `hasReview`는 **이 기록의 레벨에서** 그 장소에 후기를 썼는지다(`review.member_level = practice.level`). 후기는 레벨마다 새로 쓸 수 있고 수정도 같은 레벨에서만 되므로(`isEditable`) 레벨별 기록과 기준을 맞춘다. 목록은 한 레벨만 조회하므로 `findReviewedPlaceIds`에 레벨 조건 하나만 더한다.

**에러**

| 상황 | 코드 |
|------|------|
| 레벨 없는 회원(온보딩 미완료)이 담기 | 409 `PRACTICE_409_2` 온보딩 후 이용 가능 |

### 2. 유형 필터

```text
GET /api/v1/places?swLat=..&neLng=..&lat=..&lng=..   (JWT, filter_tags = [U_TURN, PARKING])
→ U_TURN 태그 코스 + 주차장만, 거리순. totalCount도 이 조건의 개수
```

- 커서는 필터 유무와 관계없이 `(distance, id)` 한 형식이다. 배포 전에 발급된 필터 커서(`matched|distance`)는 400이 된다 — 첫 페이지부터 다시 부르면 된다.
- 마커 API는 `SecurityConfig`의 공개 경로를 유지하고 `@CurrentMember(required = false)`로 받는다.

### 3. 온보딩 폼 (신규)

```json
// GET /api/v1/members/me/onboarding-form   (JWT)
{
  "steps": [
    {
      "order": 1, "skippable": false,
      "title": "운전 경험에 대해 알려주세요.",
      "description": "자세히 입력할수록 더 잘 맞는 연습 장소를 추천해요.",
      "questions": [
        {
          "code": "drivingPeriod", "order": 1, "type": "SINGLE_SELECT", "required": true,
          "title": "면허 취득 후 실제 운전한 기간을 알려주세요",
          "options": [
            { "code": "UNDER_1_MONTH", "label": "1개월 미만", "order": 1 },
            { "code": "MONTHS_1_2", "label": "1~2개월", "order": 2 },
            { "code": "MONTHS_3_5", "label": "3~5개월", "order": 3 },
            { "code": "MONTHS_6_11", "label": "6~11개월", "order": 4 },
            { "code": "YEARS_1_2", "label": "1~2년", "order": 5 },
            { "code": "YEARS_3_9", "label": "3~9년", "order": 6, "skipsFollowUps": true },
            { "code": "OVER_10_YEARS", "label": "10년 이상", "order": 7, "skipsFollowUps": true }
          ]
        },
        {
          "code": "recentFrequency", "order": 2, "type": "SINGLE_SELECT", "required": false,
          "title": "가장 최근, 운전을 언제 하셨나요?",
          "options": [ "RARELY 거의 없음", "MONTHLY_1_2 월 1~2회", "WEEKLY_1 주 1회", "WEEKLY_2_3 주 2~3회", "WEEKLY_4_PLUS 주 4회 이상" ]
        },
        {
          "code": "roadExperiences", "order": 3, "type": "MULTI_SELECT", "required": false,
          "title": "면허 취득후 도로주행을 해본 적이 있나요?",
          "options": [
            { "code": "NONE", "label": "없음", "order": 1, "exclusive": true },
            { "code": "ACCOMPANIED", "label": "동승 연습", "order": 2 },
            { "code": "PROFESSIONAL_TRAINING", "label": "전문 도로 연수", "order": 3 },
            { "code": "SOLO", "label": "혼자 연습", "order": 4 }
          ]
        },
        {
          "code": "soloDrivingRange", "order": 4, "type": "SINGLE_SELECT", "required": false,
          "title": "혼자 운전, 어디까지 해봤나요?",
          "showWhen": { "question": "roadExperiences", "anyOf": ["SOLO"] },
          "options": [ "NEAR_HOME 집 근처", "FAMILIAR_ROAD 익숙한 길", "UNFAMILIAR_ROAD 낯선 도로", "HIGHWAY_LONG 고속 장거리" ]
        },
        {
          "code": "soloParkingLevel", "order": 5, "type": "SINGLE_SELECT", "required": false,
          "title": "혼자 주차는 어느 정도 해봤나요?",
          "showWhen": { "question": "roadExperiences", "anyOf": ["SOLO"] },
          "options": [ "NONE 없음", "WIDE_ONLY 넓은 곳만", "FAMILIAR_PLACE 익숙한 곳", "MOSTLY_POSSIBLE 대부분 가능" ]
        }
      ]
    },
    {
      "order": 2, "skippable": true,
      "title": "추가 정보를 입력하면 더 정확해요.",
      "description": "딱 맞는 코스 추천을 위한 선택항목이에요.",
      "questions": [
        {
          "code": "practiceTypes", "order": 6, "type": "RANKED_SELECT", "required": false, "maxSelect": 3,
          "title": "더 연습해보고 싶은 상황이 있나요?",
          "description": "1순위부터 순서대로 선택해주세요.",
          "options": [ "PracticeType 13종, enum 선언 순서, label은 PracticeType.label" ]
        },
        {
          "code": "carType", "order": 7, "type": "SINGLE_SELECT", "required": false,
          "title": "주로 타는 차종은 무엇인가요?",
          "options": [ "LIGHT 경차", "COMPACT 소형차", "MIDSIZE 중형차", "SEMI_LARGE 준대형", "LARGE 대형차", "SUV SUV" ]
        },
        {
          "code": "drivingGoal", "order": 8, "type": "TEXT", "required": false, "maxLength": 30,
          "title": "이루고 싶은 운전 목표를 입력해주세요.",
          "placeholder": "ex)강남 운전 자신있게 하기!"
        }
      ]
    }
  ]
}
```

> 지면상 일부 `options`는 `"코드 라벨"` 문자열로 줄여 적었다. 실제 응답은 모두 `{ code, label, order }` 객체다.

- `required`는 제출 요청의 검증과 같다 — 필수는 Q1뿐이다(`level`은 문항이 아니라 클라이언트가 계산해 제출).
- `maxSelect`(3)는 제출 검증의 `@Size(max = 3)`, `maxLength`(30)는 `Member.DRIVING_GOAL_MAX_LENGTH`와 같은 값을 쓴다.
- 값이 없는 선택 속성(`description`·`showWhen`·`maxSelect`·`maxLength`·`placeholder`·`exclusive`·`skipsFollowUps`)은 생략한다(NON_NULL). `exclusive`·`skipsFollowUps`는 `true`일 때만 내린다.
- 저장이 없는 조회라 트랜잭션을 열지 않는다.

### 4. 온보딩 제출 — 배타·중복 검증

```json
// POST /api/v1/members/me/onboarding
{ "drivingPeriod": "MONTHS_1_2", "roadExperiences": ["NONE", "SOLO"], "level": "SEED" }
// → 400 COMMON_400 ('없음'은 다른 선택지와 함께 고를 수 없음)
```

- `["NONE"]` 단독, `["ACCOMPANIED","SOLO"]`는 정상이다. 빈 배열·생략도 기존처럼 허용한다(선택 문항).

## 완료 조건 (Acceptance Criteria)

**1. 레벨별 연습 기록** — 보류(화면 도출 후)
- [ ] V27 적용 후 기존 기록의 `level`이 회원의 현재 레벨(없으면 SEED)로 채워지고, 유니크가 `(member_id, place_id, level)`이 된다.
- [ ] SEED 회원이 담은 코스를 ROOKIE 승급 후 다시 담으면 **새 행**(visitCount 0, PLANNED, level=ROOKIE)이 생기고 SEED 행은 그대로다.
- [ ] 같은 레벨에서 다시 담으면 기존처럼 행을 늘리지 않고 PLANNED로 되돌린다(연습 횟수 유지).
- [ ] 목록은 `level` 생략 시 현재 레벨 기록만, `level=SEED`면 SEED 기록만 반환하고 `totalCount`도 그 레벨 기준이다. 잘못된 `level`은 400.
- [ ] ROOKIE 회원이 SEED 기록으로 방문하면 200이고, 그 장소의 ROOKIE 기록이 생기거나(없으면) 갱신된다. 응답 `practiceId`는 ROOKIE 기록이고 SEED 기록은 바뀌지 않는다.
- [ ] 지난 레벨 기록에도 미방문 사유를 제출할 수 있고(200), 그 기록에만 저장된다.
- [ ] V27 후 `level`에 enum 밖의 값을 넣으면 DB가 거부한다(`ck_member_practice_level`).
- [ ] 레벨 없는 회원의 담기는 409 `PRACTICE_409_2`.
- [ ] 승급을 일으킨 방문은 승급 전 레벨 기록에 남고, 인증 레벨도 승급 전 레벨이다(기존 규칙 유지).
- [ ] 승급을 일으킨 방문을 쿨다운 안에 재시도하면 409가 아니라 200(현재 상태 그대로)이다.
- [ ] 목록의 `hasReview`는 그 레벨에서 쓴 후기만 본다 — SEED에서 후기를 쓴 장소의 ROOKIE 기록은 `false`.

**2. 유형 필터**
- [ ] 필터가 있는 인증 회원의 목록·검색·마커에 **비매칭 장소가 나오지 않는다**(태그가 겹치지 않는 코스, 필터에 PARKING이 없을 때의 주차장).
- [ ] 필터 적용 시 `totalCount`가 매칭 장소 수와 같다.
- [ ] 필터 적용 목록의 2페이지가 `(distance, id)` 커서로 누락·중복 없이 이어진다.
- [ ] 비로그인·빈 필터는 기존과 같은 결과(전부, 거리순)다. 마커는 토큰 없이도 200이다.

**3·4. 온보딩**
- [ ] 온보딩 폼이 2단계를 반환한다 — 1단계(Q1~Q4-2, `skippable: false`)·2단계(연습유형·차종·운전 목표, `skippable: true`). 문항은 `order` 1~8 순서다.
- [ ] 선택지는 enum 전체와 같은 개수·선언 순서이고, 라벨이 화면 문구와 같다(Q1 `YEARS_3_9` = "3~9년").
- [ ] `PracticeType` 라벨이 온보딩 화면 표기로 바뀌고, 코스 등록 폼에도 같은 라벨이 나온다.
- [ ] Q4-1·Q4-2에만 `showWhen`이 있다.
- [ ] `roadExperiences`의 `NONE` 선택지에만 `exclusive: true`, Q1의 `YEARS_3_9`·`OVER_10_YEARS`에만 `skipsFollowUps: true`가 있다.
- [ ] `roadExperiences`에 `NONE`과 다른 값이 함께 오면 400이고 아무것도 저장되지 않는다. `["NONE"]` 단독은 200.
- [ ] `roadExperiences`에 같은 값이 중복되면 400.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

1. ~~온보딩 폼 문항·문구~~ — 2026-09-29 화면 디자인으로 확정(도메인 모델 3절·API 3절).
2. **지난 레벨 기록의 미방문 사유를 409로 막을지** — 1번과 함께 **보류(화면 도출 후 결정)**. 현재 설계는 허용(레벨 검사 없이 그 기록에 저장)이다. 지난 기록을 완전히 고정하려면 409 `PRACTICE_409_3`으로 막고 앱이 지난 레벨 목록에서 "안 했어요"를 숨긴다.
3. **유형 필터 적용 범위** — 지금은 목록·검색·마커다. 연관 검색어(`/places/related-search`)는 필터 없이 전부 제안해, 자동완성에 뜬 장소가 필터 때문에 검색 결과에 없을 수 있다. 적용 범위는 앱 화면 흐름을 보고 조정할 수 있다(2026-09-28 사용자).
