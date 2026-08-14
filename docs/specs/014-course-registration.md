# 사용자 코스 등록 (등록 · 등록 폼 · 승인 노출 필터)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-14 | Draft | 최초 작성 (3차 업데이트 1·2번 — 코스 등록·등록 폼). 승인 상태를 `course`에 두는 안(A) 확정, 미승인 코스는 전체 조회에서 제외 |
| 2026-08-14 | Draft | develop 병합 — 번호 013→**014**([스펙 013](013-app-integration-refinements.md)이 선점), 마이그레이션 V22→**V24**. 요청의 경로점을 `start`/`waypoints`/`destination` 분리에서 **단일 `waypoints` 배열**(`type` 포함, 순서·개수는 서버 검증)로 통일하고, 앱이 카카오맵에서 채우는 값의 출처를 표로 명시 |
| 2026-08-14 | Draft | 구현 반영 — 등록 응답을 **201 → 200**(프로젝트의 모든 POST가 200), 카테고리의 "전체" 버튼 노출은 **항목 수 ≤ 최대 선택 개수로 파생**(구성이 바뀌어도 규칙이 따라온다) |
| 2026-08-14 | Draft | 구현 반영 — **미승인 코스 상세를 `COURSE_404_1`이 아니라 공통 `COMMON_404`로** 응답한다. 전용 코드를 주면 id를 훑어 심사 중인 코스의 존재를 알아낼 수 있다(삭제는 알려주는 게 목적이라 전용 코드 유지) |
| 2026-08-14 | Draft | 미해결 질문 4건 해소 — **코스명은 `name` 선택 필드 유지 + 미제공 시 출발지 지점명**(START의 `name` 필수), **주행거리 필수**, 연습유형 1~3개 필수, 미승인 코스 상세는 등록자 본인만 200. 삭제가 **soft delete로 확정**되어 `deleted_at` 컬럼 추가·**FK cascade 재정의 철회**, 노출 필터에 `deleted_at IS NULL` 추가 |
| 2026-08-14 | Draft | 등록 폼 UI 반영 — 연습유형 **전체 선택 제거**, 섹션 소제목(`기본정보` 등) 응답 추가, 카테고리별 연습유형 매핑·순서를 최신 기획표로 수정 |
| 2026-08-14 | Implemented | 구현 완료 — V24, 코스 등록·등록 폼, 승인 전/삭제 코스 노출 필터, 최신 등록 폼 UI 반영 |

> 3차 업데이트 코스 도메인은 4개 문서로 나뉜다. **이 문서가 도메인 모델(마이그레이션 V24)·승인 상태·노출 규칙의 기준**이고 나머지는 여기를 참조한다.
> [015 내 코스 관리](015-my-course-management.md) · [016 코스 승인](016-course-approval.md) · [017 코스 등록 튜토리얼](017-course-tutorial.md)

## 배경 / 목적

지금 코스는 **운영자가 시딩한 데이터뿐**이다. 사용자는 자기가 자주 다니는 연습 경로를 서비스에 남길 방법이 없고, 코스 수는 운영 투입량에 묶여 있다.

사용자가 지도에서 **출발지·경유지·도착지**를 찍어 코스를 등록하게 하고, **관리자 승인을 거친 코스만** 전체 목록·검색에 노출한다. 승인 전 코스는 등록자 본인에게만 보인다([015](015-my-course-management.md)).

### 범위 밖

- **코스 수정(PUT)** — 3차에서 구현하지 않는다. 잘못 등록하면 삭제 후 재등록한다([015](015-my-course-management.md)).
- **반려 사유** — 상태만 `REJECTED`로 남기고 사유 저장·노출은 추후.
- **등록 개수 제한(스팸 방지)** — 두지 않는다.
- **서버 지오코딩·경로탐색** — 좌표·주소·주행거리는 앱이 카카오맵에서 받은 값을 그대로 저장한다(아래 "책임 분담").
- 관리자 화면 — 승인 API만 정의한다([016](016-course-approval.md)).

## 책임 분담 (앱 ↔ 서버)

