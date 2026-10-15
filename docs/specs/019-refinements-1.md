# 1차 보완 (유형 필터·정렬 분리 · 온보딩 폼 · '없음' 배타 선택)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-09-27 | Draft | 최초 작성 — 데모데이 후 수정 4건. 온보딩 폼 **문구는 대기**(사용자 제공 예정), 구조만 확정 대상 |
| 2026-09-28 | Draft | 1차 리뷰 반영 — 방문 검사 순서(쿨다운 → 레벨)·담기 쿼리의 레벨 조건, `hasReview` 레벨 기준 확정, 중복 값 400 확정, 온보딩 문항은 구현 시 제공 |
| 2026-09-28 | Draft | 2차 리뷰 반영 — 지난 레벨 기록으로 방문 시 현재 레벨 기록에 기록(200)·방문 응답 `practiceId`, `level` CHECK 제약, 레벨 점수·Q4 조건 검증 제외 확정 |
| 2026-09-28 | Approved | 미방문 사유는 레벨 검사 없이 요청한 기록에 저장(현재 동작 유지, `PRACTICE_409_3` 제거 — 잠정, 미해결 질문 2). 스펙 승인 |
| 2026-09-29 | Approved | 온보딩 폼 문구·구조 확정 — 2단계(steps)·2단계 건너뛰기, Q1 `YEARS_3_9` = "3~9년", 연습유형 라벨을 온보딩 화면 표기로 통일(코스 등록 폼 공용), 연습유형은 평면 목록. 유형 필터 적용 범위 조정 여지(미해결 질문 3) |
| 2026-10-01 | Approved | **1번(레벨별 연습 기록) 보류** — 화면 도출 후 재확인·구현. #74는 2·3·4번만 |
| 2026-10-15 | Draft | **2번 재설계** — 첫 진입 시 매칭 코스가 없어 빈 화면이 자주 나오는 문제로, 저장 필터로 매칭 장소만 주던 방식(9dc4505)을 되돌리고 **저장 태그는 정렬 기준(`sort_tags`), 필터는 요청 파라미터(`practiceTypes`)**로 분리. 옛 PUT 경로는 별칭으로 유지. **1번은 [스펙 022](022-level-practice-record.md)으로 분리(추후 구현)** |
| 2026-10-15 | Approved | 2번 재설계 승인 — 비로그인 파라미터 필터 허용(미해결 질문 4), 마이그레이션은 V27(먼저 머지되는 #74가 사용, #75 닉네임은 rebase 시 V28로) |
| 2026-10-15 | Approved | **필터와 정렬 동시 적용 · 쿼리 통합** — `practiceTypes`가 있어도 `sort_tags` 정렬을 적용한다(필터 안에서 매칭 우선). 모드별 쿼리(기본·정렬·필터)를 목록·검색 각각 **하나의 쿼리**로 합치고, 커서 `sortValue`를 항상 `matched\|distance`로 통일(거리만 있는 커서는 matched=0으로 받음) |

## 배경 / 목적

데모데이 이후 나온 수정 요청을 반영하는 **1차 보완**이다. 새 도메인은 없고 **이미 구현된 스펙 004·007의 동작을 바꾸는 작업**이라 [스펙 013](013-app-integration-refinements.md)처럼 한 문서로 묶는다. 온보딩 폼(3번)과 '없음' 배타 선택(4번)은 같은 문항 정의를 공유해 한 곳에서 봐야 앞뒤가 맞는다.

| # | 요청 | 성격 | 관련 스펙 | 진행 |
|---|------|------|-----------|------|
| 1 | 연습 기록을 **레벨별로** 저장·표시 | 스키마·정책 변경 | 011 · 012 · 013 | **추후 구현** — [스펙 022](022-level-practice-record.md)으로 분리 |
| 2 | 저장 태그는 **정렬**, 필터는 **요청 파라미터**로 분리 | 동작 변경·파라미터 추가 | 007 | 구현 |
| 3 | 온보딩 폼을 서버에서 내려주기 | 신규 API | 004 | 구현 |
| 4 | 온보딩 '없음' 선택 시 복수 응답 금지 | 검증 추가 | 004 | 구현 |

**2번이 바뀐 이유(2026-10-15)** — 처음에는 저장된 `filter_tags`로 **매칭 장소만** 주도록 바꿨다(9dc4505). 그런데 아직 코스 수가 적어, 필터를 저장한 회원이 앱에 들어오자마자 매칭 장소가 없는 빈 화면을 자주 보게 된다. 목표는 **빈 화면을 최대한 덜 보여주는 것**이다.

- 저장된 태그는 **정렬 기준으로만** 쓴다(매칭 장소를 위로, 나머지도 전부 보여줌). 스펙 007의 기존 동작 그대로라 9dc4505를 되돌렸다. 저장 값의 쓰임이 정렬이므로 이름을 `filter_tags` → **`sort_tags`**로 바꾼다.
- **필터는 사용자가 직접 적용할 때만** 건다. 앱이 필터 선택을 로컬에 저장하고 요청마다 `practiceTypes` 파라미터로 보낸다. 서버는 필터 상태를 저장하지 않는다.
- 앱의 초기 필터값은 없다(첫 진입은 항상 필터 없음).

> **1번은 [스펙 022](022-level-practice-record.md)으로 옮겼다(추후 구현).** 레벨별 연습 기록 화면이 도출된 뒤 그 스펙을 다시 확인하고 이슈를 연다. 이번 이슈(#74)는 2·3·4번만 담는다.

### 범위 밖

- **레벨 점수 계산의 서버 이관** — 레벨 변환(점수→레벨)은 지금처럼 클라이언트가 한다. 폼은 문항·선택지만 내려주고 점수는 담지 않는다(리뷰에서 제외 확정).
- **`sort_tags` 자동 채우기** — 온보딩 제출 시 연습유형을 `sort_tags`로 복사하지 않는다. 지금처럼 앱이 온보딩 후 PUT으로 저장한다.
- **옛 경로 `PUT /members/me/filter-tags` 제거** — 앱이 새 경로로 바뀐 뒤 별도 이슈에서 지운다.
- **카테고리 UX** — 그대로 클라이언트 전용 개념이다. 서버는 풀린 `PracticeType` 목록만 받는다.
- 신고 사유·미방문 사유 폼(`FormResponse`) — 변경 없다.
- **Q4 값의 조건 검증** — `roadExperiences`에 `SOLO`가 없는데 `soloDrivingRange`·`soloParkingLevel`이 오는 경우를 400으로 막지 않는다. 폼의 `showWhen`으로 클라이언트가 막는다(리뷰에서 제외 확정).

## 요구사항

### 기능 요구사항

2. **유형 필터·정렬 분리**
   - **정렬 기준 저장(`sort_tags`)** — `member.filter_tags`를 `sort_tags`로 이름만 바꾼다. 값·의미(전체 교체, 빈 목록이면 해제)는 그대로다.
     - 새 경로 `PUT /members/me/sort-tags`(`{ "sortTags": [...] }`)로 저장한다.
     - 옛 경로 `PUT /members/me/filter-tags`(`{ "filterTags": [...] }`)는 **별칭으로 남긴다.** 같은 서비스 메서드를 불러 `sort_tags`에 저장한다. Swagger에 deprecated로 표시하고, 호출될 때마다 INFO 로그를 남겨 앱이 아직 옛 경로를 쓰는지 확인할 수 있게 한다.
   - **필터와 정렬은 독립적으로 함께 적용된다** — 목록(`GET /places`)·검색(`GET /places/search`)에서
     - **필터(무엇을 보여줄지)**: `practiceTypes`가 있으면 맞는 장소**만**, 없으면 전부.
     - **정렬(어떤 순서로)**: 인증 회원의 `sort_tags`가 있으면 그에 맞는 장소가 **먼저**, 그다음 나머지. 같은 그룹 안에서는 거리 → id. `sort_tags`가 없거나 비로그인이면 거리 → id.

     | `practiceTypes` | 인증 + `sort_tags` | 결과 | 순서 |
     |------|------|------|------|
     | 없음 | 없음 | 전부 | 거리 |
     | 없음 | 있음 | 전부 | `sort_tags` 매칭 우선 → 거리 (스펙 007과 같음) |
     | 있음 | 없음 | 필터 매칭만 | 거리 |
     | 있음 | 있음 | 필터 매칭만 | 그 안에서 `sort_tags` 매칭 우선 → 거리 |
   - **지도 마커(`GET /places/coordinates`)** — `practiceTypes`가 있으면 매칭 장소만, 없으면 전부. 순서가 의미 없어 `sort_tags`는 쓰지 않는다. 지금처럼 인증 없이 호출한다.
   - **매칭 규칙**은 필터와 정렬이 같다 — 코스는 태그 중 하나라도 기준에 있으면(합집합), 주차장은 기준에 `PARKING`이 있으면.
   - **인증** — 목록은 옵셔널 JWT, 검색은 **JWT 필수**(변경 없음), 마커는 공개. 파라미터 필터는 토큰과 관계없이 동작한다.
   - 연관 검색어(`/places/related-search`)는 변경 없다(필터·정렬 없음).
3. **온보딩 폼 조회** — 온보딩 문항·선택지·선택 규칙을 서버가 내려준다. 문구·순서를 앱 배포 없이 바꿀 수 있게 하는 것이 목적이다(코스 등록 폼과 같은 이유).
4. **'없음' 배타 선택** — 복수 선택 문항에서 '없음'은 **단독으로만** 고를 수 있다. 서버는 '없음'과 다른 선택지가 함께 오면 **400**으로 거부하고, 폼 응답에 배타 선택지임을 표시(`exclusive: true`)해 클라이언트가 UI에서도 막을 수 있게 한다.
   - 현재 해당 문항은 **Q3 도로 주행 경험(`roadExperiences`)의 `NONE`** 하나다. Q4-2의 `NONE`은 단일 선택이라 해당 없다.

### 비기능 요구사항

- **하위호환을 지킨다** — 서버만 먼저 배포해도 기존 앱이 그대로 동작한다.
  - 파라미터 없는 요청은 develop과 같은 결과다(정렬 동작 그대로, 커서 형식도 그대로).
  - 옛 PUT 경로·요청 본문이 그대로 동작한다.
  - 앱이 `practiceTypes`를 보내기 전까지는 필터가 걸리지 않는다(괜찮다고 확인, 2026-10-15).
- 컬럼 이름 변경(V27)은 한 커밋에 담고(#75 닉네임 브랜치도 V27을 쓰므로 #74를 먼저 머지하고 #75는 V28로 바꾼다), 그 커밋 시점에 빌드·기동 가능해야 한다(엔티티 매핑을 같은 커밋에서 바꾼다).

## 도메인 모델

### 2. 정렬 기준 저장·필터 쿼리

**V27 — 컬럼 이름 변경**

```sql
ALTER TABLE member RENAME COLUMN filter_tags TO sort_tags;
```

- 데이터·타입(jsonb `List<PracticeType>`)은 그대로다.
- 엔티티·서비스 이름도 맞춘다: `Member.filterTags` → `sortTags`, `updateFilterTags()` → `updateSortTags()`, `MemberFilterService` → `MemberSortService`, 요청 DTO `SortTagsRequest { sortTags }`. 옛 경로용 `FilterTagsRequest { filterTags }`는 별칭 제거 때 함께 지운다.

**PUT 호출 흐름**

```text
PUT /members/me/sort-tags   { sortTags }    → MemberController.updateSortTags()   ─┐
PUT /members/me/filter-tags { filterTags }  → MemberController.updateFilterTags() ─┤ (deprecated, INFO 로그)
                                                                                   ▼
                                          MemberSortService.updateSortTags(memberId, types)
                                            → 회원 조회(없으면 404) → Member.updateSortTags(types)
```

두 엔드포인트는 단발 엔드포인트라 소유 컨트롤러인 `MemberController`에 나란히 둔다.

**쿼리 — 목록·검색 각각 하나로 합친다.** 필터와 정렬 기준을 모두 파라미터로 받고, 쓰지 않는 쪽은 플래그로 끈다. 모드마다 쿼리를 따로 두면 필터+정렬 조합까지 4벌이 되고 서비스도 모드 분기가 늘어서다.

```sql
SELECT ...,
       CASE WHEN :useSort AND <sort 기준 매칭> THEN 1 ELSE 0 END AS matched
FROM place p
WHERE <뷰포트 또는 키워드> AND <노출 조건(VISIBLE)>
  AND (:useFilter = false OR <filter 기준 매칭>)
-- 커서 keyset (matched, distance, id)
ORDER BY matched DESC, distance ASC, id ASC
```

| 용도 | 목록 | 검색 |
|------|------|------|
| 데이터 | `findInViewport` | `searchByKeyword` |
| 건수(totalCount) | `countInViewport` | `countByKeyword` |

- 매칭 규칙(코스는 태그 중 하나라도, 주차장은 PARKING 포함 시)은 필터용 `MATCHES_FILTER`와 정렬용 `MATCHES_SORT` 두 상수로 둔다. `@Query`에는 컴파일 타임 상수만 들어가 파라미터 이름을 바꿔 끼울 수 없으므로, **파라미터 이름만 다르고 술어는 같은 두 상수**다. 같은 규칙인지는 테스트로 지킨다(같은 기준을 필터와 정렬에 넣으면 매칭 장소 집합이 같다).
- 쓰지 않는 기준은 `IN ()`(빈 목록)이 SQL 오류이므로 자리표시 목록을 넘기고 플래그로 끈다. 이 처리는 서비스 한 곳에서 한다.
- `sort_tags`가 없으면 모든 행의 `matched`가 0이라 결과는 거리순과 같다.
- 건수는 필터만 반영한다(정렬은 개수에 영향 없음).
- 서비스는 모드 분기 없이 요청의 `practiceTypes`와 회원의 `sort_tags`를 채워 한 쿼리를 부른다.
- 지도 마커는 순서가 없어 정렬과 무관하므로 지금처럼 `findAllVisible`·`findAllVisibleFiltered`(JPQL) 둘로 둔다.

- 지금(develop)의 `*Filtered`는 정렬 쿼리라 `*Sorted`로 이름을 바꾸고, `*Filtered`는 매칭만 거르는 쿼리로 새로 둔다(9dc4505에 있던 쿼리를 다시 쓴다).

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
| GET | /api/v1/places | 현위치 목록 | 옵셔널 JWT | `practiceTypes` 파라미터 추가(필터) |
| GET | /api/v1/places/search | 검색 | JWT | `practiceTypes` 파라미터 추가(필터) |
| GET | /api/v1/places/coordinates | 지도 마커 | 없음 | `practiceTypes` 파라미터 추가(필터) |
| PUT | /api/v1/members/me/sort-tags | 정렬 기준 저장 | JWT | **신규**(filter-tags의 새 이름) |
| PUT | /api/v1/members/me/filter-tags | 정렬 기준 저장(옛 경로) | JWT | **deprecated** — 별칭 유지, 호출 로그 |
| GET | /api/v1/members/me/onboarding-form | 온보딩 폼 | JWT | **신규** |
| POST | /api/v1/members/me/onboarding | 온보딩 제출 | JWT | '없음' 배타·중복 검증 |

**컨트롤러 배치** — 온보딩 폼·sort-tags는 단발 엔드포인트라 소유 컨트롤러인 `MemberController`에 둔다.

### 2. 유형 필터·정렬

**필터 파라미터**

```text
GET /api/v1/places?swLat=..&swLng=..&neLat=..&neLng=..&lat=..&lng=..&practiceTypes=U_TURN&practiceTypes=PARKING
GET /api/v1/places?...&practiceTypes=U_TURN,PARKING          (콤마 형식도 같은 의미)
→ U_TURN 태그 코스 + 주차장만, 거리순. totalCount도 이 조건의 개수
```

- `practiceTypes`는 `PracticeType` enum 목록이다. 반복(`?a=X&a=Y`)·콤마(`?a=X,Y`) 형식을 모두 받는다. 중복 값은 하나로 본다.
- 생략하거나 빈 값(`practiceTypes=`)이면 필터 없음이다.
- 없는 enum 값이면 400 `COMMON_400`.
- 검색·마커도 같은 파라미터·규칙이다.

**정렬 (인증 + `sort_tags` 있음)** — 스펙 007 그대로이고, 필터와 함께 쓰면 필터 결과 안에서 정렬한다.

```text
GET /api/v1/places?...   (JWT, sort_tags = [U_TURN, PARKING])
→ U_TURN 태그 코스·주차장이 먼저(거리순), 그다음 나머지 전부(거리순). totalCount는 전부의 개수

GET /api/v1/places?...&practiceTypes=PARKING,INTERSECTION   (JWT, sort_tags = [INTERSECTION])
→ 주차장·교차로 코스만. 그중 교차로 코스가 먼저(거리순), 그다음 주차장(거리순). totalCount는 필터 매칭 수
```

**커서** — 커서 페이징은 그대로이고, 커서 안의 `sortValue`가 **항상 `matched|distance`** 한 형식이 된다(쿼리가 하나라 정렬 키가 늘 `(matched, distance, id)`).

- 다음 페이지는 **첫 페이지와 같은 조건**(파라미터·토큰)으로 부른다. 조건이 바뀌면 첫 페이지부터 다시 부른다.
- **거리만 있는 커서(`distance`)도 받는다** — `matched=0`으로 해석한다. 배포 전 앱이 정렬 없이 받은 커서는 이 형식이고, 그때 모든 행이 `matched=0`이라 해석이 정확하다. 배포 순간 스크롤 중이던 사용자가 400을 받지 않게 하기 위함이다.
- 형식·값이 비정상인 커서(숫자 아님, `matched`가 0·1 외, 음수·NaN 거리)는 400 `COMMON_400`.

**정렬 기준 저장**

```json
// PUT /api/v1/members/me/sort-tags   (JWT)
{ "sortTags": ["U_TURN", "INTERSECTION", "PARKING"] }
// → 200, 본문 없음. 빈 배열이면 해제

// PUT /api/v1/members/me/filter-tags   (JWT, deprecated — 같은 동작)
{ "filterTags": ["U_TURN", "INTERSECTION", "PARKING"] }
```

- 검증은 기존과 같다 — 목록 누락·원소 `null`·없는 enum 값은 400, 없는 회원은 404.

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

**2. 유형 필터·정렬**
- [ ] V27 후 `member.sort_tags`에 기존 `filter_tags` 값이 그대로 남아 있다.
- [ ] `PUT /members/me/sort-tags`와 `PUT /members/me/filter-tags`가 모두 200이고, 같은 `sort_tags` 컬럼에 저장된다. 빈 배열이면 해제된다.
- [ ] 옛 경로를 호출하면 INFO 로그가 한 줄 남는다.
- [ ] `practiceTypes`가 있으면 목록·검색·마커에 **비매칭 장소가 나오지 않는다**(태그가 겹치지 않는 코스, 파라미터에 PARKING이 없을 때의 주차장).
- [ ] 필터 적용 시 `totalCount`가 매칭 장소 수와 같고, 2페이지가 누락·중복 없이 이어진다.
- [ ] `sort_tags`가 없으면(비로그인 포함) 필터 결과가 거리순이다.
- [ ] `practiceTypes`와 `sort_tags`가 함께 있으면 **필터 매칭 장소만, 그 안에서 `sort_tags` 매칭이 먼저** 나오고, 정렬 경계를 넘는 2페이지가 누락·중복 없이 이어진다.
- [ ] 파라미터 없이 `sort_tags`가 있는 인증 회원은 **매칭 장소가 먼저, 비매칭도 전부** 나오고 2페이지가 이어진다(develop과 같은 결과).
- [ ] 비로그인·빈 `sort_tags`이고 파라미터도 없으면 전부, 거리순이다. 마커는 토큰 없이 200이다.
- [ ] 같은 연습유형을 필터와 정렬 기준에 넣으면 필터 결과와 정렬의 매칭 그룹이 같은 장소 집합이다(두 매칭 상수의 규칙 일치).
- [ ] 다음 커서는 항상 `matched|distance` 형식이고, 거리만 있는 커서(`distance`)도 `matched=0`으로 받아 이어진다. 비정상 커서는 400이다.
- [ ] 반복·콤마 형식이 같은 결과를 내고, 없는 enum 값은 400이다.

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
2. ~~지난 레벨 기록의 미방문 사유를 409로 막을지~~ — 1번과 함께 [스펙 022](022-level-practice-record.md)으로 옮겼다.
3. **유형 필터 적용 범위** — 목록·검색·마커다. 연관 검색어(`/places/related-search`)는 필터 없이 전부 제안해, 자동완성에 뜬 장소가 필터 때문에 검색 결과에 없을 수 있다. 적용 범위는 앱 화면 흐름을 보고 조정할 수 있다(2026-09-28 사용자).
4. ~~비로그인 필터 허용~~ — **허용으로 확정(2026-10-15)**. 스펙 007·ADR 0011은 "필터는 로그인 전용"이었다. 파라미터 방식에서는 목록·마커에 비로그인도 `practiceTypes`를 보낼 수 있고, 이 스펙은 **허용**으로 둔다(필터 상태가 앱 로컬이라 막을 근거가 없음). 비로그인에게 필터 UI를 보여줄지는 앱이 정한다. 검색은 JWT 필수라 해당 없다.
