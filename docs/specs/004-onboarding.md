# 온보딩 (닉네임 부여 · 운전 경험/추가정보 · 레벨 배정)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-10 | Draft | 최초 작성 |
| 2026-07-10 | Draft | 리뷰 반영 — 레벨 5단계·차종 사진기준·재제출 불가 확정. 추천 연습 유형 매핑만 대기 |
| 2026-07-11 | Draft | 저장구조 확정 — member_onboarding 1:1 분리, 복수응답 jsonb 리스트, 점수 미저장, driving_goal은 member, 닉네임 리소스 기반(테이블 없음) |
| 2026-07-11 | Draft | 요청을 점수 대신 **레벨**로 변경(클라가 점수→레벨 변환·전송, 서버는 enum 검증 후 저장) |
| 2026-07-11 | Draft | 온보딩 API 구현 — 응답 200만(데이터 없음), 추천유형 매핑은 클라 소유로 확정, `LEFT_TURN`→`LEFT_RIGHT_TURN`, 홈 정렬은 별도 기능 |

## 배경 / 목적

가입 직후, 사용자에게 **닉네임을 자동 부여**하고 **운전 경험·추가 정보**를 받아 **레벨**을 배정한다. 레벨과 추천 연습 유형은 이후 맞춤 코스·장소 추천의 기준이 된다. 온보딩 응답은 모바일에서 로컬에 모았다가 **마지막에 한 번에** 서버로 전송된다.

## 요구사항

### 기능 요구사항

1. **닉네임 자동 부여**
   - 신규 가입 시 후보 풀(업로드 CSV `nickname` 열, 990개, 예: "차근차근 토끼")에서 **미사용 닉네임 1개를 무작위 배정**한다.
   - 닉네임은 **중복 불가**(회원 간 유일).
   - **로그인 응답에 닉네임을 포함**해 내려준다(기존 응답 필드 + `nickname`).

2. **온보딩 제출(운전 경험 + 추가 정보)** — 한 번의 요청으로 전송
   - **운전 경험**(사진 1, 클라이언트 레벨 산정 근거): 실제 운전 기간 · 최근 운전 빈도 · 도로 주행 경험(복수) · 혼자 운전 범위 · 혼자 주차 수준.
   - **추가 정보**(사진 2): 더 연습하고 싶은 상황(연습 유형, 최대 3개·순위) · 주로 타는 차종 · 운전 목표(자유 텍스트).
   - **레벨은 클라이언트가 계산**한다: 문항별 점수 → 최종 점수 → 레벨 변환(Navigator 강제 규칙 포함)까지 클라이언트가 수행하고, **레벨만 전송**한다(점수는 안 보냄).
   - 서버는 각 정보와 **전달받은 레벨을 저장**한다(점수·변환 로직 없음, 레벨 enum 유효성만 검증).
   - **응답 데이터는 없다(200만).** 추천 연습유형·레벨은 클라이언트 로컬 값이라 서버가 돌려주지 않는다.

### 비기능 요구사항

- 온보딩·닉네임 제출은 **JWT 인증** 필요(로그인 응답은 소셜 로그인 흐름).
- 닉네임 배정은 동시 가입에도 유일성 보장(회원 `nickname` unique + 재시도).

## 도메인 모델

### 닉네임 부여

- **후보 풀은 리소스 파일**(CSV `nickname` 990개)로 번들 → 기동 시 `NicknamePool` 빈이 메모리에 로드(불변). **별도 테이블 없음.**
- 부여(신규 가입 시): 사용 중 닉네임(`SELECT nickname FROM member WHERE nickname IS NOT NULL`)을 제외한 미사용 후보에서 무작위 1개 선택 → `member.nickname`에 저장.
- **정합성**: `member.nickname` UNIQUE가 최종 방어선. 동시 가입 충돌 시 1~2회 재시도(다른 후보 선택).
- 소진(>990)·닉네임 변경은 다음 업데이트 범위.

### member 확장 (마이그레이션 V5) — 노출·활용되는 값만 member에

| 필드 | 타입 | 설명 |
|------|------|------|
| `level` | varchar(enum) | 배정 레벨(마이페이지·추천에 사용) |
| `driving_goal` | varchar(30), null | 운전 목표(마이페이지 노출) |

### member_onboarding (신규 1:1 테이블 — 저장 목적, 마이페이지 미노출)

