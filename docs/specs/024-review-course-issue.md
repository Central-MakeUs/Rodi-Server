# 후기 '별로에요' 코스 문제 항목 · 후기 작성 폼

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-10-27 | Draft | 최초 작성 — 이슈 [#85](https://github.com/Central-MakeUs/Rodi-Server/issues/85), 화면 RV-03(후기 상세정보 1/2) 개편. 문제 항목은 **저장만**(운영 참고용), 선택지는 **서버 폼**으로 내려준다. **선택지 구성은 미결정**(미해결 질문 1) |
| 2026-10-28 | Draft | 미해결 질문 답변 반영 — ① 선택지는 **시안 그대로** 확정(코드 이름 확정) ② 요약 `levelReviewCount`를 **난이도를 표시한 후기 수**로 재정의 ③ 기존 비추천 후기는 그대로 둔다(운영 데이터 확인 중) ④ 운영 확인 방법은 미정(추후 관리자 페이지 가능성) |
| 2026-10-28 | **Approved** | 승인 — 구현 착수. 커밋을 ①DB·도메인(V30·`CourseIssue`·`Review`) ②작성·수정 분기·문제 항목 폼·상세 ③요약 `levelReviewCount` 재정의 세 단계로 나눠 검토받는다 |
| 2026-10-28 | **Implemented** | 구현 완료 — ①V30·`CourseIssue`·`Review` 필드 ②분기 규칙(`Review.rate`)·요청 검증·문제 항목 폼·상세 응답·요약 `levelReviewCount`. ③을 ②에 합쳤다 — 별로에요 후기에서 난이도가 빠지는 순간 분포 합계와 `levelReviewCount`가 어긋나 따로 커밋하면 중간 상태가 틀린다 |
| 2026-10-29 | Draft | **요구사항 정정** — 요청은 문제 항목 폼이 아니라 **후기 작성 화면 전체 폼**이었다. `GET /reviews/course-issue-form`(미배포)을 없애고 `GET /places/{placeId}/review-form`으로 1/2·2/2 단계 문항을 모두 내려준다(제목에 코스명). 스펙 013의 "후기 폼 API 미제공" 결정을 뒤집는다. 난이도·혼잡도·연습 방법 enum에 시안 문구 라벨 추가. 저장·검증·V30은 변경 없음 |
| 2026-10-29 | **Approved** | 승인 — 기타 직접 입력 제출 키는 폼에 담지 않는다(신고 사유 폼과 통일), 초기 선택값 없음(추천 여부만 보임). 기존 브랜치에 커밋 1개로 추가한다 |
| 2026-10-29 | **Implemented** | 후기 작성 폼 구현 — `ReviewFormResponse`·`GET /places/{placeId}/review-form`, 난이도·혼잡도·연습 방법 enum 라벨, `course-issue-form` 삭제 |

## 배경 / 목적

지금은 후기 1단계 화면에서 **추천해요/별로에요 어느 쪽을 골라도 같은 평가 항목**(난이도·혼잡도·주의사항)을 받는다. 그런데 "별로에요"를 고른 사용자에게 필요한 것은 난이도 체감보다 **코스에 무슨 문제가 있었는지**다. 차량 진입 불가, 도로 정보 불일치, 공사·통제처럼 코스 정보 자체가 틀렸다는 신호는 운영 쪽에서 코스를 손볼 근거가 된다.

이번에 "별로에요"를 고르면 **코스 문제 항목(복수 선택)** 을 받도록 분기한다. "추천해요"는 기존 흐름 그대로다.

화면이 추천 여부에 따라 갈리면서 문항 구성·문구를 앱과 서버가 각각 들고 있으면 어긋나기 쉽다. 그래서 **후기 작성 화면 전체(1/2·2/2 단계의 모든 문항·선택지·문구·노출 조건)를 서버가 폼으로 내려준다**(온보딩 폼·코스 등록 폼과 같은 이유 — 앱 배포 없이 문구·구성 변경).

화면 흐름: RV-02-1 연습 완료 재진입 팝업 → "다녀왔어요" → **RV-03 후기 상세정보 1/2(이번 변경)** → RV-03-1 후기 상세정보 2/2(연습 방법·후기 작성, 변경 없음)

| 단계 | 추천해요 | 별로에요 |
|------|----------|----------|
| 1/2 | 난이도 · 혼잡도 · 주의사항 | **코스 문제 항목(복수) + 기타 직접 입력** |
| 2/2 | 연습 방법 · 후기 내용 | 연습 방법 · 후기 내용 |

관련 스펙: [010 장소 후기](010-place-review.md) · [013 앱 연동 보완](013-app-integration-refinements.md)(후기 폼 API 미제공 결정 — **이 스펙이 뒤집는다**) · [018 텍스트 길이](018-text-length-grapheme.md) · [019 1차 보완](019-refinements-1.md)(온보딩 폼 구조)

### 스펙 013 결정 변경

스펙 013은 "난이도·혼잡도·연습 방법은 enum이라 클라이언트가 선택지를 갖고, 문구를 앱 배포 없이 바꿀 이유가 없다"며 후기 폼 API를 두지 않았다. 이번에 1단계가 추천 여부로 갈리고 서버 폼이 필요한 문항(코스 문제 항목)이 생겨, 화면 일부만 서버가 주면 앱이 두 출처를 합쳐야 한다. **화면 전체를 한 폼으로** 내려주는 쪽으로 바꾼다. 수정 화면 프리필은 그대로 후기 상세 조회로 한다(폼은 문항 정의만, 값은 상세).

### 범위 밖

- 코스 문제 항목의 **노출**(후기 목록·요약·장소 상세) — 이번엔 저장만 한다. 화면 요구가 생기면 그때 추가한다.
- 문제 항목 누적에 따른 **자동 처리**(코스 비노출·승인 상태 변경 등)·관리자 조회 화면.
- 후기 목록·요약 응답의 라벨 — 지금처럼 **코드만** 내려간다. 라벨은 폼에만 쓴다.
- 버튼 문구("다음"·"완료")·"다음" 버튼 활성화 판정 — 앱이 폼의 `required`·`showWhen`으로 판단한다.

## 요구사항

### 기능 요구사항

1. **분기 입력**: 후기 작성·수정 요청은 `isRecommended`에 따라 1단계 필수 항목이 달라진다.
   - **추천해요(`true`)**: `difficulty`·`congestion` 필수(기존과 같음). `caution` 선택.
   - **별로에요(`false`)**: `courseIssues` **1개 이상 필수**. `difficulty`·`congestion`은 받지 않는다.
   - 두 경우 모두 `practiceMethod` 필수, `content` 선택(2/2 화면, 기존과 같음).
2. **반대쪽 값은 무시한다**: 별로에요로 온 `difficulty`·`congestion`·`caution`, 추천해요로 온 `courseIssues`·`courseIssueDetail`은 **에러 없이 버리고 저장하지 않는다**. 사용자가 1단계에서 추천해요→별로에요로 바꿔도 앱이 이전 값을 지워 보내야 할 부담을 지지 않게 한다(신고 `detail`을 사유가 OTHER가 아닐 때 무시하는 것과 같은 방침).
3. **복수 선택·중복**: `courseIssues`는 복수 선택이다. 같은 코드가 중복으로 오면 하나로 합친다.
4. **기타 직접 입력**: `OTHER`를 고르면 `courseIssueDetail` **필수**, **최대 30자**(grapheme 기준, 스펙 018). 앞뒤 공백은 잘라내고, 공백뿐이면 없는 것으로 본다. `OTHER`가 없으면 `courseIssueDetail`은 무시한다.
5. **후기 작성 폼**: 후기 작성 화면 전체를 **서버가 폼으로 내려준다**(`GET /places/{placeId}/review-form`). 온보딩 폼처럼 단계 → 문항 → 선택지 구조이고, 추천 여부에 따라 갈리는 문항은 `showWhen`으로 표시한다.
   - 1단계(RV-03): 추천 여부 · 난이도 · 혼잡도 · 주의사항(추천해요일 때) · 코스 문제 항목(별로에요일 때). 2단계(RV-03-1): 연습 방법 · 후기 작성.
   - 문항 `code`는 작성 요청의 필드명, 선택지 `code`는 제출 값 그대로다. 추천 여부는 요청이 boolean이라 선택지 코드를 문자열 `"false"`·`"true"`로 둔다.
   - 1단계 제목에 **코스명**을 넣는다(`{코스명}의 연습은 어떠셨나요?`). 코스명 강조 표시를 위해 `placeName`도 따로 준다. 없는 장소면 404.
   - 문구는 **시안 그대로**다. 난이도·혼잡도·연습 방법 라벨은 enum에 둔다(도메인 모델 참고).
   - `required`는 **문항이 보일 때만** 적용된다(예: 별로에요면 난이도는 숨겨지므로 필수가 아니다). 서버 검증(요구사항 1)과 같은 규칙이다.
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
| GET | /api/v1/places/{placeId}/review-form | 후기 작성 폼 | JWT | **신규** |
| POST | /api/v1/places/{placeId}/reviews | 후기 작성 | JWT | 분기 검증·필드 추가 |
| PUT | /api/v1/reviews/{reviewId} | 후기 수정 | JWT | 분기 검증·필드 추가 |
| GET | /api/v1/reviews/{reviewId} | 후기 상세 | JWT | 응답 필드 추가 |
| GET | /api/v1/places/{placeId}/reviews/summary | 후기 요약 | JWT | `levelReviewCount` 의미 변경 |

**컨트롤러 배치** — `ReviewController`. 후기 작성(`/places/{placeId}/reviews`)과 같은 장소 하위 경로다. 미배포였던 `GET /reviews/course-issue-form`은 **삭제**한다.

### 1. 후기 작성 폼 (신규)

```json
// GET /api/v1/places/1/review-form   (JWT)
// Response data — 시안(RV-03·RV-03-1) 그대로
{
  "title": "후기 남기기",
  "placeName": "북악스카이웨이 드라이브",
  "steps": [
    {
      "order": 1,
      "title": "북악스카이웨이 드라이브의 연습은 어떠셨나요?",
      "questions": [
        {
          "code": "isRecommended", "order": 1, "type": "SINGLE_SELECT", "required": true,
          "title": "이 코스를 다른 초보 운전자에게 추천하시겠어요?",
          "options": [
            { "code": "false", "label": "별로에요", "order": 1, "requiresTextInput": false },
            { "code": "true",  "label": "추천해요", "order": 2, "requiresTextInput": false }
          ]
        },
        {
          "code": "difficulty", "order": 2, "type": "SINGLE_SELECT", "required": true,
          "title": "난이도",
          "showWhen": { "question": "isRecommended", "anyOf": ["true"] },
          "options": [ "VERY_EASY 매우 쉬움", "EASY 쉬움", "NORMAL 보통", "HARD 어려움", "VERY_HARD 매우 어려움" ]
        },
        {
          "code": "congestion", "order": 3, "type": "SINGLE_SELECT", "required": true,
          "title": "혼잡도",
          "showWhen": { "question": "isRecommended", "anyOf": ["true"] },
          "options": [ "QUIET 한산해요", "NORMAL 보통이에요", "CROWDED 복잡해요" ]
        },
        {
          "code": "caution", "order": 4, "type": "TEXT", "required": false,
          "title": "주의사항", "placeholder": "예) 갑자기 나오는 자전거 주의!",
          "showWhen": { "question": "isRecommended", "anyOf": ["true"] }
        },
        {
          "code": "courseIssues", "order": 5, "type": "MULTI_SELECT", "required": true,
          "title": "해당 코스에 어떤 문제가 있나요?",
          "showWhen": { "question": "isRecommended", "anyOf": ["false"] },
          "options": [
            { "code": "VEHICLE_RESTRICTED", "label": "차량 진입이 제한된 장소예요",          "order": 1, "requiresTextInput": false },
            { "code": "ROAD_INFO_MISMATCH", "label": "도로 정보가 실제와 달라요",            "order": 2, "requiresTextInput": false },
            { "code": "CONSTRUCTION",       "label": "공사·통제 등으로 현재 주행할 수 없어요", "order": 3, "requiresTextInput": false },
            { "code": "NOT_FOR_PRACTICE",   "label": "연습 코스로 적합하지 않아요",           "order": 4, "requiresTextInput": false },
            { "code": "OTHER",              "label": "기타",                              "order": 5, "requiresTextInput": true,
              "textInputPlaceholder": "이유를 작성해주세요.", "textInputMaxLength": 30 }
          ]
        }
      ]
    },
    {
      "order": 2,
      "questions": [
        {
          "code": "practiceMethod", "order": 6, "type": "SINGLE_SELECT", "required": true,
          "title": "연습 방법",
          "options": [ "SOLO 혼자 연습", "ACCOMPANIED 동승자와 연습" ]
        },
        {
          "code": "content", "order": 7, "type": "TEXT", "required": false,
          "title": "후기 작성", "placeholder": "자유롭게 후기를 작성해주세요.", "maxLength": 150
        }
      ]
    }
  ]
}
```

- **구조**는 온보딩 폼(스펙 019)과 같다 — 단계(`order`) → 문항(`code`·`order`·`type`·`required`·`title`·`placeholder`·`maxLength`·`showWhen`·`options`). 문항 `order`는 단계를 넘어 1부터 이어진다. 값이 없는 속성은 생략한다(NON_NULL). 2단계는 시안에 제목이 없어 `title`을 생략한다.
- **선택지**는 공용 `FormOption`을 쓴다(신고 사유 폼과 같은 키, 직접 입력이 없으면 `textInput*` 생략). 위 예시에서 `"VERY_EASY 매우 쉬움"`처럼 줄인 것은 `{ "code": "VERY_EASY", "label": "매우 쉬움", "order": 1, "requiresTextInput": false }` 형태다. 순서는 enum 선언 순서.
- **`showWhen`**: `question` 문항에서 `anyOf` 중 하나를 골랐을 때만 보인다. 없으면 항상 보인다. 숨겨진 문항은 `required`여도 입력하지 않는다. 기본 선택값은 없다 — 처음엔 아무것도 고르지 않은 상태라 **추천 여부만 보이고**, 고르는 순간 해당 쪽 문항이 나타난다.
- **기타 직접 입력**은 `OTHER` 선택지의 `requiresTextInput`·`textInput*`로만 표시하고, 제출 키(`courseIssueDetail`)는 폼에 담지 않는다 — 신고 사유 폼(`OTHER` → `detail`)과 같은 방식으로 통일한다(공용 `FormOption` 변경 없음).
- **`maxLength`**는 grapheme 기준(스펙 018)이다. 주의사항은 길이 제한이 없어 생략한다.
- **`placeName`**: 1단계 제목에서 코스명을 강조 표시할 때 쓴다(시안의 보라색 코스명).
- 응답은 회원·후기 상태와 무관하다(레벨 미설정 회원도 받는다 — 작성 시 409는 기존대로). 작성·수정 화면이 같은 폼을 쓰고, 수정 화면의 값은 후기 상세로 채운다(상세의 `placeId`로 폼을 받는다).
- 없는 `placeId`는 **404 `ENTITY_NOT_FOUND`**(후기 작성과 같은 판정).

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

### Difficulty · Congestion · PracticeMethod (변경 — 라벨 추가)

지금은 코드만 있고 화면 문구는 앱이 들고 있었다(스펙 013). 후기 작성 폼이 문구를 내려주므로 **시안 문구를 enum `label`로** 둔다. DB·요청·목록/요약 응답은 그대로 코드만 쓴다.

| enum | 코드 → 라벨 |
|------|-------------|
| `Difficulty` | VERY_EASY 매우 쉬움 · EASY 쉬움 · NORMAL 보통 · HARD 어려움 · VERY_HARD 매우 어려움 |
| `Congestion` | QUIET 한산해요 · NORMAL 보통이에요 · CROWDED 복잡해요 |
| `PracticeMethod` | SOLO 혼자 연습 · ACCOMPANIED 동승자와 연습 |

- 스펙 010의 표기(한산/보통/복잡, 동승자 연습)와 다르다 — **시안 문구를 따른다**(2026-10-29 확정).

### ReviewFormResponse (DTO 신규, `domain.review.dto`)

온보딩 폼(`OnboardingFormResponse`)처럼 **전용 응답**을 둔다. 공용 `FormResponse`는 문항 하나만 담는 구조라 단계·조건부 노출을 담을 수 없다. 선택지는 공용 `FormOption`을 재사용한다(직접 입력 표현이 이미 있다). 문항 정의·문구는 `ReviewFormResponse.of(placeName)` 한 곳에 둔다.

### Review (변경)

- 작성·수정 시 `recommended`에 따라 반대쪽 값을 비운다(요구사항 2·6). 이 규칙은 엔티티가 가진다 — 서비스가 아니라 `Review`가 "별로에요면 난이도 없음"을 보장한다.
- `difficulty`·`congestion`은 nullable이 된다.

### 요약 집계 영향

- 난이도 분포는 값별 `FILTER`로 세므로 **별로에요 후기는 분포에서 자연스럽게 빠진다**. 추천/비추천 수·총 후기 수에는 그대로 잡힌다.
- `topDifficulty`는 분포에서 계산하므로 영향 없다.
- `levelReviewCount`는 분포 합계로 재정의한다(API 명세 4) — 집계 쿼리의 `levelCount`를 `in_level AND difficulty IS NOT NULL`로 바꾼다.

## 완료 조건 (Acceptance Criteria)

**후기 작성 폼**
- [x] `GET /places/{placeId}/review-form`이 2단계를 반환한다 — 1단계(추천 여부·난이도·혼잡도·주의사항·코스 문제 항목), 2단계(연습 방법·후기 작성). 문항 `order`는 1~7이다.
- [x] 1단계 제목이 `{코스명}의 연습은 어떠셨나요?`이고 `placeName`이 함께 내려간다.
- [x] 추천 여부 선택지가 `"false"` 별로에요 → `"true"` 추천해요 순이다.
- [x] 난이도·혼잡도·주의사항은 `showWhen isRecommended=true`, 코스 문제 항목은 `showWhen isRecommended=false`다.
- [x] 난이도·혼잡도·연습 방법 선택지 라벨이 시안 문구(enum `label`)다.
- [x] 코스 문제 항목은 `MULTI_SELECT`이고 `OTHER`만 `requiresTextInput: true`·`textInputMaxLength: 30`이다.
- [x] 후기 작성은 `maxLength: 150`, 주의사항은 `maxLength` 생략.
- [x] 없는 `placeId`는 404.
- [x] `GET /reviews/course-issue-form`이 사라졌다.

**작성**
- [x] 별로에요 + `courseIssues` 1개 이상이면 작성이 성공하고, 문제 항목이 저장되며 난이도·혼잡도는 `null`이다.
- [x] 별로에요로 `difficulty`·`congestion`·`caution`을 함께 보내도 성공하고, 세 값은 저장되지 않는다.
- [x] 추천해요로 `courseIssues`를 보내도 성공하고, 문제 항목은 저장되지 않는다.
- [x] 별로에요 + `courseIssues` 누락·빈 배열은 400.
- [x] 추천해요 + 난이도 또는 혼잡도 누락은 400(기존 동작 유지).
- [x] `OTHER` 선택 + `courseIssueDetail` 누락·공백뿐은 400, 31자는 400, 30자(이모지 포함 grapheme 기준)는 성공.
- [x] `OTHER`가 없으면 `courseIssueDetail`은 저장되지 않는다.
- [x] 같은 코드를 두 번 보내면 하나만 저장된다.

**수정·상세·삭제**
- [x] 별로에요 후기를 추천해요로 수정하면 문제 항목·직접 입력이 지워지고 난이도·혼잡도가 저장된다. 반대 방향도 같다.
- [x] 상세 조회에 `courseIssues`(폼 order 순)·`courseIssueDetail`이 내려가고, 추천해요 후기는 `courseIssues: []`.
- [x] 후기를 삭제하면 문제 항목 행도 함께 지워진다.

**노출·집계**
- [x] 후기 목록·내 후기 목록·요약 응답에 문제 항목이 나가지 않는다.
- [x] 별로에요 후기는 난이도 분포와 `levelReviewCount`에 잡히지 않고, 비추천 수·`totalReviewCount`·`levelCounts`에는 잡힌다.
- [x] `levelReviewCount`는 항상 `difficultyCounts` 합계와 같다.
- [x] 선택 레벨 후기가 모두 새 별로에요 후기면 `levelReviewCount: 0`이고 `topDifficulty`가 생략된다.

**DB**
- [x] V30 후 추천 후기에 난이도·혼잡도 `NULL`을 넣으면 DB가 거부한다(`ck_review_recommended_ratings`).
- [x] `review_course_issue.issue`에 enum 밖의 값을 넣으면 DB가 거부한다.
- [x] 기존 후기(추천·비추천 모두)가 V30 적용 후 그대로 조회된다.
- [x] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

1. ~~코스 문제 항목 선택지~~ → **확정(2026-10-28)**: 시안 그대로 5개, 기타 직접 입력 30자. 코드 이름은 API 명세 1의 값으로 확정한다(`VEHICLE_RESTRICTED`·`ROAD_INFO_MISMATCH`·`CONSTRUCTION`·`NOT_FOR_PRACTICE`·`OTHER`). 이후 항목을 추가하면 enum + CHECK 교체 마이그레이션, 문구·순서만 바뀌면 마이그레이션 없음.
2. ~~요약 모수~~ → **확정**: (a) 방향 — 분포 합계를 모수로 쓴다. 서버가 `levelReviewCount`를 **난이도를 표시한 후기 수**로 재정의해 내려준다(새 필드 없이). 앱에서 이 값을 다른 용도로 쓰는지만 확인한다(API 명세 4).
3. ~~기존 비추천 후기~~ → **확정**: 그대로 둔다(백필 없음). **운영 DB 확인(2026-10-28): 후기 7건 모두 추천해요, 비추천 0건**(작성 기간 2026-08-15~09-01, 비공개 0건) — 난이도가 있는 기존 비추천 후기가 분포에 섞이는 경우가 실제로는 없다.
4. **운영 확인 방법 — 미정.** 당분간 DB 직접 조회로 보고, 추후 관리자 페이지에서 코스별 문제 항목을 볼 수도 있다. 이번 범위에는 조회 API를 넣지 않는다.