서버에는 지오코딩·경로탐색이 **없다**. 앱이 카카오맵으로 고른 결과를 요청에 담고, 서버는 **형식 검증 후 저장만** 한다.

| 값 | 출처 | 서버 처리 |
|----|------|-----------|
| 각 지점 좌표(`lat`·`lng`) | 앱(카카오맵 선택) | SRID 4326 Point로 저장 |
| 각 지점명(`name`) | 앱(주소/장소명 일부) | 그대로 저장. **출발지 지점명은 코스명이 된다**(아래) |
| 코스 주소(`address`, 시군구) | 앱 | `place.address`에 저장 — **없으면 주소 검색에서 이 코스가 빠진다** |
| 주행거리(`distanceMeters`) | 앱(길찾기 결과) | `course.distance_meters`에 저장. **필수** |

서버가 카카오 Local/길찾기 API를 직접 부르지 않는 이유: 등록 경로에 REST 키·쿼터·외부 장애가 들어온다. [스펙 011](011-practice-course.md)에서 이미 앱 측정값(인정 주행거리)을 서버가 신뢰하는 전례가 있다.

## 요구사항

### 기능 요구사항

1. **코스 등록**: 출발지 1 · 경유지 0~3 · 도착지 1, 주행거리, 연습유형 1~3개, 한줄소개(10~30자), 주의사항(선택, 100자)을 받아 코스를 만든다. **코스명은 받지 않고 출발지 지점명을 쓴다.** 상태는 **`PENDING`(승인 대기)** 로 시작한다.
2. **등록 폼 조회**: 연습유형 **카테고리 → 연습유형** 트리와 입력 제약(글자수·최대 개수·안내 문구)을 서버가 내려준다. 앱 배포 없이 문구·구성을 바꾸기 위함이다.
3. **승인 전 비노출**: `PENDING`·`REJECTED` 코스는 **전체 목록·검색·연관검색어·마커 좌표·상세** 어디에도 나오지 않는다. 등록자 본인만 [015](015-my-course-management.md)의 내 코스 목록에서 본다.
4. **연습유형 카테고리는 저장하지 않는다**: 카테고리는 폼 구성·화면 탐색용이고, 저장·매칭은 기존 `PracticeType`(13종)으로만 한다.

### 비기능 요구사항

- 등록·폼 조회 모두 **JWT 필수**.
- 좌표는 **SRID 4326(WGS84)**. 코스의 대표 좌표(`place.location`)는 기존 규약대로 **출발지**다.
- 승인 노출 필터는 **리포지토리 쿼리 한 곳씩**에 넣고, 빠뜨림이 없도록 완료 조건에 엔드포인트별로 나열한다.

## 도메인 모델 (마이그레이션 V24)

> V23까지 사용 중([스펙 013](013-app-integration-refinements.md)이 V22·V23 사용). 코스 승인·작성자는 **V24**, 튜토리얼 컬럼은 **V25**([017](017-course-tutorial.md)).

### course 확장

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| approval_status | varchar(20) enum | Y | PENDING / APPROVED / REJECTED |
| created_by_member_id | bigint FK→member | N | 등록자. **NULL = 운영자 시딩분**(사용자가 삭제·수정 불가) |
| approved_at | timestamp | N | 마지막 승인 시각(`APPROVED`로 바뀔 때 갱신) |
| deleted_at | timestamp | N | 삭제 시각(**soft delete**, NULL=살아 있음) — [015](015-my-course-management.md) |

```sql
ALTER TABLE course ADD COLUMN approval_status VARCHAR(20);
UPDATE course SET approval_status = 'APPROVED';               -- 기존 시딩분은 전부 승인 상태
ALTER TABLE course ALTER COLUMN approval_status SET NOT NULL;
ALTER TABLE course ADD CONSTRAINT ck_course_approval_status
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE course ADD COLUMN created_by_member_id BIGINT
    REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE course ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE course ADD COLUMN deleted_at  TIMESTAMP;

CREATE INDEX idx_course_created_by ON course (created_by_member_id, place_id DESC);
```

