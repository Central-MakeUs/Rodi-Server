# 코스 탐색 (place/course/parking 조회·북마크)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-16 | Draft | 최초 작성 |
| 2026-07-17 | In Progress | #1·#2 구현. 공통 `CursorPage`(hasNext·첫 페이지 한정 totalCount) 도입, place에 `address`(시군구) 추가·`description`을 course 전용으로 이동, #2 아이템 필드 확정(practiceTypes·capacity·openTime) |
| 2026-07-17 | In Progress | #3 코스 상세 구현. `cautions`를 문자열 리스트(`course_caution`)로 변경, 북마크 여부 JSON 키를 `isBookmarked`로 통일 |

## 배경 / 목적

위치기반 연습 장소·코스 탐색이 서비스 핵심이나 `place` 도메인이 아직 미구현이다(ERD·ADR 0002 JOINED 상속·0003 region+PostGIS 설계만 존재). 이번에 **지도 마커·현위치 거리순 목록·코스/주차장 상세·북마크**를 위한 조회 4종 + 저장 1종 API와 place 도메인을 신규 구축한다.

**범위 밖**: 장소/코스 생성 API(코스 생성은 추후 로그인 유저 작성→관리자 승인 별도 기능), region 그룹핑, 레벨 기반 필터(2차), 리뷰. 기본 코스·주차장 데이터는 직접 투입하고, 검증용 샘플만 시드로 소량 넣는다.

## 요구사항

### 기능 요구사항
1. **지도 마커**: 전체 place의 간단 좌표 목록(공개).
2. **현위치 목록**: 지도 뷰포트(NE/SW bbox) 안의 place(코스+주차장)를 현위치 거리순으로 커서 페이징(공개). 공통(주소·거리·연습유형) + 코스는 설명·주행거리, 주차장은 주차면수·영업시작.
3. **코스 상세**(JWT): 연습유형·주의사항(리스트)·설명·주행거리·전체 경로(waypoint)·북마크수·북마크 여부.
4. **주차장 상세**(JWT): 주소·영업시간·요금·주차면수 등 공영주차장 정보 + 북마크 여부.
5. **북마크**(JWT): 장소 저장(POST)·해제(DELETE). 코스·주차장 공통.

### 비기능 요구사항
- 공간 데이터는 **SRID 4326(WGS84)**, PostGIS + hibernate-spatial.
- 목록은 **커서 페이지네이션**(offset 아님) — 거리순 keyset.
- #1·#2 공개, #3·#4·#5 JWT 필수.

## 도메인 모델 (마이그레이션 V7 — place 도메인 신규)

JOINED 상속(ADR 0002): `place`(공통) + `parking`/`course`가 `place_id` 공유 PK.

**place** (공통)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| place_type | varchar(enum) | PARKING \| COURSE (구분자) |
| name | varchar | 장소명 |
| address | varchar(100) | 시군구 단위 주소(예: "서울특별시 강남구") |
| location | geometry(Point,4326) | 대표 좌표(코스=시작점, 주차장=위치) |
| created_at·updated_at | timestamptz | |

**course** (place_id PK/FK)
| 필드 | 타입 | 설명 |
|------|------|------|
| place_id | bigint PK,FK | |
| description | text | 코스 설명(주차장엔 없음) |
| distance_meters | int | 주행거리 |
- 1:N `waypoint`, N:M 태그 `course_practice_type`, 1:N 주의사항 `course_caution`.

**course_caution** (코스 주의사항, 1:N) — PK(course_id, seq)
| 필드 | 타입 | 설명 |
|------|------|------|
| course_id | bigint FK | |
| caution | varchar(100) | 주의사항 문구(칩) |
| seq | int | 순서(`@OrderColumn`이라 INTEGER) |

**parking** (place_id PK/FK) — 공영주차장 open DB 컬럼과 1:1 (**개별 컬럼, jsonb 아님**)
| 필드 | 타입 | 설명 |
|------|------|------|
| place_id | bigint PK,FK | |
| road_address·lot_address | varchar | 도로명·지번 주소(주차장에만) |
| management_no | varchar | 관리번호 |
| parking_type | varchar | 노외/노상/부설 등 |
| capacity | int | 주차면수 |
| is_free | boolean | 무료 여부 |
| has_accessible_space | boolean | 장애인 구역 |
| phone·operator·note | varchar/text | 전화·운영기관·비고 |
| payment_methods | varchar | 결제수단(예: "카드") |
| base_minutes·base_fee | int | 기본 시간·요금 |
| add_unit_minutes·add_unit_fee | int | 추가 단위·요금 |
| day_ticket_hours·day_ticket_fee | int | 일일권 |
| monthly_fee | int | 월정기 |
| weekday_hours·saturday_hours·holiday_hours | varchar | 영업시간(예: "00:00-23:59") |
- 응답에서만 `feeInfo{...}`·`operatingHours{weekday,saturday,holiday}`로 묶어 반환(저장은 flat).

