# 온보딩 (닉네임 부여 · 운전 경험/추가정보 · 레벨 배정)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-10 | Draft | 최초 작성 |
| 2026-07-10 | Draft | 리뷰 반영 — 레벨 5단계·차종 사진기준·재제출 불가 확정. 추천 연습 유형 매핑만 대기 |

## 배경 / 목적

가입 직후, 사용자에게 **닉네임을 자동 부여**하고 **운전 경험·추가 정보**를 받아 **레벨**을 배정한다. 레벨과 추천 연습 유형은 이후 맞춤 코스·장소 추천의 기준이 된다. 온보딩 응답은 모바일에서 로컬에 모았다가 **마지막에 한 번에** 서버로 전송된다.

## 요구사항

### 기능 요구사항

1. **닉네임 자동 부여**
   - 신규 가입 시 후보 풀(업로드 CSV `nickname` 열, 990개, 예: "차근차근 토끼")에서 **미사용 닉네임 1개를 무작위 배정**한다.
   - 닉네임은 **중복 불가**(회원 간 유일).
   - **로그인 응답에 닉네임을 포함**해 내려준다(기존 응답 필드 + `nickname`).

2. **온보딩 제출(운전 경험 + 추가 정보)** — 한 번의 요청으로 전송
   - **운전 경험**(사진 1, 점수 산정 대상): 실제 운전 기간 · 최근 운전 빈도 · 도로 주행 경험(복수) · 혼자 운전 범위 · 혼자 주차 수준.
   - **추가 정보**(사진 2): 더 연습하고 싶은 상황(연습 유형, 최대 3개·순위) · 주로 타는 차종 · 운전 목표(자유 텍스트).
   - **최종 점수는 클라이언트가 계산**해 함께 전송한다(문항별 배점은 클라이언트 소유).
   - 서버는 각 정보를 저장하고, **최종 점수로 레벨을 배정**한다.
   - 응답으로 **레벨**과 **추천 연습 유형**(레벨별 고정값, 사용자 선택과 무관)을 내려준다.

### 비기능 요구사항

- 온보딩·닉네임 제출은 **JWT 인증** 필요(로그인 응답은 소셜 로그인 흐름).
- 닉네임 배정은 동시 가입에도 유일성 보장(회원 `nickname` unique + 재시도).

## 도메인 모델

### 닉네임 후보 풀

- 테이블 `nickname_candidate` — CSV의 `nickname` 열만 Flyway 마이그레이션으로 시드(부가 열 `type/modifier/animal`은 저장 안 함). `nickname` unique.
- 배정: 회원 `nickname`에 아직 없는 후보를 무작위 선택 → `member.nickname`에 저장. `member.nickname` unique 제약으로 경합 방지(충돌 시 재시도).

### member 확장 (신규 컬럼 — 마이그레이션 V5 예정)

| 필드 | 타입 | 설명 |
|------|------|------|
| `driving_period` | enum | Q1 실제 운전 기간(단일) |
| `recent_frequency` | enum | Q2 최근 운전 빈도(단일) |
| `solo_driving_range` | enum | Q4 혼자 운전 범위(단일) |
| `solo_parking_level` | enum | Q5 혼자 주차 수준(단일) |
| `driving_experience_score` | int | 클라이언트 최종 점수(0~14) |
| `level` | enum | 배정 레벨 |
| `car_type` | enum | 차종(단일, nullable) |
| `driving_goal` | varchar(30) | 운전 목표(nullable) |
| `onboarded_at` | timestamptz | 온보딩 완료 시각(nullable). **재제출 거부 판정용**(응답에는 노출 안 함) |

- **Q3 도로 주행 경험(복수)**: `member_road_experience(member_id, road_experience)` 조인 테이블(또는 element collection).
- **연습 유형(순위)**: 기존 `member_practice_type`에 `priority`(smallint, 1~3) 추가 — 1순위부터 순서 저장. (ERD의 `practice_type` 테이블 재사용.)

### Enum

| Enum | 값 |
|------|-----|
| `driving_period` (Q1) | UNDER_1_MONTH / MONTHS_1_3 / MONTHS_3_6 / MONTHS_6_12 / YEARS_1_2 / YEARS_2_10 / OVER_10_YEARS |
| `recent_frequency` (Q2) | RARELY / MONTHLY_1_2 / WEEKLY_1 / WEEKLY_2_3 / WEEKLY_4_PLUS |
| `road_experience` (Q3, 복수) | NONE / ACCOMPANIED / PROFESSIONAL_TRAINING / SOLO |
| `solo_driving_range` (Q4) | NEAR_HOME / FAMILIAR_ROAD / UNFAMILIAR_ROAD / HIGHWAY_LONG |
| `solo_parking_level` (Q5) | NONE / WIDE_ONLY / FAMILIAR_PLACE / MOSTLY_POSSIBLE |
| `practice_type` (13종) | U_TURN / LEFT_TURN / PARKING / LANE_CHANGE / INTERSECTION / ROUNDABOUT / UNPROTECTED_LEFT_TURN / HIGHWAY_ENTRY / CORNERING / NARROW_ROAD / MULTILANE / MERGING / STRAIGHT |
| `car_type` | LIGHT(경차) / COMPACT(소형차) / MIDSIZE(중형차) / SEMI_LARGE(준대형) / LARGE(대형차) / SUV |
| `level` | SEED / ROOKIE / OWNER / EXPLORER / NAVIGATOR |

