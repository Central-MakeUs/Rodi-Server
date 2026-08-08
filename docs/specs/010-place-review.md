# 장소 후기 (작성·조회·신고/차단)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-08-01 | Draft | 최초 작성 (2차 업데이트 1번 — 후기). *(작성 시 009였으나 연관 검색어 스펙과 번호 충돌로 **010**으로 조정)* |
| 2026-08-01 | Draft | 리뷰 반영 — 혼잡도 3단계(한산/보통/복잡) 확정, **한 장소에 후기 여러 개 허용**(1레벨 1후기 제약 폐기 → 요약은 후기 **건수** 집계), 레벨 필터 기본값 = **조회자 본인 레벨**(`level=ALL`로 전체), 탈퇴 회원 후기 유지, 차단은 목록에서만 제외, 코스 상세와 후기 조회는 API 분리 유지 |
| 2026-08-03 | Draft | **후기 좋아요 취소** — 이번 범위에서 빼고 추후 구현으로 미룸(`review_like` 테이블·엔드포인트·응답 필드 모두 제거) |
| 2026-08-03 | **Approved** | 구현 착수 — 커밋을 ①작성·수정·삭제 ②신고·차단 ③목록·요약 세 단계로 나눠 단계별로 검토받는다 |
| 2026-08-06 | Approved | 신고 사유를 화면 기준 5종으로 확정(`IRRELEVANT` 추가, `INAPPROPRIATE`·`PRIVACY` 제거, `ETC`→`OTHER`)하고 **선택지를 서버가 폼으로 내려주도록** 추가(`GET /reviews/report-form`). 공통 폼 구조는 `global.common.form` |
| 2026-08-06 | **Implemented** | 신고 **5명 누적 시 자동 비공개** 추가(V15 `hidden_at`) — 목록·요약에서 빠지고 작성자 본인에게만 `isHidden`으로 보인다. 마이그레이션은 **V13**(review)·**V14**(review_report·member_block)·**V15**(hidden_at). 목록은 **JPQL + fetch join**(작성자 닉네임 N+1 방지), 요약만 native `FILTER` 집계 — Postgres가 null 바인드 타입을 못 정해(`could not determine data type`) **레벨 필터는 IN 목록**, **첫 페이지 커서는 미래시각 sentinel**로 표현. 신고·차단은 `ON CONFLICT DO NOTHING` 멱등 |
| 2026-08-06 | Implemented | PR 리뷰 반영 — **후기 내용 상한 1000자 → 150자**(화면 확정값). V13은 이미 적용된 마이그레이션이라 수정하지 않고 **V16 ALTER**로 줄였다 |
| 2026-08-08 | **Implemented** | "인증된 후기" 배지 추가(V20 `is_verified_visit`) — 스펙 011의 GPS 방문 인증 이력을 작성 시점 스냅샷으로 남긴다 |
| 2026-08-08 | **Implemented** | 요약 개편 — **최다 난이도**(`topDifficulty`, 동률은 더 어려운 쪽) 추가, **추천 수는 전체 레벨 합산**으로 분리(`levelReviewCount`·`totalReviewCount`), **혼잡도는 응답에서 제외**(저장은 유지) |
| 2026-08-08 | **Implemented** | 목록 응답 정리 — 카드에 쓰는 값만 남기고 추천 여부·난이도·혼잡도·작성 당시 레벨·`caution`을 뺐다 |
| 2026-08-08 | **Implemented** | **내가 쓴 후기 목록**(`GET /members/me/reviews`) 추가 — 레벨 필터 없이 내 후기를 전부 보여줘, 레벨이 바뀌면 자기 후기가 기본 화면에서 사라지던 문제를 푼다 |
| 2026-08-08 | **Implemented** | **차단한 회원 목록**(`GET /members/me/blocks`) 추가 — 마이페이지에서 차단을 관리할 수 있게 됐다(미해결 질문 6 해소) |

## 배경 / 목적

1차 업데이트까지는 장소·코스를 **탐색**하는 기능만 있었다. 실제로 그 장소가 초보에게 어땠는지는 **먼저 다녀온 사람의 체감**이 가장 정확하다. 이번에 장소(코스·주차장)에 **후기**를 붙여, 추천 여부·체감 난이도·혼잡도·연습 방법과 주의사항을 공유하게 한다.

핵심은 **레벨별로 갈라 보여주는 것**이다. 같은 장소도 SEED와 NAVIGATOR의 체감이 다르므로, 후기 목록과 난이도 분포를 **작성 당시 레벨** 기준으로 필터·집계해 "나와 비슷한 수준의 사람들은 이 코스를 어떻게 느꼈나"를 보여준다(첨부 화면: `난이도` 섹션 + 우상단 레벨 선택 드롭다운 + 난이도별 인원 수 막대).

**장소 : 후기 = 1:N**. `place`가 코스·주차장 공통 슈퍼클래스(JOINED, ADR 0002)이므로 후기는 `place_id`에 달리고 **코스·주차장 모두** 후기를 받는다(북마크와 동일 방침).

### 이 스펙의 범위

- 후기 작성·수정·삭제
- 후기 목록(레벨 필터 + 커서 페이지네이션)
- 후기 요약(레벨별 난이도 분포·추천 수·혼잡도 분포)
- **후기 신고**·**작성자 차단**(후기 노출에 미치는 영향까지)

### 범위 밖

