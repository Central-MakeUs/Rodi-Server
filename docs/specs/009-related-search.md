# 연관 검색어 (지역·장소명 자동완성)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-28 | Draft | 최초 작성 |
| 2026-07-28 | Draft | 결정 반영 — 지역 정렬은 **대표좌표 미확보로 관련도순**(거리순은 추후), 장소는 **이름 관련도순**, 주차장 포함, JWT 필수 |
| 2026-07-28 | Draft | 지역 데이터 확정 — **별칭 CSV로 매칭**·시군구 CSV로 정규 출력, **시도+시군구 2단계만**(일반구·시도·미시행 제외), place.address를 display_name 표기로 정규화(선행 작업) |

## 배경 / 목적

검색창에 키워드를 입력하는 동안 **지역명**과 **장소명** 후보를 실시간으로 보여줘, 사용자가 원하는 대상을 빠르게 고르게 한다. 선택한 항목은 최근 검색어(스펙 008)로 등록되고, 지역이면 그 지역 검색으로, 장소면 그 장소 상세로 이어진다. 응답은 **지역(regions)**과 **장소(places)**를 구분해 내려준다.

## 요구사항

### 기능 요구사항

1. **지역 연관검색(regions)**: 키워드를 포함하는 시군구를 **관련도순(키워드가 앞쪽일수록 상위) 최대 4개** 반환. 장소 유무와 무관하게 노출(코스가 없는 "강원특별자치도 춘천시"도 뜸). *(대표좌표 확보 후 "현위치 거리순"으로 전환 — 미해결 질문)*
2. **장소 연관검색(places)**: `place.name`에 키워드를 포함하는 장소(코스+주차장)를 **이름 관련도순**(키워드 매칭 위치 앞쪽 우선)으로 **커서 페이지네이션(20개씩)** 반환. 각 항목에 `placeId`·`name`·`region`(= 장소 주소).
3. 지역/장소를 **구분해서** 응답한다.

### 비기능 요구사항

- **JWT 필수**(검색 흐름이 로그인 전용, 스펙 007).
- `lat`/`lng`(현위치)는 **선택** — 현재 정렬은 관련도순이라 미사용. **지역 대표좌표 확보 후** 지역 거리정렬에 쓸 예정이라 파라미터는 미리 열어둔다.

## 도메인 · 데이터

### 지역 데이터 (리소스 CSV, 인메모리)

두 CSV를 기동 시 메모리 로드(닉네임 풀 패턴). 별칭은 매칭용, 시군구는 정규 출력용:

- **정규 목록(출력)**: 시군구 CSV에서 **시도+시군구 2단계만**(예: "경기도 남양주시", "서울특별시 강남구") 로드. **일반구**(수원시 장안구 등 `parent_city_code`가 있는 행)·**시도 단독**은 제외 → 순수 시군구 269개. **미시행/신설**(effective_date 미래, `전남광주통합특별시` 등 통합·신설)은 **CSV 생성 단계에서 제외**. 표준 표기 = `display_name`.
- **별칭(매칭)**: `administrative_region_aliases.csv`(`region_code → normalized_alias`, priority)로 짧은 이름("강남"←강남구)·구 시도명("강원도 춘천")·띄어쓰기 변형("강원 강릉")을 매칭. 정규 목록에 없는 region_code(시도 등)를 가리키는 별칭은 드롭.
- **매칭·정렬**: 키워드 정규화(공백 제거) → `normalized_alias` **접두 일치 우선(그다음 부분 일치)** → region_code로 정규 지역 매핑 → **region_code로 dedup**(예 "강서"→서울/부산 강서구 둘 다 후보) → 관련도(접두/위치)+priority 순 → **상위 4개**. 응답은 `display_name`.
- **대표 좌표(office_lat/lng)는 심사 대기로 빈 값** → 현재 관련도순, 좌표 확보 후 현위치 거리순으로 전환.
- **place.address 정합성**: 지역 선택 후 `address ILIKE display_name`로 검색되므로, **장소의 address를 CSV `display_name` 표기로 통일**해야 한다("서울시 강남구" → "서울특별시 강남구"). place 데이터는 레포 시드가 아니라 DB 직접 입력이라, 일회성 정규화 UPDATE + 이후 입력 규칙으로 맞춘다.