**waypoint** (코스 경로, 1:N)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| course_id | bigint FK | |
| waypoint_type | varchar(enum) | START \| VIA \| DESTINATION |
| sequence | smallint | 경로 순서. unique(course_id, sequence) |
| location | geometry(Point,4326) | 좌표 |
| name | varchar, null | 지점명 |

**course_practice_type** (코스 태그, N:M) — PK(course_id, practice_type)
| 필드 | 타입 | 설명 |
|------|------|------|
| course_id | bigint FK | |
| practice_type | varchar(enum) | `PracticeType`(온보딩과 동일 13종) 재사용 |

**bookmark** (회원 ↔ place, 코스·주차장 공통) — unique(member_id, place_id)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| member_id·place_id | bigint FK | |
| created_at | timestamptz | |

### Enum
| Enum | 값 |
|------|-----|
| `place.place_type` | PARKING / COURSE |
| `waypoint.waypoint_type` | START / VIA / DESTINATION |
| `course_practice_type.practice_type` | `PracticeType` 재사용(U_TURN … STRAIGHT, 13종) |

### 제약·인덱스
- `place.location` **GiST 공간 인덱스**(bbox·거리 쿼리).
- `waypoint` unique `(course_id, sequence)` · `bookmark` unique `(member_id, place_id)`.
- `region_id`·rating·difficulty·bookmark_count **컬럼 없음**(북마크수는 COUNT 계산).

## API 명세 (패키지 `domain.place`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | /api/v1/places/coordinates | 전체 좌표(마커) | 공개 |
| GET | /api/v1/places | 뷰포트 내 목록(거리순·커서) | 공개 |
| GET | /api/v1/places/courses/{placeId} | 코스 상세 | JWT |
| GET | /api/v1/places/parkings/{placeId} | 주차장 상세 | JWT |
| POST·DELETE | /api/v1/places/{placeId}/bookmark | 북마크 저장·해제 | JWT |

### 1. 좌표 목록 (마커)
```json
// GET /api/v1/places/coordinates
// Response data
[ { "id": 1, "type": "COURSE", "name": "한강 코스", "address": "서울특별시 영등포구", "lat": 37.51, "lng": 127.03 } ]
```
전체 place 반환(필터 없음). 지도 클러스터링은 클라이언트가 이 완전한 마커 집합으로 수행.

### 2. 현위치 목록 (뷰포트 bbox + 거리순 + 커서)
```
GET /api/v1/places?swLat=&swLng=&neLat=&neLng=&lat=&lng=&size=20&cursor=
```
- `sw*`/`ne*`: 화면 남서/북동 모서리(bbox). `lat`/`lng`: 현위치(거리 정렬 기준). `size` 기본 20. `cursor` 다음 페이지 토큰.
```json
// Response data — 공통 CursorPage<PlaceListItem>
{
  "items": [
    { "id": 1, "type": "COURSE", "name": "한강 코스", "address": "서울특별시 영등포구",
      "lat": 37.51, "lng": 127.03, "distanceFromMe": 320,
      "practiceTypes": ["STRAIGHT","LANE_CHANGE"],
      "description": "…", "distanceMeters": 2100,
      "capacity": null, "openTime": null },
    { "id": 7, "type": "PARKING", "name": "세종로 공영", "address": "서울특별시 종로구",
      "lat": 37.57, "lng": 126.97, "distanceFromMe": 540,
      "practiceTypes": ["PARKING"],
      "description": null, "distanceMeters": null,
      "capacity": 1260, "openTime": "00:00" }
  ],
  "hasNext": true,
  "nextCursor": "eyJkIjo1NDAsImlkIjo3fQ==",
  "totalCount": 42
}
```
- 정렬/커서: **현위치까지 거리(m) ASC, place_id ASC**. `hasNext=false`면 `nextCursor=null`.
- 응답은 **공통 `CursorPage<T>`**(`items`·`hasNext`·`nextCursor`·`totalCount`) — 북마크·리뷰 목록도 재사용(ADR 0010).
- `totalCount`(bbox 내 총 장소 수, 클러스터 배지 대조용)는 **첫 페이지(cursor 없음)에서만** 채우고 이후 페이지는 `null`(매 페이지 count 쿼리 방지).
- 공통 아이템: `id·type·name·address·lat·lng·distanceFromMe·practiceTypes`. **연습유형**은 코스=태그들, 주차장=`["PARKING"]`(항상 주차).
- 코스 전용: `description`·`distanceMeters`. 주차장 전용: `capacity`(주차면수)·`openTime`(영업시작, `weekday_hours`의 시작 시각). 해당 없으면 `null`.
- `distanceMeters`(코스 주행거리) ≠ `distanceFromMe`(현위치까지).

