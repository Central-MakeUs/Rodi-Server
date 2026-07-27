# 코스 검색 · 홈 정렬 필터 (+ 마커 클러스터링 보류)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-23 | Draft | 최초 작성 |
| 2026-07-23 | Draft | 리뷰 반영 — filter_tags 서버 저장(A안) 잠정 확정, Navigator는 주차 선택 무관 전체 노출, name 검색 확장 경로 명시 |
| 2026-07-23 | Draft | 비로그인 필터 지원 확정 → 저장 방식 재검토, `filterTags` 파라미터 도입 |
| 2026-07-23 | Draft | **B안(클라 로컬 저장) 최종 확정** — 서버 저장(V8·backfill·PUT·옵셔널 JWT·appliedFilterTags) 전부 제거 |
| 2026-07-23 | **Approved** | 승인 — 검색부터 구현 시작. **B안은 클라 팀 합의 전제**(기본값 계산·로컬 저장이 클라 몫 — 합의 불발 시 A안 재검토). 클러스터링 방식은 계속 대기 |
| 2026-07-23 | In Progress | **검색 엔드포인트 구현**(키워드 검증·ILIKE 이스케이프·거리순 커서·totalCount·공개) |
| 2026-07-23 | In Progress | **FilterCategory enum 제거** — 카테고리는 클라 전용 UX 개념으로 확정. 정렬 파라미터는 `filterTags` 대신 기존 `PracticeType` 리스트(`practiceTypes`)로 단순화(혼합 선택=합집합) |
| 2026-07-26 | In Progress | 회의 반영 — **장소명(name) 검색 추가**(주소 OR 장소명 부분 일치, 구현·테스트 완료). **필터는 로그인 전용**으로 변경(비로그인은 bbox 거리순만) |
| 2026-07-26 | In Progress | 필터 저장 **A안(서버) 확정** — `member.filter_tags`에 `List<PracticeType>` 저장(V9), 카테고리는 클라 전용 유지, **backfill 없음**, 적용은 인증 시에만. Unit 2에서 구현 |
| 2026-07-26 | In Progress | **Unit 2 필터 정렬 구현** — V9 컬럼·`PUT /members/me/filter-tags`·`@CurrentMember(required=false)` 옵셔널 JWT·목록/검색 매칭 우선 정렬·커서 확장·전역 400 핸들러(무효 본문). 필터는 저장값에서만(쿼리 파라미터 없음). 남은 건 클러스터링뿐 |
| 2026-07-26 | In Progress | 회의 반영 — **검색을 로그인 전용으로 변경**(GET /places/search JWT 필수, 비로그인 401). 뷰포트 목록은 옵셔널 JWT 유지 |

## 배경 / 목적

홈에서 원하는 지역의 코스를 찾으려면 지도를 직접 움직이는 방법뿐이다. **시군구 주소 검색**으로 지역을 바로 찾게 하고, 온보딩에서 배정된 **레벨에 맞는 코스가 자동으로 앞에 노출**되도록 홈 정렬 필터를 도입한다.

**필터는 서버 저장(로그인 전용)이다**: 회원의 선택 연습유형을 `member.filter_tags`(jsonb, V9)에 저장하고 `PUT /members/me/filter-tags`로 갱신한다. 목록·검색은 **인증된 요청일 때만** 저장 필터로 매칭 우선 정렬하고, 비로그인은 거리순만(검색은 로그인 필수). **카테고리(기초 주행 등)는 클라 전용 UX 개념** — 클라가 카테고리→연습유형 매핑·온보딩 기본값 계산을 하고 풀린 `PracticeType` 리스트를 PUT으로 보낸다. 서버는 카테고리를 모르며 연습유형만 저장·매칭한다(서버 카테고리 enum 없음).

**범위 밖**: 장소명(`name`) 검색(확장 경로만 확보), 리뷰, 코스 생성. **마커 클러스터링은 방식 미정(보류)** — 결정 후 본 스펙을 갱신해 이번 작업 단위 안에서 진행한다.

## 요구사항

### 기능 요구사항