### 장소 (기존 place) — 관련도 쿼리

- `place`(코스+주차장)에서 `name ILIKE '%kw%'` 매칭. **관련도 = 키워드가 이름에서 처음 나오는 위치**로, 앞에서 매칭될수록 상위:
  ```sql
  SELECT ..., POSITION(LOWER(:kw) IN LOWER(p.name)) AS match_pos
  FROM place p
  WHERE p.name ILIKE :pattern ESCAPE '\'
  ORDER BY match_pos ASC, p.id ASC
  LIMIT :size
  ```
  예: "강남" → `강남 코스`(pos=1)가 `한강남단 코스`(pos=2)보다 위. 동률은 id.
- 커서 keyset은 `(match_pos, id)`(기존 커서 패턴, `CursorCodec` 재사용). 응답 `region`은 `place.address`.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | /api/v1/places/related-search | 지역·장소명 연관검색(자동완성) | JWT |

```http
GET /api/v1/places/related-search?keyword=강&size=20&cursor=
Authorization: Bearer <JWT>
```

- `keyword` **필수**: 트림 후 1~50자, `%`·`_`·`\` 이스케이프.
- `lat`/`lng` **선택**: 현재 미사용(추후 지역 거리정렬용).
- `size`(장소 페이지 크기, 기본 20)·`cursor`(장소 다음 페이지).

```json
// '강' 입력 시 응답 data
{
  "regions": [
    { "name": "서울특별시 강남구" },
    { "name": "서울특별시 강동구" },
    { "name": "강원특별자치도 춘천시" },
    { "name": "강원특별자치도 강릉시" }
  ],
  "places": {
    "items": [
      { "placeId": 101, "name": "강남 운전연습 코스", "region": "서울특별시 강남구" }
    ],
    "hasNext": true,
    "nextCursor": "eyJkIjoxLCJpZCI6MTAxfQ==",
    "totalCount": 42
  }
}
```

- **regions**: 최대 4개, 관련도순. **첫 페이지(cursor 없음)에서만** 채우고 이후 페이지는 빈 배열(지역은 페이지네이션 없음).
- **places**: 공통 `CursorPage`(items·hasNext·nextCursor·totalCount 첫 페이지만). 관련도(match_pos) 커서.
- 결과 없으면 빈 배열/빈 페이지(200). keyword 누락·공백·size 범위 밖 400.

## 완료 조건 (Acceptance Criteria)

- [ ] `keyword=강`이면 시군구명에 "강"이 포함된 지역이 관련도순 **최대 4개** 반환된다(장소 없는 지역 포함).
- [ ] 장소는 `name`에 키워드가 포함된 place(코스+주차장)가 **관련도순(매칭 위치)**으로 **20개 커서 페이지네이션**되고 각 항목에 placeId·name·region이 있다.
- [ ] regions는 첫 페이지에만, places는 이후 페이지로 이어진다(2페이지 연속성).
- [ ] keyword 누락/공백/50자 초과, size 범위 밖은 400.
- [ ] 지역명이 `place.address` 표기와 일치해, 지역 선택 후 그 문자열로 검색(스펙 007)이 동작한다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문 / 데이터 작업

- **place.address 정규화(선행 작업)** — 현 DB의 `SELECT DISTINCT address FROM place`를 CSV `display_name` 표기로 매핑하는 일회성 UPDATE. 이게 돼야 지역 선택→검색이 연결된다.
- **지역 대표좌표 추가(추후)** — 심사 후 좌표를 CSV에 넣어 지역 정렬을 관련도 → 현위치 거리순으로 전환. `lat`/`lng` 파라미터가 그때 사용된다.
- **CSV 최종 가공** — 시도+시군구 269개 필터(일반구·시도 제외), 미시행 행정구역(전남광주통합특별시 등) 수동 제외.
- **컨트롤러 위치** — 단발 GET이라 소유 컨트롤러(PlaceController)에 둠(CLAUDE.md 분리 기준, 기본).

## 범위 밖 / 다음

- 오타 보정·유사어, 인기/추천 검색어, 지역 거리정렬(좌표 확보 후), 검색 히스토리 기반 개인화.
