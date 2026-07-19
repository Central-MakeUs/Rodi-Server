# 마이페이지 (조회·운전목표 수정·저장 목록)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-17 | Draft | 최초 작성 |
| 2026-07-17 | Draft | 미해결 질문 확정 반영(레벨별 추천 매핑, 저장=장소 전체, distanceFromMe null, 목표 빈값 허용, 회원 부분수정 일반화) |
| 2026-07-17 | Draft | 추천을 라벨 없는 `recommendationTags: List<String>` 고정 매핑으로 확정(한글은 문서에만). 미해결 질문 전부 해소 |
| 2026-07-17 | Approved | 사용자 승인 — 구현 가능 |

## 배경 / 목적

로그인한 회원이 자신의 프로필 요약을 확인하고(닉네임·레벨·추천 연습유형·운전목표·저장 수), 운전목표를 수정하며, 저장(북마크)한 장소 목록을 이어 보는 마이페이지를 제공한다. 회원 도메인(닉네임·레벨·운전목표)과 place 도메인(북마크)을 오가는 화면이다.

**범위 밖**: 프로필 이미지, 온보딩 재작성, 레벨 재계산(레벨은 클라이언트가 산정해 저장 — [스펙 004](004-onboarding.md)), 운전기록/리뷰.

## 요구사항