### 레벨 배정 규칙 (사진 3)

- **강제 배정**: Q1(`driving_period`) ∈ { `YEARS_2_10`, `OVER_10_YEARS` } → **NAVIGATOR** (점수 무관).
- 그 외 최종 점수 기준:

| 총점 | 레벨 |
|------|------|
| 0~2 | SEED |
| 3~5 | ROOKIE |
| 6~9 | OWNER |
| 10~14 | EXPLORER |

- 서버는 클라이언트 점수를 신뢰해 매핑하되(배점표는 클라이언트 소유, 서버는 범위 검증만), **NAVIGATOR 강제 규칙은 서버가 Q1로 판정**한다.
- **ERD 갱신 필요(구현 시)**: 기존 ERD의 7단계(DRIVER·MENTOR)·점수식 서술과 `car_type`(VAN 포함/준대형 없음)을 본 스펙 기준(레벨 5단계, 차종 사진 기준)으로 정정한다.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/auth/oauth/{provider} | 소셜 로그인(기존) — 응답에 `nickname` 추가 | 소셜 |
| POST | /api/v1/members/me/onboarding | 온보딩 제출 → 레벨·추천 연습 유형 반환 | JWT |

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
  "roadDrivingExperiences": ["SOLO"],
  "soloDrivingRange": "HIGHWAY_LONG",
  "soloParkingLevel": "MOSTLY_POSSIBLE",
  "finalScore": 12,
  "practiceTypes": ["LANE_CHANGE", "ROUNDABOUT", "NARROW_ROAD"],
  "carType": "MIDSIZE",
  "drivingGoal": "강남 운전 자신있게 하기!"
}

// Response
{
  "level": "NAVIGATOR",
  "recommendedPracticeType": "LANE_CHANGE"   // 레벨별 고정값(미해결: 매핑 확정 필요)
}
```

- `practiceTypes`: 순서 = 우선순위(1~3순위), 최대 3개. **추가 정보(연습 유형·차종·목표)는 선택**(건너뛰기 가능), 운전 경험 5문항은 필수.
- `finalScore`: 0~14 범위 검증.
- **재제출 불가**: 이미 온보딩한 회원(`onboarded_at` 존재)이 다시 제출하면 거부(409 등)한다. 정보 수정은 별도(마이페이지) 기능.

## 완료 조건 (Acceptance Criteria)

- [ ] 신규 가입 시 후보 풀에서 미사용 닉네임이 배정되고, 로그인 응답에 `nickname`이 포함된다.
- [ ] 서로 다른 두 회원에게 같은 닉네임이 배정되지 않는다(unique).
- [ ] 온보딩 제출 시 운전 경험·추가 정보가 저장되고, `driving_experience_score`가 기록된다.
- [ ] Q1이 `YEARS_2_10`/`OVER_10_YEARS`면 점수와 무관하게 `level=NAVIGATOR`가 배정된다.
- [ ] 그 외에는 점수대(0-2/3-5/6-9/10-14)에 따라 SEED/ROOKIE/OWNER/EXPLORER가 배정된다.
- [ ] 응답으로 배정된 레벨과 레벨별 고정 추천 연습 유형이 반환된다.
- [ ] 연습 유형이 1~3순위 순서대로 저장된다.
- [ ] 이미 온보딩한 회원의 재제출은 거부된다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

1. **레벨별 추천 연습 유형 매핑(값 미정, 구현 전 필요)** — 사용자가 추후 전달. (레벨당 1개인지 복수인지 포함)

   | 레벨 | 추천 연습 유형 |
   |------|----------------|
   | SEED | (대기) |
   | ROOKIE | (대기) |
   | OWNER | (대기) |
   | EXPLORER | (대기) |
   | NAVIGATOR | (대기) |

## 다음 업데이트 (이번 범위 밖)

- **닉네임 풀 소진(>990) 처리·닉네임 변경**: 후보 소진 대응(숫자 접미사 등)과 사용자 닉네임 변경 기능은 다음 업데이트에서 다룬다. 이번엔 990개 풀에서 미사용 무작위 배정까지만.

## 확정된 결정 (리뷰 반영)

- 레벨 **5단계**(SEED/ROOKIE/OWNER/EXPLORER/NAVIGATOR), 점수대 0-2/3-5/6-9/10-14, Q1 `2~10년`/`10년 이상` → NAVIGATOR 강제.
- 차종은 **사진 기준**(경차/소형차/중형차/준대형/대형차/SUV) — ERD 갱신.
- 서버는 `finalScore`를 신뢰(범위 검증만).
- 추가 정보(연습 유형·차종·목표)는 **선택**, 운전 경험만 필수.
- 온보딩 **재제출 불가**(거부). 로그인 응답에 온보딩 완료 플래그 **불필요**.
- 닉네임 후보는 `nickname` 열만 시드(부가 열 저장 안 함).