1. **코스 검색**: 키워드로 전국의 place(코스+주차장)를 검색한다. `place.address`(시군구, 예: "강남") **또는** `place.name`(장소명, 예: "한강") 부분 일치. 결과는 현위치 거리순.
2. **홈 정렬 필터**
   - **정렬 적용**: 뷰포트 목록(`GET /places`)과 검색(`GET /places/search`)에서, 인증 회원의 저장 `filter_tags`에 해당하는 연습유형이 달린 place 우선, 동점은 현위치 거리순. **매칭 안 된 place도 후순위로 노출(숨김 아님)**. 필터가 없으면(비로그인·빈 필터) 기존과 동일한 거리순.
   - **저장·갱신**: `PUT /members/me/filter-tags`로 전체 교체(빈 배열=해제). 카테고리 UI·온보딩 기본값 계산(아래 참고 표)·카테고리→연습유형 변환은 클라가 하고, 서버는 풀린 연습유형만 저장·매칭.
3. **마커 클러스터링**: 방식 미정(서버 그리드 vs 기타) — 결정 대기, 후순위.

### 비기능 요구사항

- 좌표·거리는 **SRID 4326(WGS84)**, 기존 PostGIS 쿼리 방식 유지(ADR 0010).
- 목록·검색은 커서 페이지네이션(keyset) — 정렬 키 확장에 맞춰 커서도 확장.
- **인증**: 검색은 JWT 필수(비로그인 401), 뷰포트 목록은 옵셔널 JWT(비로그인 거리순), `PUT /members/me/filter-tags`는 JWT 필수.

## 도메인 모델

**V9: `member.filter_tags`(jsonb, `List<PracticeType>`) 추가.** 매칭은 기존 `PracticeType`(13종)과 `course_practice_type` 태그를 쓰고, 새 enum은 없다.

### 카테고리 ↔ 연습유형 매핑 (참고 — 클라이언트 소유)

카테고리는 클라 UI에만 존재한다. 클라가 선택된 카테고리를 아래 매핑으로 풀어 `practiceTypes`로 전송한다(복수 카테고리 선택 = 합집합).

| 카테고리(표시명) | 연습유형 |
|------|----------|
| 기초 주행 | STRAIGHT · LEFT_RIGHT_TURN · LANE_CHANGE |
| 도심 기본 | U_TURN · INTERSECTION · PARKING |
| 도로흐름 | HIGHWAY_ENTRY · MERGING · MULTILANE |
| 복합 상황 | UNPROTECTED_LEFT_TURN · ROUNDABOUT · NARROW_ROAD · CORNERING |
| 넓은 공간 | PARKING |

- `PARKING`이 도심 기본·넓은 공간 양쪽에 속하지만(겹침), 전송 값이 합집합이라 서버는 영향 없다 — 칩 선택 상태는 클라 로컬이 진실이므로 복원 모호성도 없다.

### 필터 기본값 규칙 (참고 — 클라이언트 계산)

온보딩 완료 시 **클라가 계산**해 `PUT /members/me/filter-tags`로 서버에 저장한다(서버는 계산에 관여하지 않음, 온보딩 API 변경 없음).

| 케이스 | 기본 선택 카테고리 |
|--------|--------------------|
| SEED | 기초 주행 |
| ROOKIE | 도심 기본 |
| OWNER | 도로흐름 |
| EXPLORER | 복합 상황 |
| NAVIGATOR | 선택 없음 — **주차 선택 여부 무관 전체 노출** |
| + ON-ADD-01에서 "주차" 선택 | 넓은 공간 추가 (NAVIGATOR 제외) |
| ON-ADD-01 건너뜀 | 레벨 카테고리만 |

- 서버 저장이라 재설치·기기 변경에도 필터가 유지된다. 기존 회원은 backfill 없이, 다음 홈 진입 시 클라가 PUT으로 세팅(그전엔 거리순).

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | /api/v1/places/search | 주소·장소명 검색(전국·필터 우선·거리순·커서) | **JWT 필수** |
| GET | /api/v1/places | 뷰포트 목록 — 정렬 필터 확장 | 옵셔널 JWT |
| PUT | /api/v1/members/me/filter-tags | 홈 정렬 필터(연습유형 리스트) 저장 | JWT |