### 3. 코스 상세 (JWT)
```json
// GET /api/v1/places/courses/{placeId}
{
  "id": 1, "name": "한강 코스", "address": "서울특별시 영등포구",
  "description": "…",
  "cautions": ["일반통행골목", "비보호좌회전"],
  "distanceMeters": 2100, "bookmarkCount": 12, "isBookmarked": true,
  "practiceTypes": ["STRAIGHT","LANE_CHANGE"],
  "waypoints": [
    { "type": "START", "sequence": 0, "lat": 37.51, "lng": 127.03, "name": null },
    { "type": "VIA", "sequence": 1, "lat": 37.52, "lng": 127.04, "name": "경유1" },
    { "type": "DESTINATION", "sequence": 4, "lat": 37.53, "lng": 127.05, "name": null }
  ]
}
```
- `cautions`는 **문자열 리스트**(칩), 입력 순서 유지. `practiceTypes`는 목록(#2)과 동일 표기.
- 북마크 여부 JSON 키는 **`isBookmarked`**(`ApiResponse.isSuccess`와 동일 규칙). `bookmarkCount`는 COUNT 계산.

### 4. 주차장 상세 (JWT)
```json
// GET /api/v1/places/parkings/{placeId}
{
  "name": "세종로 공영", "address": "서울특별시 종로구", "isBookmarked": false,
  "parking": {
    "roadAddress": "…", "lotAddress": "서울특별시 종로구 세종로 80-1(지하)",
    "managementNo": "100-2-000006", "parkingType": "노외", "capacity": 1260,
    "isFree": false, "hasAccessibleSpace": true, "phone": "02-2290-6567",
    "operator": "서울시설공단", "note": null, "paymentMethods": ["카드"],
    "feeInfo": { "baseMinutes": 5, "baseFee": 430, "addUnitMinutes": 5, "addUnitFee": 430,
                 "dayTicketHours": 0, "dayTicketFee": 0, "monthlyFee": 176000 },
    "operatingHours": { "weekday": "00:00-23:59", "saturday": "00:00-23:59", "holiday": "00:00-23:59" }
  }
}
```
저장은 개별 컬럼, 응답만 `feeInfo`/`operatingHours`로 묶음.

### 5. 북마크 저장·해제 (JWT)
```
POST   /api/v1/places/{placeId}/bookmark   // 저장(멱등: 이미 있으면 200)
DELETE /api/v1/places/{placeId}/bookmark   // 해제
```
응답 데이터 없음(200). 코스·주차장 공통(place 단위).

## 지오·쿼리 접근
- 뷰포트 필터: `place.location && ST_MakeEnvelope(:swLng,:swLat,:neLng,:neLat,4326)`.
- 거리: `ST_Distance(location::geography, :me::geography)`(미터).
- 커서 keyset: `거리 > :curDist OR (거리 = :curDist AND place_id > :curId)`, `ORDER BY 거리, place_id`. 커서 = `(거리, place_id)` base64 불투명 토큰(`CursorCodec`).
- 응답은 공통 `CursorPage<T>`(`global.common.pagination`) — hasNext·nextCursor·조건부 totalCount. `size+1` 조회로 hasNext 판별.
- 목록 쿼리는 **native query**(QueryDSL 미사용). 코스 태그·설명은 페이지 코스 id로, 주차장 주차면수·영업시간은 주차장 id로 별도 조회(`loadCourses`/`loadParkings`). waypoint는 상세에서 조회.

## 완료 조건 (Acceptance Criteria)
- [x] `#1` 전체 place 좌표를 `{id,type,name,address,lat,lng}`로 반환한다.
- [x] `#2` bbox 안 place만, 현위치 거리순으로 반환하고 `size`만큼 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성). `totalCount`는 첫 페이지에서만 bbox 총계와 일치하고, 이후 페이지는 null이다.
- [x] `#2` 아이템은 공통(`address`·`practiceTypes` 등) + 코스는 `description`·`distanceMeters`, 주차장은 `practiceTypes:["PARKING"]`·`capacity`·`openTime`. 해당 없는 필드는 null.
- [x] `#3` 코스 상세가 `practiceTypes`·`cautions`(리스트)·waypoint(순서대로)·`bookmarkCount`·`isBookmarked`를 반환한다.
- [ ] `#4` 주차장 상세가 `feeInfo`·`operatingHours`로 묶인 구조로 반환한다.
- [ ] `#3`/`#4`에 반대 타입 id를 주면 404.
- [ ] `#5` 저장 시 `bookmarked=true`·count 증가, 재저장은 멱등, 해제 시 원복.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문
- 샘플 시드 규모·지역(예: 강남 몇 건)?

## 범위 밖 / 다음
- 서버 클러스터링(데이터 폭증 시), 주차장 목록 상세필드, 레벨 필터(#2), region 그룹, **코스 생성 API(로그인·관리자 승인)**, 리뷰, 공영주차장 open DB 정식 인제스션.