`member_id`가 PK이자 FK(member와 1:1). 온보딩 원자료를 보관한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `member_id` | bigint | PK, FK→member |
| `driving_period` | varchar(enum) | Q1 실제 운전 기간(단일) |
| `recent_frequency` | varchar(enum) | Q2 최근 운전 빈도(단일) |
| `solo_driving_range` | varchar(enum) | Q4 혼자 운전 범위(단일) |
| `solo_parking_level` | varchar(enum) | Q5 혼자 주차 수준(단일) |
| `road_experiences` | jsonb | Q3 도로 주행 경험(복수) — 예: `["SOLO"]` |
| `practice_types` | jsonb | 선호 연습유형 — **순서=우선순위**, 예: `["LANE_CHANGE","ROUNDABOUT","NARROW_ROAD"]`(최대 3) |
| `car_type` | varchar(enum), null | 차종(단일) |
| `onboarded_at` | timestamptz | 온보딩 완료 시각. 행 존재=완료 → **재제출 거부** 판정 |

- **점수는 저장·수신하지 않는다.** 레벨은 클라이언트가 계산해 전송하며, 서버는 `member.level`에 저장한다.
- 복수/순위 응답은 별도 테이블 없이 **jsonb 리스트로 한 행에** 저장(순서 보존). Hibernate `@JdbcTypeCode(SqlTypes.JSON) List<Enum>` 매핑.

### Enum

| Enum | 값 |
|------|-----|
| `driving_period` (Q1) | UNDER_1_MONTH / MONTHS_1_3 / MONTHS_3_6 / MONTHS_6_12 / YEARS_1_2 / YEARS_2_10 / OVER_10_YEARS |
| `recent_frequency` (Q2) | RARELY / MONTHLY_1_2 / WEEKLY_1 / WEEKLY_2_3 / WEEKLY_4_PLUS |
| `road_experience` (Q3, 복수) | NONE / ACCOMPANIED / PROFESSIONAL_TRAINING / SOLO |
| `solo_driving_range` (Q4) | NEAR_HOME / FAMILIAR_ROAD / UNFAMILIAR_ROAD / HIGHWAY_LONG |
| `solo_parking_level` (Q5) | NONE / WIDE_ONLY / FAMILIAR_PLACE / MOSTLY_POSSIBLE |
| `practice_type` (13종) | U_TURN / LEFT_RIGHT_TURN / PARKING / LANE_CHANGE / INTERSECTION / ROUNDABOUT / UNPROTECTED_LEFT_TURN / HIGHWAY_ENTRY / CORNERING / NARROW_ROAD / MULTILANE / MERGING / STRAIGHT |
| `car_type` | LIGHT(경차) / COMPACT(소형차) / MIDSIZE(중형차) / SEMI_LARGE(준대형) / LARGE(대형차) / SUV |
| `level` | SEED / ROOKIE / OWNER / EXPLORER / NAVIGATOR |

### 레벨 변환 규칙 (사진 3 — 클라이언트 수행, 참고)

레벨 변환은 **클라이언트가** 하며(서버는 결과 레벨만 저장), 아래는 클라이언트와 합의한 규칙이다.

- **강제 배정**: Q1(`driving_period`) ∈ { `YEARS_2_10`, `OVER_10_YEARS` } → **NAVIGATOR** (점수 무관).
- 그 외 최종 점수 기준:

| 총점 | 레벨 |
|------|------|
| 0~2 | SEED |
| 3~5 | ROOKIE |
| 6~9 | OWNER |
| 10~14 | EXPLORER |

- **서버**: 변환 로직 없음. 요청의 `level`이 유효한 enum인지만 검증하고 그대로 저장.
- **ERD 갱신 필요(구현 시)**: 기존 ERD의 7단계(DRIVER·MENTOR)·점수식 서술과 `car_type`(VAN 포함/준대형 없음)을 본 스펙 기준(레벨 5단계, 차종 사진 기준)으로 정정한다.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/auth/oauth/{provider} | 소셜 로그인(기존) — 응답에 `nickname` 추가 | 소셜 |
| POST | /api/v1/members/me/onboarding | 온보딩 제출(저장). 성공 시 200, 응답 데이터 없음 | JWT |

### 로그인 응답 변경 (발췌)

```json
// Response (status=SUCCESS)
{
  "status": "SUCCESS",
  "accessToken": "…",
  "refreshToken": "…",
  "isNewMember": true,
  "nickname": "차근차근 토끼"
}
```

### 온보딩 제출

```json
// Request
{
  "drivingPeriod": "YEARS_2_10",
  "recentFrequency": "MONTHLY_1_2",
  "roadExperiences": ["SOLO"],
  "soloDrivingRange": "HIGHWAY_LONG",
  "soloParkingLevel": "MOSTLY_POSSIBLE",
  "level": "NAVIGATOR",
  "practiceTypes": ["LANE_CHANGE", "ROUNDABOUT", "NARROW_ROAD"],
  "carType": "MIDSIZE",
  "drivingGoal": "강남 운전 자신있게 하기!"
}

// Response — 200, data 없음 (추천유형·레벨은 클라 로컬 값이라 서버는 저장만)
{ "isSuccess": true, "code": "COMMON_200", "data": null }
```