### 기능 요구사항
1. **마이페이지 조회**: 닉네임·레벨·**레벨별 추천 연습유형**·운전목표·저장한 장소 개수를 한 번에 반환.
2. **운전목표 수정**: 운전목표(최대 30자, **빈값 허용**) 변경. 회원 부분 수정으로 일반화.
3. **저장한 장소 목록 조회**: 북마크한 장소(**코스+주차장 전체**)를 **저장 시각 최신순 커서 페이지네이션**으로 반환. 아이템은 현위치 목록(#2, `PlaceListItem`)과 **동일 구성**. 응답에 `totalCount` 포함.

### 비기능 요구사항
- 모두 **JWT 필수**(현재 회원 기준, `@CurrentMember`).
- 목록은 **커서 페이지네이션**(ADR 0010) — 저장 시각 keyset. 공통 `CursorPage<T>` 재사용.
- 저장 수·목록 `totalCount`는 동일 값(`bookmark.countByMemberId`).

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | /api/v1/members/me | 마이페이지 조회 | JWT |
| PATCH | /api/v1/members/me | 회원 부분 수정(현재: 운전목표) | JWT |
| GET | /api/v1/places/bookmarks | 저장한 장소 목록(커서) | JWT |

> **도메인 배치**: 마이페이지 조회·회원수정은 **member 도메인**(`/members/me`). 저장 목록은 반환 아이템이 place 요약(`PlaceListItem`)이라 **place 도메인**(`/places/bookmarks`)에 둔다. 저장 개수는 place 도메인의 북마크 조회 서비스(`countByMember`)를 member 요약 서비스가 호출해 조합한다(member → place 읽기 의존).

### 1. 마이페이지 조회
```json
// GET /api/v1/members/me   (JWT)
// Response data
{
  "nickname": "성난 초보",
  "level": "ROOKIE",
  "recommendationTags": ["U_TURN", "INTERSECTION", "PARKING"],
  "drivingGoal": "골목길에 익숙해지기",
  "savedPlaceCount": 12
}
```
- `recommendationTags`: **레벨에 따라 서버가 결정**하는 추천 태그(문자열 배열, 고정 매핑, 아래 표). 저장 아님(레벨에서 파생). 한글 표시는 클라이언트가 담당(응답엔 코드만).
- `drivingGoal`: 온보딩 때 저장한 값. 없으면 `null`.
- `savedPlaceCount`: 북마크 수(회원 기준 COUNT, 코스+주차장 전체).

**레벨 → 추천 태그 매핑** (한글 — 응답 코드)

| Level | 추천 태그 |
|-------|----------|
| `SEED` | 직선주행(`STRAIGHT`) / 좌우회전(`LEFT_RIGHT_TURN`) / 차선변경(`LANE_CHANGE`) |
| `ROOKIE` | 유턴(`U_TURN`) / 교차로(`INTERSECTION`) / 주차(`PARKING`) |
| `OWNER` | 고속도로(`HIGHWAY_ENTRY`) / 합류(`MERGING`) / 다차로주행(`MULTILANE`) |
| `EXPLORER` | 비보호좌회전(`UNPROTECTED_LEFT_TURN`) / 회전교차로(`ROUNDABOUT`) / 좁은도로(`NARROW_ROAD`) / 코너링(`CORNERING`) |
| `NAVIGATOR` | 코스 등록(`REGISTER_COURSE`) / 리뷰 작성(`WRITE_REVIEW`) / 추천 코스 공유(`SHARE_COURSE`) — 고정 노출(앱 액션 코드) |

- 전부 **표시용 태그**다(버튼·이동 아님, 클라가 태그 칩으로만 렌더). `SEED`~`EXPLORER`의 코드는 `PracticeType` enum 이름과 동일, `NAVIGATOR`는 연습유형이 아닌 활동 태그(리뷰·코스 등록은 미구현 기능이라 고정 노출). 연습유형/활동 구분자(`type`)는 두지 않고 한 배열로 준다. 그래서 응답 타입은 enum이 아니라 **`List<String>`** 이다.

### 2. 회원 부분 수정 (운전목표)
```json
// PATCH /api/v1/members/me   (JWT)
// Request — 보낸 필드만 수정(부분 수정). 현재 수정 가능 필드: drivingGoal
{ "drivingGoal": "고속도로 합류 연습" }
// Response data: null (200)
```
- `drivingGoal` 최대 30자. **빈 문자열/null 허용**(목표 지우기 가능). 30자 초과는 400.
- 부분 수정으로 일반화 — 향후 수정 가능 필드가 늘면 이 엔드포인트에 추가. (지금은 drivingGoal만)

### 3. 저장한 장소 목록 (커서)
```
GET /api/v1/places/bookmarks?size=20&cursor=   (JWT)
```
- 현위치(`lat`/`lng`) **받지 않는다** — 저장 시각순이라 거리 개념이 없음. `size` 기본 20. `cursor` 다음 페이지 토큰.
```json
// Response data — CursorPage<PlaceListItem> (현위치 목록 #2와 동일 아이템)
{
  "items": [
    { "id": 1, "type": "COURSE", "name": "한강 코스", "address": "서울특별시 영등포구",
      "lat": 37.51, "lng": 127.03, "distanceFromMe": null,
      "practiceTypes": ["STRAIGHT","LANE_CHANGE"],
      "description": "…", "distanceMeters": 2100, "capacity": null, "openTime": null },
    { "id": 7, "type": "PARKING", "name": "세종로 공영", "address": "서울특별시 종로구",
      "lat": 37.57, "lng": 126.97, "distanceFromMe": null,
      "practiceTypes": ["PARKING"],
      "description": null, "distanceMeters": null, "capacity": 1260, "openTime": "00:00" }
  ],
  "hasNext": true,
  "nextCursor": "eyJ...",
  "totalCount": 12
}
```
- 정렬/커서: **저장 시각(bookmark.created_at) DESC, bookmark.id DESC**(타이브레이커). 커서 = `(저장시각, id)` base64(`CursorCodec`).
- `totalCount`: 저장한 장소 총 개수(첫 페이지에서만 채움 — 마이페이지 `savedPlaceCount`와 동일).
- 아이템은 `PlaceListItem` 재사용. **`distanceFromMe`는 항상 `null`**(현위치 안 받음) → `PlaceListItem.distanceFromMe`를 `long` → **`Long`(nullable)** 로 변경 필요. #2에선 계속 값이 채워짐.
- 코스/주차장 **모두 포함**(저장한 장소 전체). 폴리모픽 필드는 #2와 동일.

## 도메인 모델

기존 엔티티 재사용, **신규 엔티티/마이그레이션 없음**.

- `Member`(member 도메인): `nickname`·`level`(enum `SEED/ROOKIE/OWNER/EXPLORER/NAVIGATOR`)·`drivingGoal`(varchar 30, nullable). 운전목표 수정용 도메인 메서드 추가(`updateDrivingGoal(String)` — 빈값 허용).
- `Bookmark`(place 도메인): `member`·`place` FK, `created_at`. 저장 시각으로 정렬. `countByMemberId`·회원 기준 목록 조회(place join) 추가.
- **레벨 → 추천 태그**: 고정 매핑(코드 상수/서비스, `Map<Level, List<String>>`). 저장 아님(파생). SEED~EXPLORER는 `PracticeType.name()` 값, NAVIGATOR는 액션 코드 문자열. 응답은 `List<String>`.
- `PlaceListItem`(place 도메인): `distanceFromMe`를 nullable(`Long`)로 변경(저장 목록에서 null, #2에선 값 유지).

## 완료 조건 (Acceptance Criteria)

- [ ] `GET /members/me`가 닉네임·레벨·추천 태그·운전목표·저장 수를 반환한다.
- [ ] 레벨별 `recommendationTags`가 매핑표대로 반환된다(5개 레벨 각 검증, NAVIGATOR는 액션 코드).
- [ ] `PATCH /members/me`로 운전목표가 변경되고 재조회 시 반영된다. 빈값으로 지울 수 있고, 30자 초과는 400.
- [ ] `GET /places/bookmarks`가 저장 시각 최신순으로 코스+주차장을 반환하고 `size`만큼 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성).
- [ ] `totalCount`가 저장 수와 일치하고, 마이페이지 `savedPlaceCount`와 동일하다.
- [ ] 저장 목록 아이템이 `PlaceListItem`과 동일 구조이고 `distanceFromMe`는 `null`이다.
- [ ] 3개 API 모두 미인증 시 401.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

- (없음 — 모든 항목 확정)