> **인증 표기**: **검색은 로그인 전용**(비로그인 401, 2026-07-26 회의). 뷰포트 목록은 토큰 없이도 동작(거리순, 비로그인 둘러보기)하되 필터 정렬은 인증된 요청에서만 적용된다.

### 1. 코스 검색 (신규, **JWT 필수**)

**구현 완료 (장소명 검색·필터 정렬). 비로그인은 401.**

```
GET /api/v1/places/search?keyword=강남&lat=37.50&lng=127.03&size=20&cursor=
Authorization: Bearer <JWT>
```

- `keyword` **필수**: 트림 후 1~50자. `place.address`(시군구) **또는** `place.name`(장소명) **부분 일치**(`ILIKE '%kw%'`, `%`·`_`·`\`는 이스케이프). 해당 컬럼이 null인 place는 그 컬럼으론 매칭 안 됨. bbox 없음(전국 대상).
- `lat`/`lng` **필수**: 현위치(거리 정렬 기준). `size` 기본 20(1~100), `cursor` 다음 페이지 토큰. 검증 규칙은 기존 목록과 동일(위경도 범위, size 범위 밖 400).
- **정렬 필터**: 회원의 저장 `filter_tags`가 있으면 매칭 우선→거리순, 없으면 거리순만(쿼리 파라미터 없음). 검색은 로그인 전용이라 항상 회원 컨텍스트가 있다.

```json
// Response data — 공통 CursorPage<PlaceListItem> 그대로 (필드 추가 없음)
{
  "items": [
    { "id": 1, "type": "COURSE", "name": "강남 직선 코스", "address": "서울특별시 강남구",
      "lat": 37.51, "lng": 127.03, "distanceFromMe": 320,
      "practiceTypes": ["STRAIGHT","LANE_CHANGE"],
      "description": "…", "distanceMeters": 2100, "capacity": null, "openTime": null }
  ],
  "hasNext": false,
  "nextCursor": null,
  "totalCount": 1
}
```

- `totalCount`는 기존 규칙 재사용 — 첫 페이지(cursor 없음)에서만, 이후 null.
- 결과 없으면 빈 `items`(200). 키워드 누락·공백만·범위 밖 400.

### 2. 뷰포트 목록 (기존 확장)

- 기존 파라미터·응답 구조 변경 없음(쿼리 파라미터 추가 없음). 변경점 두 가지:
  1. **옵셔널 JWT 전환** — 토큰 있으면 회원 식별 후 저장된 `filter_tags`로 정렬, 없으면 기존 거리순.
  2. **정렬**: 필터 있으면 `매칭 DESC → 거리 ASC → id ASC`, 없으면(비로그인·빈 필터) 기존과 동일한 거리순 — 하위 호환.

## 정렬 · 쿼리 접근

- **필터 매칭 판정**(place 단위): 회원의 저장 `filter_tags`(연습유형 집합)와
  - 코스: `course_practice_type` 태그의 교집합 존재 시 매칭(`EXISTS ... practice_type IN (:practiceTypes)`).
  - 주차장: 연습유형이 `["PARKING"]` 고정이므로 필터에 `PARKING` 포함 시 매칭(`:parkingFlag`).
  - native query에서 `CASE WHEN place_type='PARKING' THEN :parkingFlag WHEN EXISTS(...) THEN 1 ELSE 0 END AS matched`.
- **커서 keyset 확장**: 필터 적용 시 정렬 `(matched DESC, distance ASC, id ASC)`, 커서 토큰은 `CursorCodec`의 sortValue에 `"matched|distance"` 합성(코덱 변경 없이 재사용). 필터 미적용(비로그인·빈 필터)이면 기존 거리순 쿼리·`(distance, id)` 커서를 그대로 써 하위 호환.
- 필터는 **저장된 값에서만** 온다(쿼리 파라미터 없음) — 인증된 요청이면 `member.filter_tags`, 비로그인이면 빈 필터. `@CurrentMember(required=false)`로 옵셔널 주입.
- 검색 매칭: `address ILIKE :pattern OR name ILIKE :pattern` — 데이터가 수백~수천 건 규모라 인덱스 없이 시작(느려지면 trigram 인덱스 검토).
- **무효 요청 본문 400**: PUT의 무효 enum 등 `HttpMessageNotReadableException`을 전역 핸들러가 400으로 매핑(기존엔 500으로 새던 갭 보강).

## 완료 조건 (Acceptance Criteria)

- [x] 검색: `keyword=강남`이면 address 또는 name에 "강남"이 포함된 place(코스+주차장)만 전국에서 반환한다. 부분 일치·결과 없으면 빈 목록.
- [x] 검색: 주소에 없고 장소명(name)에만 있는 키워드도 매칭된다(주소 없는 place는 name으로만 매칭). 주소·장소명 혼합 매칭 결과는 합쳐서 반환.
- [x] 검색: 결과가 (필터 매칭 우선, 동점 시 현위치 거리순, id순)으로 정렬되고, `size`로 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성). `totalCount`는 첫 페이지만.
- [x] 검색: keyword 누락/공백/50자 초과, 위경도 범위 밖, size 범위 밖은 400.
- [x] 필터 적용 시(목록·검색 공통, 인증 회원): 매칭 place가 먼저, 비매칭도 후순위로 전부 노출된다(개수 불변 — 숨김 없음). 필터 없으면(비로그인·빈 필터) 기존과 동일한 거리순(하위 호환).
- [x] 주차장은 `filter_tags`에 PARKING 포함 시 매칭으로 취급된다.
- [x] 필터+커서: 2페이지 연속성이 (matched, distance, id) 기준으로 유지된다(매칭 경계에서 누락·중복 없음).
- [x] 필터 저장(PUT): 연습유형 리스트 전체 교체, 빈 배열은 해제(200), 무효 enum·filterTags 누락은 400.
- [x] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

- **마커 클러스터링 방식**(서버 그리드 vs 기타) — 사용자 결정 대기. 확정 시 본 스펙 갱신 후 진행.

## 확정된 결정 (리뷰 반영)

### 검색 (구현 완료)

- 검색은 별도 엔드포인트(`GET /places/search`), 대상은 코스+주차장 전부, keyword 1~50자, `lat`/`lng` 필수.
- 매칭은 `address`(시군구) **또는** `name`(장소명) 부분 일치(`ILIKE`, 와일드카드 이스케이프). **이름/주소 관련도 구분 없이 거리순**(추후 관련도 정렬 도입 시 커서에 nameMatch 키 추가로 확장 — 커서 손대는 김이면 소공수, 단독이면 커서 하위호환 비용).

### 필터 (Unit 2 — A안 서버 저장, 구현 완료)

- **필터 상태는 서버 저장(A안)** — 2026-07-26 회의에서 "비로그인은 필터 불가, bbox 거리순만"으로 바뀌며 필터가 로그인 전용 개념이 됨. 클라 소유(B안)의 근거(비로그인도 파라미터로 필터)가 사라져 A안으로 회귀.
- **서버는 `member.filter_tags`에 `List<PracticeType>`(이미 풀린 연습유형) jsonb 저장**(V9 컬럼 신규). 카테고리는 여전히 **클라 전용 UX 개념** — 클라가 카테고리→연습유형 매핑·기본값 계산을 하고 풀린 연습유형을 `PUT /members/me/filter-tags`로 전송. 서버는 카테고리를 모른다.
- **backfill 없음** — 기존 온보딩 회원은 다음 홈 진입 시 클라가 PUT으로 세팅, 그전엔 거리순(무해). → V9에 카테고리 매핑 SQL이 안 들어감.
- **적용은 인증 시에만**: 목록·검색 모두 옵셔널 JWT — 인증된 요청이면 저장된(또는 파라미터로 넘어온) 연습유형으로 (matched, 거리, id) 정렬, 비로그인은 거리순만(파라미터 보내도 무시 → 로그인 전용 강제).
- Navigator는 주차 선택 여부 무관 전체 노출.

## 범위 밖 / 다음

- 검색어 자동완성·최근 검색어, 이름/주소 관련도 정렬, 클라 필터 UI 사양, region 그룹핑, 리뷰.