- `practiceTypes`: 순서 = 우선순위(1~3순위), 최대 3개. **추가 정보(연습 유형·차종·목표)는 선택**(건너뛰기 가능), 운전 경험 5문항은 필수.
- `level`: 유효한 레벨 enum(SEED/ROOKIE/OWNER/EXPLORER/NAVIGATOR)인지 검증. 클라이언트가 변환해 전송(점수는 안 받음).
- **재제출 불가**: 이미 온보딩한 회원(`onboarded_at` 존재)이 다시 제출하면 거부(409 등)한다. 정보 수정은 별도(마이페이지) 기능.

## 완료 조건 (Acceptance Criteria)

- [ ] 신규 가입 시 후보 풀에서 미사용 닉네임이 배정되고, 로그인 응답에 `nickname`이 포함된다.
- [ ] 서로 다른 두 회원에게 같은 닉네임이 배정되지 않는다(unique).
- [ ] 온보딩 제출 시 운전 경험·추가 정보가 `member_onboarding`에 저장되고, `member.level`·`member.driving_goal`이 저장된다(점수 자체는 미저장).
- [ ] 요청으로 받은 `level`이 `member.level`에 저장되고, 필수 항목 누락·유효하지 않은 값이면 400을 반환한다.
- [ ] 제출 성공 시 200을 반환한다(응답 데이터 없음).
- [ ] 연습 유형이 1~3순위 순서대로 저장된다(jsonb 순서 보존).
- [ ] 이미 온보딩한 회원의 재제출은 거부된다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

- (없음) — 남았던 추천 연습유형 매핑은 아래처럼 **클라이언트 소유**로 확정되어 서버 응답과 무관해졌다.

## 레벨별 추천 연습유형 (참고 — 클라이언트 소유)

카드 팝업(ON-EX-02)에서 보여주는 고정 태그. **서버는 저장·반환하지 않는다**(클라 로컬 값). 홈 필터 카테고리와는 별개.

| 레벨 | 추천 연습유형 |
|------|----------------|
| SEED | STRAIGHT · LEFT_RIGHT_TURN · LANE_CHANGE |
| ROOKIE | U_TURN · INTERSECTION · PARKING |
| OWNER | HIGHWAY_ENTRY · MERGING · MULTILANE |
| EXPLORER | UNPROTECTED_LEFT_TURN · ROUNDABOUT · NARROW_ROAD · CORNERING |
| NAVIGATOR | (연습유형 없음 — 코스 등록/리뷰/공유 등 액션, 클라가 안내) |

> **홈 바텀시트 정렬(카테고리·filter_tags·코스 노출 순서)은 별도 기능**이다. Course 도메인이 필요해 이 스펙 범위 밖이며, filter_tags 기본값은 그때 저장된 `level`·`practice_types`로 파생한다.

## 다음 업데이트 (이번 범위 밖)

- **닉네임 풀 소진(>990) 처리·닉네임 변경**: 후보 소진 대응(숫자 접미사 등)과 사용자 닉네임 변경 기능은 다음 업데이트에서 다룬다. 이번엔 990개 풀에서 미사용 무작위 배정까지만.

## 확정된 결정 (리뷰 반영)

- 레벨 **5단계**(SEED/ROOKIE/OWNER/EXPLORER/NAVIGATOR), 점수대 0-2/3-5/6-9/10-14, Q1 `2~10년`/`10년 이상` → NAVIGATOR 강제.
- 차종은 **사진 기준**(경차/소형차/중형차/준대형/대형차/SUV) — ERD 갱신.
- **레벨 변환은 클라이언트가 수행**(점수→레벨, Navigator 규칙 포함). 요청은 점수가 아닌 **`level`**을 받고, 서버는 enum 유효성만 검증해 저장.
- 추가 정보(연습 유형·차종·목표)는 **선택**, 운전 경험만 필수.
- 온보딩 **재제출 불가**(거부). 로그인 응답에 온보딩 완료 플래그 **불필요**.
- **저장 구조**: `member`에는 노출·활용 값만(`level`·`driving_goal`), 나머지 온보딩 원자료는 **`member_onboarding` 1:1 테이블**로 분리.
- **복수/순위 응답은 별도 테이블 없이 `jsonb` 리스트**로 한 행에 저장(`practice_types`는 순서=우선순위).
- **운전 경험 점수는 미저장**(레벨 계산에만 사용).
- **닉네임 후보 테이블 없음** — CSV를 리소스로 번들해 메모리 로드, `member.nickname` UNIQUE로 유일성 보장.