- **DEFAULT를 두지 않는다** — 애플리케이션이 상태를 항상 명시하게 해서 "기본값이 승인"이 되는 사고를 막는다.
- `created_by_member_id`는 **ON DELETE SET NULL**. 탈퇴는 soft delete(ADR 0004)라 보통 발생하지 않지만, [즉시 탈퇴 API](013-app-integration-refinements.md)로 물리 삭제되더라도 다른 사용자가 쓰고 있는 코스가 사라지면 안 된다. NULL이 된 코스는 운영자 코스와 같이 취급된다(사용자 삭제 불가).
- 승인 상태를 **별도 접수 테이블이 아니라 `course`에 둔다**. 승인 후에도 `place.id`가 그대로라 북마크·후기·연습기록이 이어지고, place 기준으로 짜인 목록·검색 쿼리를 재구성하지 않아도 된다. 대가로 **모든 place 조회에 필터를 넣어야 한다**(아래).
- **삭제는 soft delete라 FK는 손대지 않는다.** 행이 지워지지 않으므로 `bookmark`·`review`·`member_practice`의 참조가 그대로 유효하고, cascade 재정의도 필요 없다(마이그레이션이 `course` 컬럼 추가만으로 끝난다).

### Enum

| Enum | 값 | 저장 |
|------|-----|------|
| `ApprovalStatus` | PENDING(승인 대기) / APPROVED(승인) / REJECTED(반려) | `course.approval_status` |
| `PracticeCategory` | BASIC_DRIVING / CITY_BASIC / PARKING_SPACE / TRAFFIC_FLOW / COMPLEX | **저장하지 않음** — 폼 응답 생성 전용 |
| `PracticeType` | 기존 13종 그대로 | `course_practice_type` |

### PracticeCategory ↔ PracticeType 매핑

[스펙 007](007-course-search-filter.md)의 홈 필터 카테고리와 **같은 구성**이다(007은 클라 소유였고, 여기서 **서버가 폼으로 내려주는 정의**를 갖는다 — DB에는 넣지 않는다).

| code | 표시명 | 순서 | 연습유형 |
|------|--------|------|----------|
| BASIC_DRIVING | 기초 주행 | 1 | STRAIGHT · LEFT_RIGHT_TURN · LANE_CHANGE |
| CITY_BASIC | 도심 기본 | 2 | INTERSECTION · U_TURN |
| PARKING_SPACE | 주차 | 3 | PARKING |
| TRAFFIC_FLOW | 도로 흐름 | 4 | MULTILANE · MERGING · HIGHWAY_ENTRY |
| COMPLEX | 복합 상황 | 5 | ROUNDABOUT · UNPROTECTED_LEFT_TURN · NARROW_ROAD · CORNERING |

- **`PARKING`은 코스 태그로 허용한다** — 경로 중간에 주차 연습 구간이 있을 수 있다. 다만 최신 등록 폼에서는 `PARKING`이 **주차 카테고리에만** 나온다.
- **카테고리별 "전체" 선택 버튼은 내려주지 않는다** — UI에서 제거됐다.
- 카테고리 code `PARKING_SPACE`는 `PracticeType.PARKING`과 이름이 겹치지 않게 붙였다(007의 "넓은 공간"에 해당, 표시명만 "주차").

### 연습유형 선택 규칙

서버 검증은 **1~3개·중복 없음**뿐이고, 나머지는 앱 동작 규칙이다(폼 응답의 값으로 서버가 문구·노출을 통제한다).

| 규칙 | 주체 |
|------|------|
| 카테고리를 넘나들며 **통합 최대 3개** | 서버 검증 + 앱 |
| 카테고리를 바꿔도 기존 선택값 유지 | 앱 |
| 선택된 유형 재선택 시 해제 | 앱 |
| 3개 선택 후 추가 선택 시 기존 유지 + 안내(CM-08) | 앱 (문구는 서버가 폼으로 내려줌) |

## API 명세 (패키지 `domain.place`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/courses | 코스 등록(승인 대기로 생성) | JWT |
| GET | /api/v1/courses/registration-form | 등록 폼(카테고리·연습유형·입력 제약) | JWT |

