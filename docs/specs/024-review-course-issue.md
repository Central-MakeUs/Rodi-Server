# 후기 '별로에요' 코스 문제 항목

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-10-27 | Draft | 최초 작성 — 이슈 [#85](https://github.com/Central-MakeUs/Rodi-Server/issues/85), 화면 RV-03(후기 상세정보 1/2) 개편. 문제 항목은 **저장만**(운영 참고용), 선택지는 **서버 폼**으로 내려준다. **선택지 구성은 미결정**(미해결 질문 1) |
| 2026-10-28 | Draft | 미해결 질문 답변 반영 — ① 선택지는 **시안 그대로** 확정(코드 이름 확정) ② 요약 `levelReviewCount`를 **난이도를 표시한 후기 수**로 재정의 ③ 기존 비추천 후기는 그대로 둔다(운영 데이터 확인 중) ④ 운영 확인 방법은 미정(추후 관리자 페이지 가능성) |
| 2026-10-28 | **Approved** | 승인 — 구현 착수. 커밋을 ①DB·도메인(V30·`CourseIssue`·`Review`) ②작성·수정 분기·문제 항목 폼·상세 ③요약 `levelReviewCount` 재정의 세 단계로 나눠 검토받는다 |

## 배경 / 목적

지금은 후기 1단계 화면에서 **추천해요/별로에요 어느 쪽을 골라도 같은 평가 항목**(난이도·혼잡도·주의사항)을 받는다. 그런데 "별로에요"를 고른 사용자에게 필요한 것은 난이도 체감보다 **코스에 무슨 문제가 있었는지**다. 차량 진입 불가, 도로 정보 불일치, 공사·통제처럼 코스 정보 자체가 틀렸다는 신호는 운영 쪽에서 코스를 손볼 근거가 된다.

이번에 "별로에요"를 고르면 **코스 문제 항목(복수 선택)** 을 받도록 분기한다. "추천해요"는 기존 흐름 그대로다.

화면 흐름: RV-02-1 연습 완료 재진입 팝업 → "다녀왔어요" → **RV-03 후기 상세정보 1/2(이번 변경)** → RV-03-1 후기 상세정보 2/2(연습 방법·후기 작성, 변경 없음)

| 단계 | 추천해요 | 별로에요 |
|------|----------|----------|
| 1/2 | 난이도 · 혼잡도 · 주의사항 | **코스 문제 항목(복수) + 기타 직접 입력** |
| 2/2 | 연습 방법 · 후기 내용 | 연습 방법 · 후기 내용 |

관련 스펙: [010 장소 후기](010-place-review.md) · [013 앱 연동 보완](013-app-integration-refinements.md)(후기 폼 API 미제공 결정) · [018 텍스트 길이](018-text-length-grapheme.md)

### 범위 밖

- 코스 문제 항목의 **노출**(후기 목록·요약·장소 상세) — 이번엔 저장만 한다. 화면 요구가 생기면 그때 추가한다.
- 문제 항목 누적에 따른 **자동 처리**(코스 비노출·승인 상태 변경 등)·관리자 조회 화면.
- 난이도·혼잡도·연습 방법의 서버 폼화 — 스펙 013 결정 유지(enum, 문구는 클라이언트).

## 요구사항

### 기능 요구사항

1. **분기 입력**: 후기 작성·수정 요청은 `isRecommended`에 따라 1단계 필수 항목이 달라진다.
   - **추천해요(`true`)**: `difficulty`·`congestion` 필수(기존과 같음). `caution` 선택.
   - **별로에요(`false`)**: `courseIssues` **1개 이상 필수**. `difficulty`·`congestion`은 받지 않는다.
   - 두 경우 모두 `practiceMethod` 필수, `content` 선택(2/2 화면, 기존과 같음).
2. **반대쪽 값은 무시한다**: 별로에요로 온 `difficulty`·`congestion`·`caution`, 추천해요로 온 `courseIssues`·`courseIssueDetail`은 **에러 없이 버리고 저장하지 않는다**. 사용자가 1단계에서 추천해요→별로에요로 바꿔도 앱이 이전 값을 지워 보내야 할 부담을 지지 않게 한다(신고 `detail`을 사유가 OTHER가 아닐 때 무시하는 것과 같은 방침).
3. **복수 선택·중복**: `courseIssues`는 복수 선택이다. 같은 코드가 중복으로 오면 하나로 합친다.
4. **기타 직접 입력**: `OTHER`를 고르면 `courseIssueDetail` **필수**, **최대 30자**(grapheme 기준, 스펙 018). 앞뒤 공백은 잘라내고, 공백뿐이면 없는 것으로 본다. `OTHER`가 없으면 `courseIssueDetail`은 무시한다.
5. **선택지 폼**: 문제 항목의 코드·문구·순서·직접 입력 여부를 **서버가 폼으로 내려준다**(`GET /reviews/course-issue-form`). 신고 사유 폼과 같은 `FormResponse`를 쓰고 유형은 `MULTI_SELECT`다.
6. **수정**: PUT 전체 교체 규칙 그대로다. 추천↔비추천을 바꿔 수정하면 반대쪽 값은 지워진다(별로에요→추천해요면 문제 항목 삭제, 추천해요→별로에요면 난이도·혼잡도·주의사항 삭제). 레벨 불일치 시 409는 기존과 같다.
7. **상세 조회**: 수정 화면이 채울 수 있도록 `courseIssues`·`courseIssueDetail`을 추가한다. 상세는 본인 전용이라 "저장만" 방침과 충돌하지 않는다.
8. **노출 없음**: 문제 항목은 후기 목록·내 후기 목록·요약 어디에도 내리지 않는다.
9. **요약 모수 정리**: 요약의 `levelReviewCount`를 **선택 레벨에서 난이도를 표시한 후기 수**로 바꾼다. 난이도 분포 합계와 항상 같아져, 앱이 "N명이 평가" 같은 표기나 비율 계산에 그대로 쓸 수 있다.

### 비기능 요구사항

- JWT 필수(후기 API 공통).
- 문제 항목은 **별도 테이블**에 행으로 저장한다(복수 선택 → 정규화). 코드값은 DB CHECK로도 막는다(V17과 같은 방침).
- "추천해요면 난이도·혼잡도 필수"를 **DB CHECK로도** 강제한다(애플리케이션 검증의 최종 방어선).

## API 명세

| Method | Path | 설명 | 인증 | 변경 |
|--------|------|------|------|------|
| GET | /api/v1/reviews/course-issue-form | 코스 문제 항목 폼 | JWT | **신규** |
| POST | /api/v1/places/{placeId}/reviews | 후기 작성 | JWT | 분기 검증·필드 추가 |
| PUT | /api/v1/reviews/{reviewId} | 후기 수정 | JWT | 분기 검증·필드 추가 |
| GET | /api/v1/reviews/{reviewId} | 후기 상세 | JWT | 응답 필드 추가 |
| GET | /api/v1/places/{placeId}/reviews/summary | 후기 요약 | JWT | `levelReviewCount` 의미 변경 |

**컨트롤러 배치** — `ReviewController`. 신고 사유 폼(`/reviews/report-form`)과 같은 자리다.

### 1. 코스 문제 항목 폼 (신규)

```json
// GET /api/v1/reviews/course-issue-form   (JWT)
// Response data — 시안(RV-03) 그대로
{
  "questionId": "REVIEW_COURSE_ISSUE",
  "type": "MULTI_SELECT",
  "title": "해당 코스에 어떤 문제가 있나요?",
  "required": true,
  "options": [
    { "code": "VEHICLE_RESTRICTED", "label": "차량 진입이 제한된 장소예요",      "order": 1, "requiresTextInput": false },
    { "code": "ROAD_INFO_MISMATCH", "label": "도로 정보가 실제와 달라요",        "order": 2, "requiresTextInput": false },
    { "code": "CONSTRUCTION",       "label": "공사·통제 등으로 현재 주행할 수 없어요", "order": 3, "requiresTextInput": false },
    { "code": "NOT_FOR_PRACTICE",   "label": "연습 코스로 적합하지 않아요",       "order": 4, "requiresTextInput": false },
    { "code": "OTHER",              "label": "기타",                          "order": 5, "requiresTextInput": true,
      "textInputPlaceholder": "이유를 작성해주세요.", "textInputMaxLength": 30 }
  ]
}
```

- `options`는 order 오름차순, 텍스트 입력이 없는 선택지는 `textInput*` 키를 생략한다(기존 `FormOption` 규칙).
- 배타 선택지(`exclusive`)는 없다.
- `code`가 곧 작성 요청 `courseIssues`의 값이다.

### 2. 후기 작성 — 별로에요

```json
// POST /api/v1/places/1/reviews   (JWT)
// Request
{
  "isRecommended": false,
  "courseIssues": ["ROAD_INFO_MISMATCH", "OTHER"],
  "courseIssueDetail": "입구 차단봉이 내려가 있어요",
  "practiceMethod": "SOLO",
  "content": "지도엔 길이 있는데 실제론 막혀 있었어요."
}
// Response data
{ "reviewId": 42 }
```

추천해요 요청은 기존과 같다(`courseIssues`·`courseIssueDetail` 생략).

### 3. 후기 상세 — 응답 필드 추가

```json
// GET /api/v1/reviews/42   (JWT)
{
  "reviewId": 42, "placeId": 1, "placeName": "북악스카이웨이 드라이브",
  "isRecommended": false,
  "difficulty": null, "congestion": null,
  "courseIssues": ["ROAD_INFO_MISMATCH", "OTHER"],
  "courseIssueDetail": "입구 차단봉이 내려가 있어요",
  "practiceMethod": "SOLO",
  "content": "지도엔 길이 있는데 실제론 막혀 있었어요.",
  "caution": null,
  "isEditable": true, "isHidden": false, "isVerifiedVisit": false,
  "createdAt": "2026-10-27T14:02:11"
}
```

- `courseIssues`는 **폼의 order 순**으로 내려준다. 추천해요 후기는 빈 배열 `[]`.
- 값이 없는 필드는 키를 남긴 채 `null`(기존 상세 응답 규칙). 별로에요 후기의 `difficulty`·`congestion`은 `null`이다.

### 4. 후기 요약 — `levelReviewCount` 재정의

```json
// GET /api/v1/places/1/reviews/summary?level=ROOKIE   (JWT) — ROOKIE 후기 5건 중 1건이 새 별로에요(난이도 없음)
{
  "level": "ROOKIE",
  "levelReviewCount": 4,
  "totalReviewCount": 12,
  "topDifficulty": { "difficulty": "EASY", "count": 2 },
  "recommendCount": 9, "notRecommendCount": 3,
  "difficultyCounts": { "VERY_EASY": 1, "EASY": 2, "NORMAL": 1, "HARD": 0, "VERY_HARD": 0 },
  "levelCounts": { "SEED": 7, "ROOKIE": 5 }
}
```

- 필드 이름·타입은 그대로, **의미만** "선택 레벨 후기 수" → "선택 레벨에서 난이도를 표시한 후기 수"로 바뀐다. 항상 `difficultyCounts` 값의 합과 같다.
- `totalReviewCount`·추천/비추천 수·`levelCounts`(드롭다운)는 기존대로 **모든 후기**를 센다. 위 예시처럼 `levelCounts.ROOKIE`(5)와 `levelReviewCount`(4)가 달라질 수 있다.
- `topDifficulty`는 난이도를 표시한 후기가 0건이면 생략된다(기존 "후기가 없으면 생략"과 같은 판정을 분포 기준으로).
- 기존 비추천 후기가 있었다면 난이도가 있어 계속 셌겠지만, 운영에는 0건이다(미해결 질문 3).
- **앱 확인 필요**: 앱이 `levelReviewCount`를 "이 레벨 후기 수"(예: 목록 헤더)로 쓰고 있다면 표기가 바뀐다. 그 용도라면 `levelCounts[level]`을 쓰면 된다.

### 검증 실패

모두 **400 `COMMON_400`(입력값 검증 실패)** — 기존 `@Valid` 실패와 같은 코드다.

| 상황 | 결과 |
|------|------|
| 추천해요인데 `difficulty` 또는 `congestion` 없음 | 400 |
| 별로에요인데 `courseIssues` 없음·빈 배열 | 400 |
| `OTHER` 선택했는데 `courseIssueDetail` 없음·공백뿐 | 400 |
| `courseIssueDetail` 30자 초과 | 400 |
| 폼에 없는 코드 | 400(enum 역직렬화 실패) |

## 도메인 모델 (마이그레이션 V30 — 구현 시점 최신 +1)

### review (변경)

| 필드 | 변경 |
|------|------|
| difficulty | `NOT NULL` 해제 |
| congestion | `NOT NULL` 해제 |
| course_issue_detail | **추가** `TEXT` null — 기타 직접 입력. 길이는 grapheme 기준이라 컬럼 폭이 아니라 애플리케이션이 강제한다(content와 같은 이유, 스펙 018) |

```sql
ALTER TABLE review
    ALTER COLUMN difficulty DROP NOT NULL,
    ALTER COLUMN congestion DROP NOT NULL,
    ADD COLUMN course_issue_detail TEXT,
    -- 추천 후기는 난이도·혼잡도가 반드시 있어야 한다
    ADD CONSTRAINT ck_review_recommended_ratings
        CHECK (NOT is_recommended OR (difficulty IS NOT NULL AND congestion IS NOT NULL));
```

- 기존 `ck_review_difficulty`·`ck_review_congestion`은 NULL을 통과시키므로 그대로 둔다(CHECK는 NULL이면 참).
- **비추천 후기에 난이도가 있으면 안 된다**는 반대 방향 제약은 걸지 않는다 — 기존 비추천 후기에 값이 있다(미해결 질문 3).

### review_course_issue (신규, review 1:N)

| 필드 | 타입 | NN | 설명 |
|------|------|----|------|
| review_id | bigint FK→review `ON DELETE CASCADE` | Y | |
| issue | varchar(30) | Y | 문제 항목 코드 — CHECK로 enum 값 강제 |

- PK `(review_id, issue)` — 같은 후기에 같은 항목 중복 불가.
- 엔티티는 `Review`의 `@ElementCollection Set<CourseIssue>`로 둔다. 독립 생명주기가 없고 후기와 함께 저장·교체·삭제되는 값 묶음이라 별도 엔티티가 필요 없다.

### CourseIssue (enum 신규, `domain.review.entity`)

`ReportReason`과 같은 구조 — 라벨·순서를 enum이 들고 `toOption()`으로 폼 선택지가 된다. `OTHER`만 직접 입력(placeholder `이유를 작성해주세요.`, 최대 30자).

### Review (변경)

- 작성·수정 시 `recommended`에 따라 반대쪽 값을 비운다(요구사항 2·6). 이 규칙은 엔티티가 가진다 — 서비스가 아니라 `Review`가 "별로에요면 난이도 없음"을 보장한다.
- `difficulty`·`congestion`은 nullable이 된다.

### 요약 집계 영향

- 난이도 분포는 값별 `FILTER`로 세므로 **별로에요 후기는 분포에서 자연스럽게 빠진다**. 추천/비추천 수·총 후기 수에는 그대로 잡힌다.
- `topDifficulty`는 분포에서 계산하므로 영향 없다.
- `levelReviewCount`는 분포 합계로 재정의한다(API 명세 4) — 집계 쿼리의 `levelCount`를 `in_level AND difficulty IS NOT NULL`로 바꾼다.

## 완료 조건 (Acceptance Criteria)

**폼**
- [ ] `GET /reviews/course-issue-form`이 `MULTI_SELECT` 폼을 order 오름차순으로 반환하고, `OTHER`만 `requiresTextInput: true`·`textInputMaxLength: 30`이다.

**작성**
- [ ] 별로에요 + `courseIssues` 1개 이상이면 작성이 성공하고, 문제 항목이 저장되며 난이도·혼잡도는 `null`이다.
- [ ] 별로에요로 `difficulty`·`congestion`·`caution`을 함께 보내도 성공하고, 세 값은 저장되지 않는다.
- [ ] 추천해요로 `courseIssues`를 보내도 성공하고, 문제 항목은 저장되지 않는다.
- [ ] 별로에요 + `courseIssues` 누락·빈 배열은 400.
- [ ] 추천해요 + 난이도 또는 혼잡도 누락은 400(기존 동작 유지).
- [ ] `OTHER` 선택 + `courseIssueDetail` 누락·공백뿐은 400, 31자는 400, 30자(이모지 포함 grapheme 기준)는 성공.
- [ ] `OTHER`가 없으면 `courseIssueDetail`은 저장되지 않는다.
- [ ] 같은 코드를 두 번 보내면 하나만 저장된다.

**수정·상세·삭제**
- [ ] 별로에요 후기를 추천해요로 수정하면 문제 항목·직접 입력이 지워지고 난이도·혼잡도가 저장된다. 반대 방향도 같다.
- [ ] 상세 조회에 `courseIssues`(폼 order 순)·`courseIssueDetail`이 내려가고, 추천해요 후기는 `courseIssues: []`.
- [ ] 후기를 삭제하면 문제 항목 행도 함께 지워진다.

**노출·집계**
- [ ] 후기 목록·내 후기 목록·요약 응답에 문제 항목이 나가지 않는다.
- [ ] 별로에요 후기는 난이도 분포와 `levelReviewCount`에 잡히지 않고, 비추천 수·`totalReviewCount`·`levelCounts`에는 잡힌다.
- [ ] `levelReviewCount`는 항상 `difficultyCounts` 합계와 같다.
- [ ] 선택 레벨 후기가 모두 새 별로에요 후기면 `levelReviewCount: 0`이고 `topDifficulty`가 생략된다.

**DB**
- [ ] V30 후 추천 후기에 난이도·혼잡도 `NULL`을 넣으면 DB가 거부한다(`ck_review_recommended_ratings`).
- [ ] `review_course_issue.issue`에 enum 밖의 값을 넣으면 DB가 거부한다.
- [ ] 기존 후기(추천·비추천 모두)가 V30 적용 후 그대로 조회된다.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

1. ~~코스 문제 항목 선택지~~ → **확정(2026-10-28)**: 시안 그대로 5개, 기타 직접 입력 30자. 코드 이름은 API 명세 1의 값으로 확정한다(`VEHICLE_RESTRICTED`·`ROAD_INFO_MISMATCH`·`CONSTRUCTION`·`NOT_FOR_PRACTICE`·`OTHER`). 이후 항목을 추가하면 enum + CHECK 교체 마이그레이션, 문구·순서만 바뀌면 마이그레이션 없음.
2. ~~요약 모수~~ → **확정**: (a) 방향 — 분포 합계를 모수로 쓴다. 서버가 `levelReviewCount`를 **난이도를 표시한 후기 수**로 재정의해 내려준다(새 필드 없이). 앱에서 이 값을 다른 용도로 쓰는지만 확인한다(API 명세 4).
3. ~~기존 비추천 후기~~ → **확정**: 그대로 둔다(백필 없음). **운영 DB 확인(2026-10-28): 후기 7건 모두 추천해요, 비추천 0건**(작성 기간 2026-08-15~09-01, 비공개 0건) — 난이도가 있는 기존 비추천 후기가 분포에 섞이는 경우가 실제로는 없다.
4. **운영 확인 방법 — 미정.** 당분간 DB 직접 조회로 보고, 추후 관리자 페이지에서 코스별 문제 항목을 볼 수도 있다. 이번 범위에는 조회 API를 넣지 않는다.