- **코스 상세 개편**, **사용자 연습 코스 목록** — 2차 업데이트의 별도 스펙(각각 011·012 예정).
- 후기 **사진 첨부**(주어진 테이블에 이미지 컬럼 없음).
- **방문 검증**(`driving_record` 미구현 — 실제 이용자인지 확인하지 않는다).
- 관리자 신고 처리 화면·운영자 처리 상태(`status`) — 자동 비공개는 **범위에 포함**(신고 5명 누적).
- **후기 좋아요** — 추후 구현으로 미룸.
- 후기 정렬 옵션(좋아요순 등) — 최신순 고정.
- 후기 수정 이력 보관, 대댓글·답글.

## 요구사항

### 기능 요구사항

1. **후기 작성**: 장소 단위로 후기를 남긴다.
   - **필수**: 추천 여부(boolean) · 체감 난이도 · 혼잡도 · 연습 방법(혼자/동승자) · 후기 내용
   - **선택**: 주의사항(길이 제한 없음)
   - 서버가 **작성 시점 회원 레벨을 스냅샷으로 저장**한다(클라이언트가 보내지 않는다). 레벨이 없는 회원(온보딩 미완료)은 작성할 수 없다.
   - **같은 장소에 여러 후기를 쓸 수 있다** — 방문마다 체감이 다르므로 횟수 제한을 두지 않는다(유니크 제약 없음).
2. **후기 수정**: 본인 후기만. **작성 당시 레벨 = 현재 레벨일 때만** 수정 가능하다. 레벨이 올라간 뒤 이전 레벨에서 쓴 후기는 수정 불가(409).
3. **후기 삭제**: 본인 후기만. **레벨과 무관하게** 삭제 가능(내 글을 지우는 것은 막지 않는다).
4. **후기 목록**: 장소별 후기를 **레벨로 필터**해 **최신순 커서 페이지네이션**으로 반환한다. **기본값은 조회자 본인 레벨**이고 `ALL`로 전체를 본다. 각 항목에 내 후기인지·수정 가능한지를 포함한다.
5. **후기 요약**: 선택한 레벨 기준으로 **난이도별 후기 수**(5단계), 추천/비추천 수, 혼잡도별 후기 수, 총 후기 수를 반환한다. 드롭다운 구성을 위해 **레벨별 후기 수**도 함께 준다. 막대 길이(비율)는 클라이언트가 계산한다.
   - 한 회원이 여러 후기를 쓸 수 있으므로 **집계 단위는 "명"이 아니라 후기 건수**다(화면 표기 `30명`은 기획 확인 필요 — 미해결 질문).
6. **신고**: 후기 단위로 사유(enum)와 상세 설명을 붙여 접수한다. 같은 후기에 대한 중복 신고는 멱등. 본인 후기는 신고할 수 없다.
7. **차단**: 후기 작성자(회원)를 차단·해제한다. 차단하면 **내 후기 목록에서 그 회원의 후기가 사라진다**.

### 비기능 요구사항