**컨트롤러 배치**(CLAUDE.md 기준): 코스는 등록·폼·내 목록·삭제라는 **자체 오퍼레이션 묶음**을 가지므로 **`CourseController` 전용**으로 분리한다. 엔티티가 `domain.place.entity.Course`라 패키지는 `domain.place`에 둔다. 승인은 관리자 전용이라 [`AdminCourseController`](016-course-approval.md)로 따로 뺀다.

### 1. 코스 등록

```json
// POST /api/v1/courses   (JWT)
{
  "name": "압구정로데오역",          // 선택 — 생략하면 START의 name으로 채운다
  "address": "서울특별시 강남구",
  "distanceMeters": 8200,
  "waypoints": [
    { "type": "START",       "lat": 37.5273, "lng": 127.0403, "name": "압구정로데오역" },
    { "type": "VIA",         "lat": 37.5227, "lng": 127.0521, "name": "청담사거리" },
    { "type": "VIA",         "lat": 37.5266, "lng": 127.0670, "name": "영동대교남단" },
    { "type": "DESTINATION", "lat": 37.5133, "lng": 127.0533, "name": "삼성중앙역" }
  ],
  "practiceTypes": ["STRAIGHT", "LANE_CHANGE", "INTERSECTION"],
  "description": "차선이 넓고, 직선 구간이 길어요.",
  "caution": "갑자기 나오는 자전거 주의!"
}

// Response data (200)
{ "courseId": 101, "approvalStatus": "PENDING" }
```

> **201이 아니라 200**이다 — 이 프로젝트의 POST(후기 작성·연습 담기 등)가 모두 공통 `ApiResponse`로 200을 돌려주고 있어 등록만 다르게 두지 않는다.

| 필드 | 필수 | 제약 |
|------|------|------|
| `name` | N | 최대 255자(`place.name`). **없으면 출발지(`START`) 지점명으로 채운다** |
| `address` | Y | 최대 100자, **"시/도 + 시군구"**(예: `서울특별시 강남구`) |
| `distanceMeters` | Y | 1 이상 |
| `waypoints` | Y | **2~5개**, 경로 순서대로. 아래 검증 규칙 참고 |
| `waypoints[].type` | Y | `START` · `VIA` · `DESTINATION` |
| `waypoints[].lat`·`lng` | Y | `lat` −90~90, `lng` −180~180 |
| `waypoints[].name` | **START만 Y** | 최대 255자. **`START`의 값이 코스명이 된다**(아래) |
| `practiceTypes` | Y | **1~3개**, 중복 불가, `PracticeType` 13종 |
| `description` | Y | **10~30자** (한줄 소개) |
| `caution` | N | 최대 100자 |

**코스명은 출발지 지점명이 기본값이다.** 등록 화면에 코스명 입력란이 없으므로 `name`을 생략하면 서버가 `waypoints[0].name`(START의 지점명·주소)을 `place.name`으로 쓴다. `name` 필드 자체는 남겨둔다 — `place.name`이 이미 있는 컬럼이고, 나중에 입력란이 생기거나 앱이 다른 문자열을 쓰기로 해도 API를 바꾸지 않아도 된다.

- START의 `name`은 **필수** — 코스명의 기본값이 되기 때문이다. 경유지·도착지는 비어 있어도 된다.
- 앱이 START의 `name`에 **장소명**(`압구정로데오역`)을 넣느냐 **도로명 주소**(`서울 강남구 압구정로 100`)를 넣느냐가 곧 목록에 보이는 코스명이 된다. 프론트와 표기를 맞춘다.
- 코스명은 유일하지 않아도 된다(같은 출발지의 코스가 여럿일 수 있다). 장소명 검색([스펙 007](007-course-search-filter.md))은 이 값으로 걸린다.

**경로점을 하나의 배열로 받는다.** `waypoint` 테이블이 세 유형을 한 테이블에 담고, 등록 화면의 경로 목록·카카오맵 검색 결과도 순서 있는 한 줄기라 매핑이 1:1이다. 경유지 최대 개수 정책이 바뀌어도 DTO는 그대로다. 대신 잘못된 조합을 서버가 막는다:

- **첫 항목은 `START`, 마지막 항목은 `DESTINATION`** — 아니면 400
- `START`·`DESTINATION`은 **각각 정확히 1개**
- 가운데는 전부 `VIA`이고 **0~3개** (경유지 없이 출발·도착 2개만도 가능)

저장 결과:

- `place` — `place_type='COURSE'`, `name` = 요청의 `name` **또는 `START`의 `name`**, `address`, `location` = **`START`의 좌표**(SRID 4326)
- `course` — `description`(한줄소개), `distance_meters`, `approval_status='PENDING'`, `created_by_member_id`
- `waypoint` — 배열 순서대로 `sequence`를 **0부터 1씩** 부여(START=0 … DESTINATION=마지막), `type`은 받은 값 그대로
- `course_caution` — `caution`이 있으면 1건(자유 입력 1개)
- `course_practice_type` — 선택한 연습유형

검증 실패는 전역 400(`INVALID_INPUT_VALUE`)이다. 같은 경로의 중복 등록은 막지 않는다.

**앱이 채우는 값의 출처**(프론트 확인 완료 전제, [책임 분담](#책임-분담-앱--서버) 참고):

| 필드 | 카카오에서 받는 값 |
|------|--------------------|
| `lat`·`lng` | 장소 검색 결과의 `y`·`x`(문자열 → **숫자 변환**, x·y 뒤바뀜 주의) 또는 지도 클릭 좌표 |
| `waypoints[].name` | 검색으로 고르면 `place_name`, 핀만 찍으면 좌표→주소의 도로명 주소. **START는 필수**(코스명이 된다), 나머지는 생략 가능 |
| `address` | **`START` 좌표** → 좌표→행정구역의 `region_1depth_name` + `" "` + `region_2depth_name`. 서버의 지역 목록(`regions.csv` 231개)과 **문자열이 정확히 일치**해야 지역 검색에 걸린다 |
| `distanceMeters` | 자동차 길찾기(경유지 포함) 응답의 총 거리(m). **필수** — 방문 인증·레벨 누적의 근거값이다 |

### 2. 등록 폼 조회

```json
// GET /api/v1/courses/registration-form   (JWT)
// Response data
{
  "maxWaypoints": 3,
  "sections": {
    "basicInfo": "기본정보",
    "practiceCategory": "연습유형 카테고리 고르기",
    "practiceType": "연습유형",
    "caution": "주의사항 작성",
    "description": "한줄 소개"
  },
  "practiceType": {
    "maxSelect": 3,
    "maxSelectExceededMessage": "연습유형은 최대 3개까지 선택할 수 있어요.",
    "categories": [
      {
        "code": "BASIC_DRIVING", "label": "기초 주행", "order": 1,
        "practiceTypes": [
          { "code": "STRAIGHT",        "label": "직선주행", "order": 1 },
          { "code": "LEFT_RIGHT_TURN", "label": "좌우회전", "order": 2 },
          { "code": "LANE_CHANGE",     "label": "차선변경", "order": 3 }
        ]
      },
      {
        "code": "COMPLEX", "label": "복합 상황", "order": 5,
        "practiceTypes": [
          { "code": "ROUNDABOUT",            "label": "회전교차로",   "order": 1 },
          { "code": "UNPROTECTED_LEFT_TURN", "label": "비보호좌회전", "order": 2 },
          { "code": "NARROW_ROAD",           "label": "좁은 도로",    "order": 3 },
          { "code": "CORNERING",             "label": "코너링",      "order": 4 }
        ]
      }
    ]
  },
  "inputs": {
    "caution":     { "required": false, "maxLength": 100, "placeholder": "예) 갑자기 나오는 자전거 주의!" },
    "description": { "required": true, "minLength": 10, "maxLength": 30, "placeholder": "예) 차선이 넓고, 직선 구간이 길어요." }
  }
}
```

- 위 예시는 카테고리 2개만 보였고 실제로는 **5개 전부**를 order 순으로 내려준다.
- `code`가 그대로 등록 요청의 `practiceTypes` 값이 된다.
- **`global.common.form`의 `FormResponse`를 재사용하지 않는다** — 그쪽은 단일 선택 문항 하나(신고 사유·미방문 사유)를 위한 구조라 2단 트리·입력 제약을 담을 수 없다. 전용 DTO `CourseRegistrationFormResponse`를 둔다.
- 한글 라벨·순서·섹션 제목·안내 문구를 서버가 쥐고 있어 앱 배포 없이 바꿀 수 있다.

### 3. 노출 필터 (기존 조회 API 수정)

미승인(`PENDING`·`REJECTED`)·삭제된 코스가 새지 않도록 **place를 읽는 모든 경로**에 조건을 추가한다. 주차장은 영향받지 않는다.

```sql
AND (p.place_type <> 'COURSE'
     OR EXISTS (SELECT 1 FROM course c
                WHERE c.place_id = p.id
                  AND c.approval_status = 'APPROVED'
                  AND c.deleted_at IS NULL))
```

| 대상 | 위치 |
|------|------|
| 뷰포트 목록 `findInViewport` / 필터 목록 `findInViewportFiltered` / `countInViewport` | `PlaceRepository` |
| 키워드 검색 `searchByKeyword` / `searchByKeywordFiltered` / `countByKeyword` | `PlaceRepository` |
| 연관검색어 `searchByNameRelevance` / `countByNameRelevance` | `PlaceRepository` |
| 마커 좌표 `getAllCoordinates`(현재 `findAll()`) | `PlaceQueryService` → 전용 쿼리로 교체 |
| 장소 상세 `GET /places/{placeId}` | `PlaceQueryService` — 아래 분기 |

**장소 상세의 분기**

| 코스 상태 | 요청자 | 결과 |
|-----------|--------|------|
| 승인·미삭제 | 누구나 | 200 |
| 미승인 | 등록자 본인 | 200 (내 코스 목록에서 진입) |
| 미승인 | 그 외 | 404 **`COMMON_404`** — 없는 장소와 **똑같은 응답** |
| **삭제됨** | 누구나(등록자 포함) | **404 `COURSE_404_2` "삭제된 코스입니다."** |

- **미승인에 전용 코드를 주지 않는 이유**: 코드가 갈리면 id를 훑어 "심사 중인 코스가 존재한다"는 사실을 알아낼 수 있다. 승인 전 코스는 존재 자체를 숨겨야 하므로 없는 장소와 구분되지 않게 응답한다.
- **삭제에는 전용 코드를 주는 이유**: 북마크·연습 목록에 항목이 남아 있어 사용자가 탭할 수 있다. 이때는 "삭제됐다"를 **알려주는 것이 목적**이라 구분한다([015](015-my-course-management.md)).

지역 검색 인덱스([RegionSearchIndex](../../src/main/java/cmc/rodi/domain/place/service/RegionSearchIndex.java))는 주소 사전 기반이라 영향 없다.

### 에러 코드 (`CourseErrorCode` 신규)

| 코드 | HTTP | 메시지 | 쓰는 곳 |
|------|------|--------|---------|
| `COURSE_404_1` | 404 | 코스를 찾을 수 없습니다. | 코스 삭제·승인 변경([015](015-my-course-management.md)·[016](016-course-approval.md))에서 코스가 아닌 id를 받았을 때 |
| `COURSE_404_2` | 404 | 삭제된 코스입니다. | 삭제된 코스 상세·승인 변경 |

> 상세의 **미승인**은 공통 `COMMON_404`(`ErrorCode.ENTITY_NOT_FOUND`)를 쓴다(위 참고). 삭제 권한(403)은 [015](015-my-course-management.md)에서 같은 enum에 추가한다.

## 완료 조건 (Acceptance Criteria)

- [ ] 코스를 등록하면 `place`(place_type=COURSE, location=START 좌표) + `course`(approval_status=PENDING, created_by=요청자) + `waypoint`(배열 순서대로 sequence 0…N) + 태그 + 주의사항이 한 트랜잭션으로 저장된다.
- [ ] 경유지 없이 `[START, DESTINATION]` 2개만으로 등록되고, `VIA`가 4개면 400이다.
- [ ] 첫 항목이 `START`가 아니거나 마지막이 `DESTINATION`이 아니면 400, `START`·`DESTINATION`이 2개 이상이거나 없으면 400이다.
- [ ] 연습유형 0개 또는 4개는 400, 중복 값은 400, `PARKING`은 정상 등록된다.
- [ ] 한줄소개가 9자·31자면 400이고, 주의사항 101자면 400, 주의사항 없이도 등록된다.
- [ ] `name`을 생략하면 코스명이 **출발지(`START`)의 `name`** 으로 저장되고, 보내면 그 값이 저장된다.
- [ ] START의 `name`이 없으면 400이다(경유지·도착지의 `name`은 없어도 등록된다).
- [ ] `distanceMeters`가 없거나 0 이하면 400이다.
- [ ] 등록 폼이 섹션 소제목 5개와 카테고리 5개를 order 순으로 반환하고, **전체 선택 필드는 내려주지 않으며**, 최대 개수 3·초과 안내 문구·한줄소개 10~30 제약을 담는다.
- [ ] 등록 직후 그 코스가 **뷰포트 목록·필터 목록·키워드 검색·연관검색어·마커 좌표에 나오지 않는다**(각각 검증).
- [ ] 미승인 코스 상세는 제3자에게 **없는 장소와 같은 404(`COMMON_404`)**, **등록자 본인에게는 200**이다.
- [ ] 삭제된 코스는 전체 조회 어디에도 없고, 상세는 등록자 본인에게도 **404 `COURSE_404_2`("삭제된 코스입니다.")** 다.
- [ ] 기존 시딩 코스는 V24 이후에도 전부 노출된다(backfill `APPROVED` 확인).
- [ ] 등록·폼 조회 모두 미인증 시 401이다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 확정된 결정

- **승인 상태는 `course` 컬럼**(별도 접수 테이블 없음) — 승인 후 id·북마크·후기가 이어진다. 대신 place 조회 전부에 노출 필터를 넣는다.
- **카테고리는 서버 enum이되 DB에 저장하지 않는다** — 폼 응답 생성 전용. 저장·매칭은 `PracticeType`만.
- **`PARKING`을 코스 태그로 허용** — 경로 중간의 주차 연습 구간을 표현할 수 있어야 한다.
- **경로점은 단일 `waypoints` 배열**(출발·경유·도착을 `type`으로 구분) — `waypoint` 테이블·화면·카카오 검색 결과와 1:1. 순서·유형·개수는 서버가 검증한다.
- **좌표·주소·주행거리는 앱이 카카오맵에서 받아 보내고 서버는 저장만** 한다.
- **코스명(`name`)은 선택 필드로 두고, 없으면 출발지(`START`) 지점명을 쓴다** — 등록 화면에 입력란이 없지만 `place.name`이 이미 있는 컬럼이라 요청 필드를 없애지 않는다. START의 `name`은 기본값 공급원이라 필수.
- **주행거리는 필수** — 앱이 길찾기 결과를 보낸다. 방문 인증([011](011-practice-course.md))·레벨 누적([012](012-level-progress.md))의 근거값이라 비워둘 수 없다.
- **연습유형은 1~3개 필수**.
- **미승인 코스 상세는 등록자 본인만 200**, 제3자는 404.
- **삭제는 soft delete**(`course.deleted_at`) — 전체 조회에서 빠지고, 상세는 `COURSE_404_2`("삭제된 코스입니다.")로 구분해 안내한다([015](015-my-course-management.md)).
- **주의사항은 자유 입력 1개**(최대 100자, 선택) — `course_caution`에 1건으로 저장.
- **수정(PUT)·반려 사유·등록 개수 제한은 범위 밖**.

## 미해결 질문

- (없음 — 위 결정으로 모두 해소)

> 다만 **출발지 `name`의 표기**(장소명 `압구정로데오역` vs 도로명 주소 `서울 강남구 압구정로 100`)는 프론트와 맞춰야 한다. 그 값이 그대로 목록에 보이는 코스명이 된다. 서버 구현에는 영향이 없다.

## 범위 밖 / 다음

- 코스 수정, 반려 사유 저장·노출, 등록 개수 제한, 중복 경로 탐지.
- 관리자 웹 화면, 실제 권한(ROLE) 부여 — [016](016-course-approval.md) 참고.
