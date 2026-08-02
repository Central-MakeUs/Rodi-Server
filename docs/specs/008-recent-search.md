# 최근 검색어 (등록·조회·삭제)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-26 | Draft | 최초 작성 |
| 2026-07-26 | Draft | 보류 결정 — 검색 로그인 전용화 먼저 처리, 최근 검색어는 후순위. **보관 상한 15개 확정** |
| 2026-07-27 | Approved | 구현 착수 — 자동 저장(검색 시)·동기 처리로 1차 구현(PR #57) |
| 2026-07-28 | Draft | **재설계** — 검색 시 자동저장 **제거**, 연관검색어(스펙 009)에서 **선택 시 프론트가 POST로 등록**. 항목에 **type(REGION/PLACE)**·place는 **placeId** 추가 |
| 2026-08-03 | In Progress | **재설계 구현 완료** — V11(TRUNCATE·type·place_id·부분 유니크), POST 등록·종류별 upsert, 자동저장 훅 제거, GET에 type/placeId. 테스트 통과 |

## 배경 / 목적

로그인 회원이 이전에 고른 검색 항목을 다시 빠르게 찾도록 **최근 검색어**를 서버에 보관한다. 검색창 진입 시 최신순 목록으로 보여주고, 개별·전체 삭제를 지원한다. **로그인 유저 전용**.

**핵심 변경(재설계)**: 최근 검색어는 **연관 검색어(스펙 009)에서 사용자가 선택한 항목**만 등록된다(원시 입력 키워드 아님). 항목은 **지역(REGION)** 또는 **장소(PLACE)** 두 종류이며, 조회 시 `type`으로 구분한다. 등록 시점은 **프론트가 선택 순간 POST 호출**한다(검색 API의 자동 저장 로직은 삭제).

**범위 밖**: 인기 검색어, 검색어 기반 추천.

## 요구사항

### 기능 요구사항

1. **등록(POST)**: 연관검색어에서 지역/장소를 선택하면 프론트가 그 항목을 등록한다.
   - **type**: `REGION`(지역명) 또는 `PLACE`(장소명). PLACE는 `placeId`도 함께 저장(탭 시 상세 직행).
   - **중복 제거**: 같은 항목을 다시 선택하면 새 행 없이 **최신으로 갱신**(맨 앞으로 이동).
   - **보관 개수 상한**: **15개**(type 무관 합산). 초과 시 **가장 오래된 것부터 제거**.
2. **조회(GET)**: 회원의 최근 검색어를 **최신순**으로 반환(상한 이내). 각 항목에 `type`·`keyword`·`placeId`(PLACE만).
3. **삭제(DELETE)**: **개별 삭제**(id 지정)와 **전체 삭제**.

### 비기능 요구사항

- 전부 **JWT 필수**(비로그인 사용 불가).
- 검색 API(`GET /places/search`)의 **자동 저장 로직은 제거**한다(더 이상 검색이 최근 검색어를 기록하지 않음).

## 도메인 모델 (마이그레이션)

**member_recent_search** (회원 ↔ 선택 항목, 1:N)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| member_id | bigint FK→member | |
| type | varchar(enum) | `REGION` \| `PLACE` |
| keyword | varchar(100) | 표시명(지역: "서울특별시 강남구", 장소: "강남 운전연습 코스") |
| place_id | bigint, null | PLACE만 채움(상세 직행용). 스냅샷이라 place FK는 걸지 않음(장소 삭제돼도 항목 유지, 탭 시 404는 클라 처리) |
| searched_at | timestamptz | 마지막 선택 시각(중복 갱신 시 갱신) |

- **중복 판정(부분 유니크 인덱스)** — 지역은 이름으로, 장소는 placeId로 유일하게 한다:
  - `unique(member_id, keyword) where type='REGION'`
  - `unique(member_id, place_id) where type='PLACE'`
  - 이래야 **이름이 같은 서로 다른 장소**(placeId 다름)가 별개로 저장된다. 재선택은 각 인덱스 대상 `ON CONFLICT (...) WHERE type=... DO UPDATE SET searched_at = clock_timestamp()`로 맨 앞 갱신. `now()`는 트랜잭션 고정값이라 벽시계 `clock_timestamp()` 사용.
- 조회 정렬: `searched_at DESC, id DESC`.
- 상한 유지: 등록 후 상한(15) 초과분을 오래된 순으로 삭제.
- `member` 하드삭제 대비 FK는 `ON DELETE CASCADE`.
- **마이그레이션 V11**: V10(1차 구현)은 **이미 배포되어 수정 불가**(Flyway 체크섬 검증) → **V11로 ALTER**한다. `type`·`place_id` 컬럼 추가, 기존 `unique(member_id, keyword)` 제거 후 위 부분 유니크 인덱스 2개 생성.

## API 명세 (패키지 `domain.member`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/members/me/recent-searches | 선택 항목 등록(연관검색어에서) | JWT |
| GET | /api/v1/members/me/recent-searches | 최근 검색어 목록(최신순) | JWT |
| DELETE | /api/v1/members/me/recent-searches/{id} | 개별 삭제 | JWT |
| DELETE | /api/v1/members/me/recent-searches | 전체 삭제 | JWT |

### 등록 (POST)

```json
// 지역 선택
{ "type": "REGION", "keyword": "서울특별시 강남구" }

// 장소 선택
{ "type": "PLACE", "keyword": "강남 운전연습 코스", "placeId": 101 }
```
- `type`·`keyword` 필수. `keyword` 트림 후 1~100자. **PLACE면 `placeId` 필수**, REGION이면 무시(null 저장).
- 응답 데이터 없음(200). 무효 type·필수 누락은 400.

### 조회 (GET)

```json
// Response data
[
  { "id": 12, "type": "PLACE",  "keyword": "강남 운전연습 코스", "placeId": 101 },
  { "id": 9,  "type": "REGION", "keyword": "서울특별시 강남구", "placeId": null }
]
```
- 최신순. 상한 이내. 없으면 빈 배열.

### 개별 삭제 / 전체 삭제

```
DELETE /api/v1/members/me/recent-searches/{id}
DELETE /api/v1/members/me/recent-searches
```
- 응답 데이터 없음(200). 개별은 본인 것만 지우고 없는/타인 id는 멱등 200. 전체는 없어도 멱등 200.

## 완료 조건 (Acceptance Criteria)

- [ ] 검색 API의 자동 저장 로직이 제거된다(검색해도 최근 검색어가 안 생김).
- [ ] POST로 지역/장소를 등록하면 저장되고, 같은 항목 재등록은 중복 없이 최신 갱신(맨 앞)된다.
- [ ] PLACE 등록 시 placeId가 저장되고 조회 응답에 포함된다. REGION은 placeId=null.
- [ ] 상한(15) 초과 시 가장 오래된 항목이 제거된다.
- [ ] 조회는 최신순으로 type·keyword·placeId를 반환한다.
- [ ] 개별 삭제는 본인 것만, 전체 삭제는 회원 전체(둘 다 멱등).
- [ ] type 누락·무효, PLACE인데 placeId 누락은 400.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

- 개별 삭제 시 없는/타인 id 응답 — 멱등 200(기본).

## 범위 밖 / 다음

- 인기 검색어, 결과 없는 항목 등록 정책.
