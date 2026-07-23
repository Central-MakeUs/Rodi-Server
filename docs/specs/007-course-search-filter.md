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

## 배경 / 목적

홈에서 원하는 지역의 코스를 찾으려면 지도를 직접 움직이는 방법뿐이다. **시군구 주소 검색**으로 지역을 바로 찾게 하고, 온보딩에서 배정된 **레벨에 맞는 코스가 자동으로 앞에 노출**되도록 홈 정렬 필터를 도입한다.

**필터 상태는 클라이언트 소유다**(레벨 계산·추천 태그와 동일 철학): 카테고리(기초 주행 등)는 사용자가 이해하기 쉽게 연습유형을 묶어 보여주는 **클라 전용 UX 개념**이고, 온보딩 기본값 계산·로컬 저장·변경 유지 모두 클라가 한다. 클라는 선택된 카테고리를 **연습유형(PracticeType) 리스트로 풀어서** 매 요청에 전달하고, 서버는 그 값으로 **정렬만** 한다. 로그인 여부와 무관하게 단일 경로로 동작하며, 서버에 새 enum·저장이 없다.

**범위 밖**: 장소명(`name`) 검색(확장 경로만 확보), 리뷰, 코스 생성. **마커 클러스터링은 방식 미정(보류)** — 결정 후 본 스펙을 갱신해 이번 작업 단위 안에서 진행한다.

## 요구사항

### 기능 요구사항

1. **코스 검색**: 키워드(시군구 주소 일부, 예: "강남")로 전국의 place(코스+주차장)를 검색한다. `place.address` 부분 일치. 결과는 필터 매칭 우선 → 현위치 거리순.
2. **홈 정렬 필터**
   - **정렬 적용**: 뷰포트 목록(`GET /places`)과 검색(`GET /places/search`) 모두 `practiceTypes` 파라미터(연습유형 리스트)를 받아 — 해당 연습유형이 달린 place 우선, 동점은 현위치 거리순. **매칭 안 된 place도 후순위로 노출(숨김 아님)**. 파라미터 없거나 빈 값이면 기존과 동일한 거리순.
   - **상태 관리는 클라 소유**: 카테고리 UI·온보딩 기본값 계산(아래 참고 표)·로컬 저장·카테고리→연습유형 변환 모두 클라. 서버 저장 없음 — 로그인·비로그인 동일 동작.
3. **마커 클러스터링**: 방식 미정(서버 그리드 vs 기타) — 결정 대기, 후순위.

### 비기능 요구사항

- 좌표·거리는 **SRID 4326(WGS84)**, 기존 PostGIS 쿼리 방식 유지(ADR 0010).
- 목록·검색은 커서 페이지네이션(keyset) — 정렬 키 확장에 맞춰 커서도 확장.
- 목록·검색 모두 **공개 API 유지**(인증 변경 없음).

## 도메인 모델

**DB·enum 변경 없음.** 매칭은 기존 `PracticeType`(13종)과 `course_practice_type` 태그를 그대로 쓴다.

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

### 필터 기본값 규칙 (참고 — 클라이언트 소유)

온보딩 완료 시 클라가 계산해 로컬에 저장한다. **서버는 관여하지 않는다**(온보딩 API 변경 없음).

| 케이스 | 기본 선택 카테고리 |
|--------|--------------------|
| SEED | 기초 주행 |
| ROOKIE | 도심 기본 |
| OWNER | 도로흐름 |
| EXPLORER | 복합 상황 |
| NAVIGATOR | 선택 없음 — **주차 선택 여부 무관 전체 노출** |
| + ON-ADD-01에서 "주차" 선택 | 넓은 공간 추가 (NAVIGATOR 제외) |
| ON-ADD-01 건너뜀 | 레벨 카테고리만 |

- 재설치·기기 변경 시 로컬 필터는 초기화된다(클라가 마이페이지 레벨로 재계산 가능 — 한계 포함 확정된 트레이드오프).

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | /api/v1/places/search | 시군구 키워드 검색(전국·필터 우선·거리순·커서) | 공개 |
| GET | /api/v1/places | 뷰포트 목록 — `practiceTypes` 파라미터·정렬 확장 | 공개 |

### 1. 코스 검색 (신규)

```
GET /api/v1/places/search?keyword=강남&lat=37.50&lng=127.03&size=20&cursor=&practiceTypes=U_TURN,INTERSECTION,PARKING
```

- `keyword` **필수**: 트림 후 1~50자. `place.address` **부분 일치**(`ILIKE '%kw%'`, `%`·`_`는 이스케이프). bbox 없음(전국 대상).
- **name 검색 확장 경로(추후)**: 매칭 조건에 `OR name ILIKE '%kw%'` 추가만으로 확장 — 파라미터·응답·커서 불변이라 클라 변경 없음.
- `lat`/`lng` **필수**: 현위치(거리 정렬 기준). `size` 기본 20(1~100), `cursor` 다음 페이지 토큰. 검증 규칙은 기존 목록과 동일(위경도 범위, size 범위 밖 400).
- `practiceTypes` **선택**: `PracticeType` 목록. **콤마 구분(`practiceTypes=U_TURN,PARKING`)이 표준 표기**이며, Spring 기본 바인딩이라 파라미터 반복도 동작한다. 없거나 빈 값이면 거리순만. 무효 enum 값은 바인딩 실패로 400.

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