- **모든 엔드포인트 JWT 필수.** 장소 상세(스펙 005 #3·#4)가 이미 JWT라 후기도 동일하게 로그인 전용으로 통일한다.
- 목록은 **커서 페이지네이션**(ADR 0010), 공통 `CursorPage<T>` 재사용.
- 후기 수는 **비정규화 컬럼 없이 COUNT**로 계산한다(북마크수와 동일 방침). 트래픽 증가 시 집계 컬럼/캐시는 후속.
- 목록은 **JPQL + fetch join**, 요약 집계만 **native query**(QueryDSL 미사용, ADR 0011 방침 유지).

## 도메인 모델 (마이그레이션 V13·V14 — review 도메인 신규)

> V11(최근 검색어 재설계)·V12(세종 주소 정규화)가 [스펙 008](008-recent-search.md)·[009](009-related-search.md)에서 이미 쓰였다. 후기는 **V13**부터 시작한다. 커밋을 단계별로 나눠 **V13 = `review`**, **V14 = `review_report`·`member_block`** 으로 분리한다.

패키지는 **`domain.review`** 신규(`entity`/`repository`/`service`/`controller`/`dto`). 회원 차단(`member_block`)만 **`domain.member`** 소유다(대상이 후기가 아니라 회원).

### review (장소 후기, place 1:N)

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| id | bigint PK | Y | |
| place_id | bigint FK→place | Y | 후기 대상 장소(코스·주차장 공통) |
| member_id | bigint FK→member | Y | 작성자 |
| is_recommended | boolean | Y | 추천/비추천 |
| difficulty | varchar(20) enum | Y | 사용자 체감 난이도(5단계) |
| congestion | varchar(20) enum | Y | 혼잡도(3단계 — 한산/보통/복잡) |
| practice_method | varchar(20) enum | Y | SOLO(혼자 연습) / ACCOMPANIED(동승자 연습) |
| content | varchar(150) | Y | 후기 내용(1~150자, 화면 확정. V13은 1000자였고 V16에서 줄임) |
| caution | text | N | 주의사항(길이 제한 없음) |
| member_level | varchar(20) enum | Y | **작성 당시** 작성자 레벨(스냅샷, 이후 레벨 변경에도 불변) |
| hidden_at | timestamptz | N | 신고 5명 누적으로 비공개된 시각(null=공개). V15에서 추가 |
| created_at·updated_at | timestamptz | Y | `BaseEntity` |

- **유니크 제약 없음** — 같은 회원이 같은 장소에 여러 후기를 쓸 수 있다(방문마다 체감이 달라진다). 따라서 요약 집계는 **후기 건수**이며 사람 수와 다를 수 있다.
- **`member_level`은 서버가 채운다.** 작성 시 `member.level`을 복사하고 이후 회원 레벨이 바뀌어도 후기 값은 그대로 남는다(레벨별 집계의 기준).
- **수정 가능 판정**: `review.member_level == member.level`. 다르면 수정 거부(삭제는 허용).
- FK는 `ON DELETE CASCADE`(회원 하드 삭제 대비. soft delete가 기본이라 실제 삭제는 익명화 배치에서만).
- 인덱스
  - `(place_id, created_at DESC, id DESC)` — 목록 keyset 커서
  - `(place_id, member_level)` — 레벨 필터 목록·요약 집계
  - `(member_id)` — 내 후기 조회·차단 필터

### review_report (후기 신고)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| review_id | bigint FK→review | 신고 대상 후기 |
| reporter_id | bigint FK→member | 신고자 |
| reason | varchar(30) enum | 신고 사유 |
| detail | text, null | 상세 설명(선택) |
| created_at | timestamptz | |

- **unique(review_id, reporter_id)** — 같은 후기 중복 신고 방지(재신고는 멱등 200, 갱신 없음).
- 신고가 **5명 누적되면 대상 후기를 자동 비공개**(`review.hidden_at`) 처리한다. 상태(처리중/완료) 컬럼은 두지 않는다(필요해지면 `status` 추가).

### member_block (회원 차단, 회원 ↔ 회원)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | bigint PK | |
| blocker_id | bigint FK→member | 차단한 회원(나) |
| blocked_id | bigint FK→member | 차단당한 회원 |
| created_at | timestamptz | |

- **unique(blocker_id, blocked_id)**, `CHECK (blocker_id <> blocked_id)`. index `(blocker_id)`(목록 필터용).
- **효과는 단방향**: 내 후기 목록에서 차단 회원의 후기가 빠진다. 차단당한 쪽 화면은 영향 없다.
- **요약(분포) 집계는 전체 기준**(차단 반영 안 함) — 사람마다 막대 수치가 달라지면 데이터 해석이 흔들리고, 집계 캐싱도 불가능해진다. (미해결 질문에 남김)

### Enum

| Enum | 값 | 비고 |
|------|-----|------|
| `review.difficulty` | VERY_EASY / EASY / NORMAL / HARD / VERY_HARD | 매우 쉬움 / 쉬움 / 보통 / 어려움 / 매우 어려움 (첨부 화면) |
| `review.congestion` | QUIET / NORMAL / CROWDED | 한산 / 보통 / 복잡 (3단계 확정) |
| `review.practice_method` | SOLO / ACCOMPANIED | 혼자 연습 / 동승자 연습 |
| `review.member_level` | `Level` 재사용 (SEED / ROOKIE / OWNER / EXPLORER / NAVIGATOR) | |
| `review_report.reason` | SPAM / ABUSE / IRRELEVANT / FALSE_INFO / OTHER | 스팸·광고 / 욕설, 음란성, 혐오 표현 / 코스와 무관한 내용 / 허위정보 / 기타 (화면 확정) |

> 한글 라벨은 **문서·클라이언트에만** 둔다. 응답은 코드(enum name)로 내려보내고 표기는 클라이언트가 담당한다(기존 `practiceTypes`·`recommendationTags`와 동일 규칙).

### ERD 갱신 필요

`docs/erd.md`의 "추후(미확정)" 항목 중 **리뷰**·**신고/차단**을 확정 테이블(`review`·`review_report`·`member_block`)로 옮기고, 엔티티 요약·제약/인덱스 표에 추가한다.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/places/{placeId}/reviews | 후기 작성 | JWT |
| GET | /api/v1/places/{placeId}/reviews | 후기 목록(레벨 필터·최신순 커서, 기본=내 레벨) | JWT |
| GET | /api/v1/places/{placeId}/reviews/summary | 후기 요약(레벨별 난이도 분포 등, 기본=내 레벨) | JWT |
| GET | /api/v1/members/me/reviews | 내가 쓴 후기 목록(레벨 필터 없음, 최신순 커서) | JWT |
| PUT | /api/v1/reviews/{reviewId} | 후기 수정(전체 교체) | JWT |
| DELETE | /api/v1/reviews/{reviewId} | 후기 삭제 | JWT |
| GET | /api/v1/reviews/report-form | 신고 사유 폼(선택지 정의) | JWT |
| POST | /api/v1/reviews/{reviewId}/report | 후기 신고 | JWT |
| POST | /api/v1/members/{memberId}/block | 회원 차단(멱등) | JWT |
| GET | /api/v1/members/me/blocks | 차단한 회원 목록(최신순 커서) | JWT |
| DELETE | /api/v1/members/{memberId}/block | 차단 해제(멱등) | JWT |

**컨트롤러 배치**(CLAUDE.md 기준 적용)
- `reviews`는 **자체 오퍼레이션 묶음**(작성·목록·요약·수정·삭제)이라 **`ReviewController` 전용**. 작성·목록·요약은 장소 하위(`/places/{placeId}/reviews`), 개별 조작은 후기 식별자만으로 충분하므로 **`/reviews/{reviewId}`**(place 경로 중복 제거 — 장소 상세를 `placeId` 하나로 통합한 스펙 005의 판단과 같은 이유).
- 내가 쓴 후기(`/members/me/reviews`)도 **`ReviewController`** — 경로는 회원 하위지만 오퍼레이션은 후기 묶음에 속한다(연습 목록이 `PracticeController`에 있는 것과 같은 판단).
- `report`는 **단발 엔드포인트**라 소유 컨트롤러인 `ReviewController`에 둔다(별도 컨트롤러 만들지 않음).
- `block`은 대상이 회원이므로 **`MemberController`**(단발 3개, 북마크가 `PlaceController`에 있는 것과 같은 배치). 차단 목록도 같은 컨트롤러에 둔다.

### 1. 후기 작성

```json
// POST /api/v1/places/1/reviews   (JWT)
// Request
{
  "isRecommended": true,
  "difficulty": "EASY",
  "congestion": "NORMAL",
  "practiceMethod": "ACCOMPANIED",
  "content": "차선이 넓고 신호가 단순해서 처음 도로 나갈 때 딱이었어요.",
  "caution": "주말 오후엔 자전거 통행이 많습니다."
}

// Response data
{ "reviewId": 31 }
```

- `member_level`은 **요청에 없다** — 서버가 `member.level`을 스냅샷으로 저장한다.
- 검증: `isRecommended`·`difficulty`·`congestion`·`practiceMethod`·`content` 필수, `content` 1~150자, enum 유효값 아니면 **400**.
- 레벨 미배정(온보딩 미완료) → **409 `REVIEW_409_2`**.
- **같은 장소에 이미 후기가 있어도 계속 작성할 수 있다**(중복 제약 없음).
- 없는 `placeId` → **404**.

### 2. 후기 목록 (레벨 필터 + 최신순 커서)

```
GET /api/v1/places/1/reviews?level=ROOKIE&size=10&cursor=   (JWT)
```

- `level`(선택): **생략하면 조회자 본인 레벨**로 필터한다(첨부 화면 드롭다운의 기본 선택). 전체를 보려면 **`level=ALL`**, 특정 레벨은 enum 값을 그대로 보낸다.
  - 조회자가 레벨 미배정(온보딩 미완료)이면 생략 시 **전체**로 동작한다(필터할 기준이 없다).
- `size` 기본 10, `cursor` 다음 페이지 토큰.

```json
// Response data — CursorPage<ReviewItem>
{
  "items": [
    {
      "reviewId": 31,
      "memberId": 7,
      "nickname": "차근차근 토끼",
      "practiceMethod": "ACCOMPANIED",
      "content": "차선이 넓고 신호가 단순해서…",
      "isMine": true,
      "isEditable": true,
      "isHidden": false,
      "isVerifiedVisit": true,
      "createdAt": "2026-07-30T14:02:11"
    }
  ],
  "hasNext": true,
  "nextCursor": "eyJjIjoiMjAyNi0wNy0zMFQxNDowMjoxMSIsImlkIjozMX0=",
  "totalCount": 71
}
```

- 정렬/커서: **`created_at DESC, id DESC`**. 커서 = `(created_at, id)` base64 불투명 토큰(`CursorCodec`).
- `totalCount`: **`level` 필터를 적용한** 총 개수. **첫 페이지(cursor 없음)에서만** 채우고 이후 페이지는 `null`(공통 `CursorPage` 규칙).
- 같은 회원의 후기가 **여러 건 나올 수 있다**(같은 장소 재작성 허용).
- **항목은 카드에 그릴 값만 담는다** — 추천 여부·난이도·혼잡도는 분포로 보는 값이라 요약(#3)에만 있고, 작성 당시 레벨은 `isEditable` 판정에만 쓰여 내려보내지 않는다.
- `caution`은 **관리자 화면 전용**이라 응답에 넣지 않는다(저장은 계속 한다). 관리자가 모아 보고 타당하면 코스 정보에 반영하는 용도다.
- `isMine`: 내 후기 여부. `isEditable`: `isMine && memberLevel == 내 현재 레벨`(레벨업 후 이전 후기는 `false` → 클라이언트가 수정 버튼을 감춘다).
- `nickname`이 `null`이면 탈퇴·익명화된 회원의 후기다(표기는 클라이언트가 "알 수 없음" 등으로).
- **내가 차단한 회원의 후기는 제외**된다.
- `isVerifiedVisit`: 작성 시점에 그 장소에서 **GPS 방문 인증**에 성공한 이력이 있었는지(스펙 011). "다녀왔어요"만 누른 기록은 인증으로 보지 않으며, 스냅샷이라 이후 연습 항목을 지워도 값이 변하지 않는다.

### 3. 후기 요약 (레벨별 난이도 분포 — 첨부 화면)

```
GET /api/v1/places/1/reviews/summary?level=ROOKIE   (JWT)
```

```json
// Response data
{
  "level": "ROOKIE",
  "levelReviewCount": 71,
  "totalReviewCount": 94,
  "topDifficulty": { "difficulty": "VERY_EASY", "count": 30 },
  "recommendCount": 80,
  "notRecommendCount": 14,
  "difficultyCounts": {
    "VERY_EASY": 30, "EASY": 26, "NORMAL": 5, "HARD": 5, "VERY_HARD": 5
  },
  "levelCounts": {
    "SEED": 12, "ROOKIE": 71, "OWNER": 8, "EXPLORER": 0, "NAVIGATOR": 3
  }
}
```

- `level` 규칙은 목록(#2)과 동일 — **생략 = 조회자 본인 레벨**, `ALL` = 전체 집계(응답 `level`은 실제 적용된 값, 전체면 `"ALL"`).
- **모수가 둘이다.** `difficultyCounts`·`topDifficulty`는 **선택한 레벨** 기준(`levelReviewCount`), 추천/비추천 수는 **전체 레벨 합산**(`totalReviewCount`)이다. 추천은 레벨을 나눠 볼 값이 아니라 그 장소 자체의 평판이라 필터와 무관하게 같은 숫자를 준다.
- `difficultyCounts`는 후기가 0건인 값도 **키를 빼지 않고 `0`으로** 준다(막대 5개가 항상 그려진다).
- `topDifficulty`는 **가장 많이 선택된 난이도와 그 건수**다. **동률이면 더 어려운 쪽**을 고른다(쉽다고 오인해 무리하는 쪽이 더 위험하다). 선택 레벨에 후기가 하나도 없으면 **키 자체를 내려보내지 않는다** — 화면이 문구를 감춘다.
- **혼잡도는 응답에 없다.** 화면에 쓰지 않아 뺐고, 작성 시 저장은 계속 한다(나중에 필요하면 집계만 붙이면 된다).
- **집계 단위는 후기 건수**다. 한 회원이 같은 장소에 여러 후기를 쓸 수 있으므로 화면의 `30명` 표기와 정확히 일치하지 않을 수 있다(기획 확인 — 미해결 질문).
- 요약은 한 쿼리로 두 모수를 함께 센다 — 레벨 조건을 `WHERE`가 아니라 `COUNT(*) FILTER`에 걸어 전체·레벨 집계를 한 번에 얻는다.
- `levelCounts`는 **레벨 필터와 무관한 전체 분포** — 드롭다운에 레벨별 건수를 보여주거나 후기 없는 레벨을 흐리게 처리하는 데 쓴다.
- `levelReviewCount == difficultyCounts 합`, `totalReviewCount == recommendCount + notRecommendCount`.
- 막대 비율은 서버가 계산하지 않는다(클라이언트가 최대값 기준으로 렌더).
- 요약은 **차단을 반영하지 않는다**(전체 기준).

### 3-1. 내가 쓴 후기 목록

```http
GET /api/v1/members/me/reviews?size=20&cursor=   (JWT)
```

```json
// Response data — CursorPage<MyReviewItem>
{
  "items": [
    {
      "reviewId": 3,
      "placeId": 118,
      "placeName": "한강 코스",
      "content": "차선이 넓고 신호가 단순해서…",
      "isEditable": false,
      "isHidden": false,
      "isVerifiedVisit": true,
      "createdAt": "2026-08-08T15:42:16"
    }
  ],
  "hasNext": false,
  "nextCursor": null,
  "totalCount": 2
}
```

- **레벨 필터가 없다.** 이 API의 존재 이유가 그거다 — 장소 후기 목록(#2)은 기본이 조회자 본인 레벨이라 **레벨이 바뀌면 자기가 쓴 후기가 기본 화면에서 사라진다**. 여기서는 작성 당시 레벨과 무관하게 내 후기가 전부 나온다.
- **비공개(신고 누적) 후기도 포함**하고 `isHidden: true`로 표시한다 — 왜 남에게 안 보이는지 본인은 알 수 있어야 한다(장소 후기 목록과 같은 규칙).
- 차단은 무관하다(내가 쓴 글이라).
- 어느 장소에 썼는지가 핵심이라 `placeId`·`placeName`을 싣는다. `nickname`·`isMine`은 전부 나 자신이라 뺀다.
- `isEditable`은 기존 규칙 그대로 — **작성 당시 레벨 == 현재 레벨**일 때만 `true`. 레벨업 후 이전 후기는 목록에 보이되 수정 버튼은 감춘다.
- 정렬·커서·`totalCount` 규칙은 장소 후기 목록과 같다(`created_at DESC, id DESC`, 첫 페이지에서만 총계).

### 3-2. 차단한 회원 목록

```http
GET /api/v1/members/me/blocks?size=20&cursor=   (JWT)
```

```json
// Response data — CursorPage<BlockedMemberItem>
{
  "items": [
    { "memberId": 12, "nickname": "느긋한 거북이", "blockedAt": "2026-08-07T21:11:03" }
  ],
  "hasNext": false,
  "nextCursor": null,
  "totalCount": 3
}
```

- **차단한 시각 최신순**(`created_at DESC, id DESC` keyset). `totalCount`는 첫 페이지에서만 — 공통 `CursorPage` 규칙.
- 항목의 `memberId`가 그대로 해제 요청(`DELETE /members/{memberId}/block`)의 경로 값이다.
- 해제 버튼만 있는 화면이라 레벨·프로필은 싣지 않는다. 탈퇴·익명화된 회원은 `nickname`이 `null`이며, **차단 행 자체는 지우지 않는다**(지우면 복구 시 차단이 풀린다).
- 차단 수가 많지 않아 페이지네이션이 과해 보일 수 있으나, 다른 목록과 규칙을 맞춰 화면이 무한스크롤로 바뀌어도 서버를 고치지 않아도 되게 한다.

### 4. 후기 수정 (전체 교체)

```json
// PUT /api/v1/reviews/31   (JWT)
// Request — 작성과 동일한 바디(폼 전체 재전송)
{
  "isRecommended": false,
  "difficulty": "NORMAL",
  "congestion": "CROWDED",
  "practiceMethod": "SOLO",
  "content": "다시 가보니 퇴근시간엔 정체가 심했어요.",
  "caution": null
}
// Response data: null (200)
```

- **본인 후기만** — 타인 후기면 **403 `REVIEW_403_1`**(후기는 목록에 공개돼 있어 존재 자체를 숨길 이유가 없다).
- **레벨 불일치 시 거부** — `review.member_level != member.level`이면 **409 `REVIEW_409_1`**.
- `member_level`은 수정되지 않는다(작성 시점 값 고정). 없는 `reviewId` → 404.

### 5. 후기 삭제

```
DELETE /api/v1/reviews/31   (JWT)
```

- 응답 데이터 없음(200). **본인 후기만**(타인 403). **레벨 불일치여도 삭제는 허용**.
- 삭제 시 그 후기의 신고 행도 함께 제거된다(FK `ON DELETE CASCADE`). **하드 삭제**(soft delete 아님) — 삭제된 후기는 집계에서도 빠진다.

### 6. 신고 사유 폼 조회

신고 화면의 선택지를 **서버가 정의해 내려준다.** 문구·순서·직접입력 여부를 앱 배포 없이 바꿀 수 있고, 다른 선택 폼(연습 미방문 이유 등)도 같은 구조를 재사용한다(`global.common.form`).

```json
// GET /api/v1/reviews/report-form   (JWT)
// Response data
{
  "questionId": "REVIEW_REPORT_REASON",
  "type": "SINGLE_SELECT",
  "title": "신고 사유",
  "required": true,
  "options": [
    { "code": "SPAM",       "label": "스팸/광고",           "order": 1, "requiresTextInput": false },
    { "code": "ABUSE",      "label": "욕설, 음란성, 혐오 표현", "order": 2, "requiresTextInput": false },
    { "code": "IRRELEVANT", "label": "코스와 무관한 내용",     "order": 3, "requiresTextInput": false },
    { "code": "FALSE_INFO", "label": "허위정보",             "order": 4, "requiresTextInput": false },
    { "code": "OTHER",      "label": "기타",                "order": 5, "requiresTextInput": true,
      "textInputPlaceholder": "이유를 작성해주세요", "textInputMaxLength": 100 }
  ]
}
```

- `options`는 **order 오름차순**. 텍스트 입력이 없는 선택지는 `textInput*` 키를 **아예 내려보내지 않는다**(NON_NULL). 설명이 없는 폼은 `description`도 생략.
- `code`가 곧 신고 요청의 `reason` 값이다.
- 이 폼은 **한글 라벨을 서버가 준다** — 코드만 주고 표기는 클라가 하는 다른 응답(`practiceTypes`·`recommendationTags`)과 다른 방침이며, 폼 문구는 운영 중 바뀔 여지가 커서 이렇게 둔다.

### 7. 후기 신고

```json
// POST /api/v1/reviews/31/report   (JWT)
// Request
{ "reason": "ABUSE", "detail": "특정 지역 비하 표현이 있습니다." }
// Response data: null (200)
```

- `reason` 필수(폼의 `code`), `detail`은 **`OTHER`일 때 필수**(최대 100자 — 폼의 `textInputMaxLength`와 동일). 그 외 사유에서는 무시한다.
- **본인 후기 신고 → 400 `REVIEW_400_1`**. 같은 후기 **재신고는 멱등 200**(중복 저장 없음).

#### 신고 누적 자동 비공개

**서로 다른 5명**에게 신고되면 그 자리에서 후기를 비공개 처리한다(`review.hidden_at` 기록).

- `unique(review_id, reporter_id)` 덕에 **행 수 = 신고자 수**라 별도 중복 제거가 필요 없다. 신고 접수 직후 카운트해 임계값(5) 이상이면 숨긴다.
- **신고 처리는 후기 행 잠금(`SELECT … FOR UPDATE`)으로 직렬화**한다. 잠금이 없으면 READ COMMITTED에서 동시 신고가 서로의 미커밋 행을 못 봐 각자 임계값 미만으로 세고, 둘 다 커밋된 뒤 신고 수는 5인데 공개 상태로 남는다. 신고는 빈도가 낮아 잠금 경합 비용이 문제되지 않는다.
- **노출 범위**: 목록·요약에서 빠지되 **작성자 본인 목록에는 남고** `isHidden: true`로 내려간다(왜 안 보이는지 본인은 알 수 있어야 한다). 요약 집계에서는 **본인에게도 제외**한다 — 안 보이는 글이 분포 막대에 잡히면 수치가 어긋난다.
- 비공개여도 본인의 **수정·삭제는 그대로** 가능하다.
- **복구**는 운영자가 DB에서 `hidden_at`을 `NULL`로 되돌린다(처리 상태 컬럼·관리자 화면은 범위 밖).

### 8. 회원 차단·해제

```
POST   /api/v1/members/7/block   // 차단(멱등)
DELETE /api/v1/members/7/block   // 해제(멱등)
```

- 응답 데이터 없음(200). **자기 자신 차단 → 400 `MEMBER_400_1`**, 없는 회원 → 404.
- 효과: **내 후기 목록에서 그 회원의 후기 제외**(단방향). 요약 집계·다른 화면(코스 목록 등)엔 영향 없음.
- 차단 목록 조회는 `GET /members/me/blocks`(#3-2), 해제는 `DELETE /members/{memberId}/block`.

### 에러 코드 (`ReviewErrorCode` 신규 · `MemberErrorCode` 추가)

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `REVIEW_409_1` | 409 | 레벨이 변경되어 이전 레벨에서 작성한 후기는 수정할 수 없습니다. |
| `REVIEW_409_2` | 409 | 온보딩을 완료한 후 후기를 작성할 수 있습니다. |
| `REVIEW_403_1` | 403 | 본인이 작성한 후기만 수정·삭제할 수 있습니다. |
| `REVIEW_400_1` | 400 | 본인이 작성한 후기는 신고할 수 없습니다. |
| `MEMBER_400_1` | 400 | 자기 자신은 차단할 수 없습니다. |

## 쿼리 접근

- **목록**(JPQL, `JOIN FETCH r.member`): `WHERE place.id = :placeId AND memberLevel IN :levels AND NOT EXISTS(member_block b: b.blocker=:me AND b.blocked=r.member)` + keyset `(createdAt, id) < 커서` + `ORDER BY createdAt DESC, id DESC` + `Pageable(size+1)`(hasNext 판별).
  - **null 바인드를 쓰지 않는다.** Postgres가 `:param IS NULL` 형태에서 타입을 못 정해(`could not determine data type of parameter`) 쿼리가 깨진다 → **레벨 필터 없음 = 전체 레벨 IN 목록**, **첫 페이지 = 미래시각(9999-12-31) + `Long.MAX_VALUE` sentinel 커서**로 표현한다.
- **작성자 닉네임**: `JOIN FETCH r.member`로 목록과 함께 읽어 항목별 조회(N+1)를 막는다.
- **요약**(native): 한 쿼리로 `count(*) FILTER (WHERE difficulty = 'VERY_EASY')` … 식 집계 + `levelCounts`용 `GROUP BY member_level` 1쿼리 = **총 2쿼리**. 레벨 필터는 `member_level = COALESCE(CAST(:level AS varchar), member_level)`로 걸어 전체 집계 시 조건이 자기 비교가 되게 한다(널 분기 없음).
- `(place_id, member_level)` 인덱스가 목록 필터와 요약 집계를 함께 커버한다.

## 완료 조건 (Acceptance Criteria)

- [x] 후기 작성 시 필수 5개 필드가 저장되고, `member_level`에 **작성 시점 회원 레벨**이 저장된다(요청으로 받지 않는다).
- [x] 필수 누락·enum 오류·`content` 151자는 400, 없는 `placeId`는 404를 반환한다.
- [x] 레벨 미배정 회원의 작성은 409(`REVIEW_409_2`)다.
- [x] **같은 회원이 같은 장소에 후기를 여러 번 쓸 수 있고**, 목록·요약에 모두 반영된다(유니크 충돌 없음).
- [x] 회원 레벨이 바뀌어도 기존 후기의 `member_level`은 변하지 않는다.
- [x] 작성 당시 레벨 = 현재 레벨이면 수정되고, 레벨업 이후 이전 후기 수정은 409(`REVIEW_409_1`)다. 타인 후기 수정·삭제는 403이다.
- [x] 삭제는 레벨 불일치여도 성공하고, 삭제된 후기의 신고 행도 함께 사라지며 요약 집계에서 빠진다.
- [x] 목록은 **`level` 미지정 시 조회자 본인 레벨** 후기만, `level=ALL`이면 전체를, 특정 레벨 지정 시 해당 레벨만 **최신순**으로 반환하고 `size`만큼 끊어 `hasNext`·`nextCursor`로 이어진다(2페이지 연속성·중복 없음).
- [x] 레벨 미배정 회원이 `level` 없이 목록을 조회하면 전체가 반환된다.
- [x] 목록 `totalCount`는 **첫 페이지에서만** 채워지고 `level` 필터 기준 총계와 일치하며, 이후 페이지는 `null`이다.
- [x] 목록 항목의 `isMine`·`isEditable`이 요청 회원 기준으로 정확하다(레벨업한 회원의 이전 후기는 `isEditable=false`).
- [x] 내가 쓴 후기 목록은 **작성 당시 레벨과 무관하게** 내 후기를 전부 반환하고, 비공개 후기도 `isHidden=true`로 포함한다.
- [x] 내가 쓴 후기 목록의 `isEditable`이 레벨 일치 여부를 따르고, 장소명이 항목마다 채워진다.
- [x] 차단 목록이 차단한 시각 최신순으로 반환되고, 남이 한 차단은 섞이지 않으며, 해제하면 목록에서 빠진다.
- [x] 목록 항목에 추천 여부·난이도·혼잡도·작성 당시 레벨·`caution`이 없고, `isVerifiedVisit`이 있다.
- [x] 요약의 `difficultyCounts`가 **선택한 레벨** 기준 후기 건수와 일치하고, 0건 값도 키가 `0`으로 존재한다(5개 항목 항상 반환).
- [x] 요약에서 `levelReviewCount == difficultyCounts 합`이고 `totalReviewCount == recommendCount + notRecommendCount`이다(모수가 다르다).
- [x] `topDifficulty`가 최다 난이도와 건수를 주고, **동률이면 더 어려운 쪽**을 고르며, 후기가 없으면 키가 빠진다.
- [x] 추천/비추천 수는 레벨 필터를 바꿔도 같은 값이다(전체 레벨 합산).
- [x] `levelCounts`는 레벨 필터와 무관하게 전체 레벨 분포를 반환한다.
- [x] 신고는 저장되고 재신고는 멱등 200, 본인 후기 신고는 400이며, 신고해도 후기 노출은 바뀌지 않는다.
- [x] 차단하면 그 회원의 후기가 **내 목록에서만** 사라지고, 해제하면 다시 보인다. 요약 수치는 차단과 무관하게 동일하다.
- [x] 자기 자신 차단은 400이다.
- [x] 서로 다른 5명에게 신고되면 후기가 비공개된다(4명까지는 공개).
- [x] 비공개 후기는 남의 목록·요약에서 빠지고, 작성자 본인 목록에만 `isHidden=true`로 남는다(요약에서는 본인에게도 제외).
- [x] 모든 엔드포인트가 미인증 시 401이다.
- [x] 관련 테스트 통과 (`./gradlew test`).

## 확정된 결정 (리뷰 반영)

- **혼잡도 3단계** — 한산(`QUIET`) / 보통(`NORMAL`) / 복잡(`CROWDED`).
- **같은 장소에 후기 여러 개 허용** — 유니크 제약 없음. 따라서 요약은 **후기 건수** 집계다.
- **레벨 필터 기본값 = 조회자 본인 레벨**, 전체는 `level=ALL`.
- **탈퇴·익명화 회원의 후기는 유지**하고 닉네임만 `null`로 내려준다(집계에도 남는다).
- **차단은 후기 목록에서만 제외** — 요약 수치(추천 수·난이도별 수 등)는 전체 기준으로 모두에게 동일하다.
- **신고 사유는 화면 기준 5종**(스팸/광고 · 욕설, 음란성, 혐오 표현 · 코스와 무관한 내용 · 허위정보 · 기타). 초안의 `INAPPROPRIATE`·`PRIVACY`는 없애고 `IRRELEVANT`를 추가, `ETC`→`OTHER`로 통일.
- **선택지는 서버가 폼으로 내려준다**(`GET /reviews/report-form`). 기타는 직접 입력 필수(최대 100자).
- **코스 상세 API와 후기 조회 API는 분리** — 코스 상세 응답에 후기 필드를 넣지 않고 클라이언트가 각각 호출한다.
- **후기 작성 자격 = 로그인 + 레벨 보유**(온보딩 완료). 북마크·방문 이력 요구 없음.

## 미해결 질문

1. ~~**후기 내용 글자 제한**~~ — **1~150자로 확정**(화면 기준). 초안의 1000자는 임시값이었고 V16에서 줄였다.
2. **요약 카운트 표기** — 집계 단위가 **후기 건수**인데 화면은 `30명`이다. (a) 라벨을 "건"으로 바꾸거나 (b) 난이도별 `DISTINCT member_id`로 집계해 "명"을 맞추는 방법이 있다. 기획 확인 필요. *(b는 한 사람이 서로 다른 난이도로 여러 후기를 쓰면 양쪽 막대에 잡힌다.)*
3. **`review.caution`과 `course_caution`의 관계** — 코스에 이미 관리자 등록 주의사항 칩(`course_caution`)이 있다. 별개 표시로 보이나 기획 확인 대기(**미결**).
4. ~~**"인증된 후기" 배지**~~ — **해소**. 스펙 011의 GPS 방문 인증(인정 주행거리 ≥ `min(코스거리 × 40%, 5km)`)을 기준으로 삼아, 작성 시점의 인증 이력을 `review.is_verified_visit`(V20)에 스냅샷으로 남기고 목록 응답 `isVerifiedVisit`으로 내려준다.
5. **신고 `detail` 상한** — 폼의 `textInputMaxLength`와 맞춰 **100자**로 구현(연습 미방문 이유 폼과 동일). 더 길게 받아야 하면 조정.
6. ~~**차단 목록 조회·해제 화면**~~ — **해소**. 마이페이지에서 차단을 관리할 수 있도록 `GET /members/me/blocks`(#3-2)를 추가했다. 해제는 기존 `DELETE /members/{memberId}/block`을 그대로 쓴다.
7. ~~**레벨이 바뀌면 내 후기가 안 보인다**~~ — **해소**. 장소 후기 목록의 레벨 필터 기본값(조회자 본인 레벨)은 그대로 두고, **내가 쓴 후기 목록**(#3-1)을 따로 둬서 해결한다.

## 범위 밖 / 다음

- **코스 상세**(2차 #2)·**사용자 연습 코스 목록**(2차 #4) — 별도 스펙.
- **신고·차단 확장** — 코스·회원 프로필 신고, 운영자 처리 상태(`status`), 관리자 화면(비공개 복구 UI).
- **후기 좋아요**(등록·취소) — 이번 범위에서 제외, 추후 구현. `review_like(review_id, member_id)` unique + `ON CONFLICT DO NOTHING` 멱등으로 얹으면 되고, 응답에 `likeCount`·`isLiked`가 추가된다.
- 후기 **사진 첨부**, 정렬 옵션(좋아요순·추천순), 후기 수정 이력, 답글.
- 후기 수 **비정규화 컬럼·캐시**(COUNT가 병목이 되면 착수).
- **방문 검증 기반 "인증된 후기" 배지** — 방문 판정 기준이 정해진 뒤 별도 작업.
- **후기 도배 방지**(같은 장소 연속 작성 제한·쿨다운) — 여러 후기 허용이라 필요해지면 후속.