- 기존 파라미터·응답 구조 변경 없음. 변경점 두 가지:
  1. **`practiceTypes` 선택 파라미터 추가** (검색과 동일 규칙).
  2. **정렬**: `필터 매칭 DESC → 거리 ASC → id ASC` (practiceTypes 없거나 빈 값이면 기존과 동일한 거리순 — 하위 호환).

## 정렬 · 쿼리 접근

- **필터 매칭 판정**(place 단위): 받은 `practiceTypes`와
  - 코스: `course_practice_type` 태그의 교집합 존재 시 매칭.
  - 주차장: 연습유형이 `["PARKING"]` 고정이므로 리스트에 `PARKING` 포함 시 매칭.
  - native query에서 `CASE WHEN EXISTS(...) OR (place_type='PARKING' AND :parkingIncluded) THEN 1 ELSE 0 END AS matched`.
- **커서 keyset 확장**: 정렬 `(matched DESC, distance ASC, id ASC)` → 커서 토큰 `(matched, distance, id)`. 기존 `CursorCodec` 확장. 필터 미적용 시엔 matched를 상수(0)로 두어 기존 커서 의미와 동일하게 동작.
- 검색 주소 매칭: `address ILIKE :pattern` — 데이터가 수백~수천 건 규모라 인덱스 없이 시작(느려지면 trigram 인덱스 검토).

## 완료 조건 (Acceptance Criteria)

- [x] 검색: `keyword=강남`이면 address에 "강남"이 포함된 place(코스+주차장)만 전국에서 반환한다. 부분 일치·결과 없으면 빈 목록.
- [ ] 검색: 결과가 (필터 매칭 우선, 동점 시 현위치 거리순, id순)으로 정렬되고, `size`로 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성). `totalCount`는 첫 페이지만. *(거리순·커서·totalCount 구현 완료 — 필터 매칭 우선은 다음 단위)*
- [x] 검색: keyword 누락/공백/50자 초과, 위경도 범위 밖, size 범위 밖은 400.
- [ ] `practiceTypes` 적용 시(목록·검색 공통): 매칭 place가 먼저, 비매칭도 후순위로 전부 노출된다(개수 불변 — 숨김 없음). 파라미터 없거나 빈 값이면 기존과 동일한 거리순(하위 호환). 무효 enum은 400.
- [ ] 주차장은 `practiceTypes`에 PARKING 포함 시 매칭으로 취급된다.
- [ ] 필터+커서: 2페이지 연속성이 (matched, distance, id) 기준으로 유지된다(매칭 경계에서 누락·중복 없음).
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

- **마커 클러스터링 방식**(서버 그리드 vs 기타) — 사용자 결정 대기. 확정 시 본 스펙 갱신 후 진행.

## 확정된 결정 (리뷰 반영)

- **필터 상태는 클라이언트 소유(B안)** — 비로그인 필터가 요구사항이 되면서 클라는 어차피 로컬 저장+파라미터 경로가 필수 → 서버 저장(A안)을 얹으면 이중 경로가 되어 제거. 로그인 여부 무관 단일 경로. 재설치 시 필터 초기화는 감수(레벨 기반 재계산으로 부분 복구). **클라 팀 합의 전제.**
- **서버에 카테고리 개념 없음** — FilterCategory enum을 두려 했으나, 카테고리는 사용자 이해를 돕는 클라 UX 그룹핑일 뿐이고 혼합 선택은 합집합이라 서버는 기존 `PracticeType`만 알면 충분. 파라미터도 `practiceTypes`로 명명. (enum 복원 모호성 논거는 서버 저장 전제였음 — B안에선 무의미)
- 서버는 `practiceTypes` 검증(기존 enum)·매칭·정렬만 담당. **DB 변경 없음, 온보딩 API 변경 없음, 인증 변경 없음.**
- Navigator는 주차 선택 여부 무관 전체 노출.
- 검색은 별도 엔드포인트, 대상은 코스+주차장 전부, keyword 1~50자 부분 일치, `lat`/`lng` 필수.
- 장소명(`name`) 검색은 이번 범위 밖 — `OR name ILIKE` 한 줄로 확장 가능한 구조만 확보.

## 범위 밖 / 다음

- 장소명 검색, 검색어 자동완성·최근 검색어, 클라 필터 UI 사양, region 그룹핑, 리뷰, 필터 서버 동기화(기기 간 유지가 필요해지면 재검토).
