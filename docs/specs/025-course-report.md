# 코스 신고 · '별로에요' 문제 항목 신고 이관

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-11-08 | Draft | 최초 작성 — 코스 신고 API·신고 폼 API 신규, 스펙 024의 '별로에요' 코스 문제 항목 저장처를 **후기 → 신고**로 이관 |
| 2026-11-08 | **Approved** | 승인 — 이슈 [#87](https://github.com/Central-MakeUs/Rodi-Server/issues/87). 기존 주차장 후기는 그대로 둔다. 커밋을 ①V31·신고 도메인 ②코스 신고·신고 폼 API ③후기 문제 항목 이관(V32)·주차장 차단 단계로 나눠 검토받는다 |

## 배경 / 목적

코스 상세에서 **신고하기**로 코스 문제(진입 제한·도로 정보 불일치·공사 통제 등)를 알리는 화면이 생겼다. 선택지는 후기 작성의 '별로에요' 문항(스펙 024 `courseIssues`)과 **문구·구성이 똑같다**.

기획 확정: **'별로에요'로 고른 문제 항목도 신고로 처리**해 관리자가 한 곳에서 본다. 그래서

- 신고 폼과 후기 폼의 문제 항목 문항을 **같은 정의(DTO 생성 메서드)** 로 만든다 — 두 화면 문구가 어긋나지 않는다.
- 문제 항목은 **신고 테이블에만** 저장한다. 후기 테이블은 추천 여부·연습 방법·후기 내용(추천해요면 난이도·혼잡도·주의사항)만 갖는다.

화면

| 화면 | 진입 | 문항 | 버튼 |
|------|------|------|------|
| 신고하기 | 코스 상세 → 신고하기 | 해당 코스에 어떤 문제가 있나요?(복수, 기타 30자) | 제출 → "신고가 접수되었습니다." 팝업 → 확인 시 코스 상세 |
| 후기 남기기 1/2 | '별로에요' 선택 | 같은 문항 | 다음 |

관련 스펙: [010 장소 후기](010-place-review.md)(후기 신고) · [014 코스 등록](014-course-registration.md)(미승인 노출) · [018 텍스트 길이](018-text-length-grapheme.md) · [024 후기 코스 문제 항목](024-review-course-issue.md)(**저장처를 이 스펙이 바꾼다**)

### 범위

- 신고 폼 조회·코스 신고(신규)
- 후기 작성·수정·삭제·상세의 문제 항목 저장처 변경(**API 요청·응답 형태는 그대로**)
- 후기 작성 폼(`GET /places/{placeId}/review-form`) — **응답 그대로**, 문제 항목 문항만 신고 폼과 같은 생성 메서드를 쓰도록 내부 정리
- 주차장 후기 작성 차단

### 범위 밖

- **관리자 신고 조회 API** — 관리자 인증(역할) 도입 후 별도 스펙. 그 전에 열면 로그인한 누구나 남의 신고를 본다. 이번엔 쌓기만 하고, 출처 구분(`review_id`)은 지금 넣어 둔다.
- 신고 누적 자동 처리(코스 비노출 등)·신고 처리 상태(`status`)
- 신고 완료 팝업·화면 이동 — 클라이언트 처리(성공 응답만)

## 요구사항

### 기능 요구사항

1. **신고 폼 조회**: 문제 항목 문항 하나를 공용 `FormResponse`로 내려준다(복수 선택·필수·선택지 5종·기타 placeholder/30자). 문구는 후기 폼 `courseIssues` 문항과 같다.
2. **코스 신고(직접)**
   - 문제 항목 **1개 이상 복수 선택**, 중복 코드는 하나로 합친다.
   - `OTHER`면 `detail` 필수, 최대 **30자**(grapheme), 앞뒤 공백 제거·공백뿐이면 미입력. `OTHER`가 없으면 `detail`은 무시.
   - **중복 신고 멱등** — 같은 회원·같은 코스의 직접 신고는 1건. 재신고는 200, 첫 신고 유지. 신고자(member) 행을 잠그고 존재 확인 후 저장해, 같은 회원의 동시 요청이 unique에 걸려 500이 나지 않게 한다(후기 신고가 후기 행을 잠그는 것과 같은 방식).
   - **본인이 등록한 코스는 신고 불가**(400).
   - 대상: 상세를 볼 수 있는 코스만. 삭제 → `COURSE_404_2`, 없는 id·주차장 id·남의 미승인 코스 → `COURSE_404_1`(미승인 존재를 숨기는 상세 조회와 같은 판단).
3. **'별로에요' 후기 → 신고**
   - 후기 작성·수정 요청 형태·검증은 스펙 024 그대로(`courseIssues` 1개 이상 필수, `OTHER`면 `courseIssueDetail` 필수 30자, 반대쪽 값 무시).
   - 후기에는 `isRecommended=false`·`practiceMethod`·`content`만 저장하고, `courseIssues`·`courseIssueDetail`은 **그 후기에 연결된 신고 1건**(`course_report.review_id`)으로 저장한다. 신고자 = 후기 작성자.
   - 후기 하나당 신고 최대 1건. 같은 코스에 '별로에요' 후기를 여러 번 쓰면 신고도 후기마다 생긴다(직접 신고 중복 제한과 별개).
   - 본인 코스에 쓴 '별로에요' 후기도 신고로 저장한다 — 문제 항목을 남길 곳이 신고뿐이고, 후기 상세 프리필에도 필요하다.
4. **후기 수정**: 별로에요→추천해요면 연결 신고 삭제, 추천해요→별로에요면 생성, 별로에요→별로에요면 문제 항목·`detail` 교체.
5. **후기 삭제**: 연결 신고도 함께 삭제(FK `ON DELETE CASCADE`).
6. **후기 상세**: `courseIssues`·`courseIssueDetail` 응답은 그대로 — 연결 신고에서 읽는다.
7. **주차장 후기 차단**: 주차장 id로 후기 작성·후기 작성 폼 조회 시 **400 `REVIEW_400_2`** "코스에만 후기를 남길 수 있습니다." 이미 있는 주차장 후기는 그대로 둔다(목록·요약·수정·삭제 영향 없음).

### 비기능 요구사항

- JWT 필수.
- 문제 항목 코드값은 DB CHECK로도 막는다(V17·V30과 같은 방침).

## API 명세

| Method | Path | 설명 | 인증 | 변경 |
|--------|------|------|------|------|
| GET | /api/v1/courses/report-form | 코스 신고 폼 | JWT | **신규** |
| POST | /api/v1/courses/{courseId}/report | 코스 신고 | JWT | **신규** |
| POST | /api/v1/places/{placeId}/reviews | 후기 작성 | JWT | 저장처 변경, 주차장 400 |
| PUT | /api/v1/reviews/{reviewId} | 후기 수정 | JWT | 저장처 변경 |
| DELETE | /api/v1/reviews/{reviewId} | 후기 삭제 | JWT | 연결 신고 삭제 |
| GET | /api/v1/reviews/{reviewId} | 후기 상세 | JWT | 읽는 곳 변경(응답 동일) |
| GET | /api/v1/places/{placeId}/review-form | 후기 작성 폼 | JWT | 주차장 400(응답 동일) |

**컨트롤러 배치**: 신고 두 개는 단발 엔드포인트라 소유 컨트롤러 **`CourseController`**(`/courses/registration-form` 옆, 후기 신고가 `ReviewController`에 있는 것과 같은 판단).

### 1. 신고 폼

```json
// GET /api/v1/courses/report-form   (JWT)
{
  "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.",
  "data": {
    "questionId": "courseIssues",
    "type": "MULTI_SELECT",
    "title": "해당 코스에 어떤 문제가 있나요?",
    "required": true,
    "options": [
      { "code": "VEHICLE_RESTRICTED", "label": "차량 진입이 제한된 장소예요", "order": 1, "requiresTextInput": false },
      { "code": "ROAD_INFO_MISMATCH", "label": "도로 정보가 실제와 달라요", "order": 2, "requiresTextInput": false },
      { "code": "CONSTRUCTION", "label": "공사·통제 등으로 현재 주행할 수 없어요", "order": 3, "requiresTextInput": false },
      { "code": "NOT_FOR_PRACTICE", "label": "연습 코스로 적합하지 않아요", "order": 4, "requiresTextInput": false },
      { "code": "OTHER", "label": "기타", "order": 5, "requiresTextInput": true,
        "textInputPlaceholder": "이유를 작성해주세요.", "textInputMaxLength": 30 }
    ]
  }
}
```

**재사용 방식**: `CourseIssue`에 문항 정의를 한 곳에 둔다.

- `CourseIssue.QUESTION_TITLE`("해당 코스에 어떤 문제가 있나요?")·`CourseIssue.formOptions()`(order 순 `FormOption` 목록)
- 신고 폼: `new FormResponse("courseIssues", MULTI_SELECT, QUESTION_TITLE, null, true, formOptions())`
- 후기 폼 `courseIssues` 문항: 같은 `QUESTION_TITLE`·`formOptions()` 사용(지금 인라인 stream 제거)

후기 폼은 단계·`showWhen`이 있는 별도 구조(`ReviewFormResponse.Question`)라 `FormResponse` 객체 자체를 끼워 넣지는 않고, **문항을 만드는 재료를 공유**한다. 응답 JSON은 둘 다 지금과 같다.

### 2. 코스 신고

```json
// POST /api/v1/courses/12/report   (JWT)
// Request
{ "courseIssues": ["ROAD_INFO_MISMATCH", "OTHER"], "detail": "주말엔 입구가 잠겨 있어요" }
// Response 200
{ "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.", "data": null }
```

| 상황 | 응답 |
|------|------|
| `courseIssues` 누락·빈 배열·알 수 없는 코드 | 400 `COMMON_400` |
| `OTHER` + `detail` 누락/공백, `detail` 30자 초과 | 400 `COMMON_400` |
| 본인 등록 코스 | 400 `COURSE_400_1` "본인이 등록한 코스는 신고할 수 없습니다." |
| 없는 id·주차장 id·남의 미승인 코스 | 404 `COURSE_404_1` |
| 삭제된 코스 | 404 `COURSE_404_2` |
| 이미 직접 신고한 코스 | 200(첫 신고 유지) |

필드명 `courseIssues`는 후기 요청과 맞춘다. 기타 입력은 후기 신고(`detail`)와 맞춰 `detail`로 한다.

## 도메인 모델 (마이그레이션 V31·V32)

`CourseIssue` enum은 **`domain.review.entity` → `domain.place.entity`** 로 옮긴다(코스의 문제 항목이고 이제 신고가 소유). 코드값·라벨은 그대로.

### course_report (신규)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| id | bigint PK | Y | |
| course_id | bigint FK→course(place_id) | Y | `ON DELETE CASCADE` |
| reporter_id | bigint FK→member | Y | `ON DELETE CASCADE` |
| review_id | bigint FK→review, **unique** | N | '별로에요' 후기에서 온 신고면 그 후기(null=직접 신고). `ON DELETE CASCADE` |
| detail | text | N | 기타 직접 입력(`OTHER`일 때만). 길이는 애플리케이션(grapheme) |
| created_at·updated_at | timestamp | Y | `BaseEntity` |

- **직접 신고 1건 제한**: partial unique index `(course_id, reporter_id) WHERE review_id IS NULL`.
- `review_id` unique — 후기당 신고 1건.
- 출처는 `review_id` 유무로 구분한다(관리자 조회 때 사용).

### course_report_issue (신규)

| 필드 | 타입 | 설명 |
|------|------|------|
| course_report_id | bigint FK→course_report | `ON DELETE CASCADE` |
| issue | varchar(30) | `CHECK (issue IN ('VEHICLE_RESTRICTED','ROAD_INFO_MISMATCH','CONSTRUCTION','NOT_FOR_PRACTICE','OTHER'))` |

- PK `(course_report_id, issue)`. 엔티티에서는 `@ElementCollection`(코스 `course_practice_type`과 같은 방식).

### review 정리

- `Review.courseIssues`·`courseIssueDetail` 필드 삭제. `Review.rate`의 별로에요 분기는 난이도·혼잡도·주의사항을 비우는 것만 남는다.
- 문제 항목 생성·교체·삭제는 `ReviewService`가 `CourseReportRepository`로 처리한다(후기 작성의 인증 이력 조회처럼 도메인 간 참조).

### 마이그레이션

커밋 단계와 맞춰 둘로 나눈다 — 옛 테이블을 먼저 지우면 ③ 전까지 `Review` 매핑이 깨진다.

1. **V31** `course_report`·`course_report_issue` 생성
2. **V32** 기존 데이터 이관 — `review_course_issue`가 있는 후기마다 `course_report`(course_id=후기 place_id, reporter=작성자, review_id, detail=`course_issue_detail`) + 항목 행. V30은 develop(pi2)에만 배포됐고 운영(main)엔 아직 없어 운영 이관 데이터는 0건이다. 주차장 후기에 달린 문제 항목은 신고로 옮길 수 없어 버린다(개발 DB 테스트 데이터만 해당).
3. **V32** `review_course_issue` 삭제, `review.course_issue_detail` 삭제

### ERD 갱신

`docs/erd.md`: `course_report`·`course_report_issue` 추가, `review_course_issue`·`review.course_issue_detail` 제거, 스펙 024 문서에 저장처 변경 기록.

## 완료 조건 (Acceptance Criteria)

- [ ] `GET /courses/report-form`이 위 예시와 같고, 후기 폼의 `courseIssues` 문항 제목·선택지와 동일하다(같은 메서드로 생성)
- [ ] 후기 작성 폼 응답이 변경 전과 같다(주차장 제외)
- [ ] 직접 신고: 항목 복수 저장·중복 합침, `OTHER` 검증(필수·30자), `OTHER` 없으면 `detail` 미저장
- [ ] 직접 재신고는 200이고 첫 신고 유지
- [ ] 본인 코스 400 `COURSE_400_1`, 없는 id·주차장·남의 미승인 404 `COURSE_404_1`, 삭제 404 `COURSE_404_2`
- [ ] '별로에요' 후기 작성 시 `course_report`(review_id 연결)에 저장되고 review 테이블엔 문제 항목이 없다
- [ ] 직접 신고한 코스에 '별로에요' 후기를 써도 둘 다 저장된다
- [ ] 수정: 별로→추천 신고 삭제, 추천→별로 신고 생성, 별로→별로 교체
- [ ] 후기 삭제 시 연결 신고도 삭제
- [ ] 후기 상세 `courseIssues`·`courseIssueDetail` 응답 유지
- [ ] 주차장 후기 작성·폼 조회 400 `REVIEW_400_2`
- [ ] V32: 기존 `review_course_issue` 데이터가 `course_report`로 이관되고 옛 테이블·컬럼이 삭제된다
- [ ] Swagger 신고 폼 예시, `docs/erd.md`·스펙 024 갱신
- [ ] `./gradlew test` 통과

## 미해결 질문

1. ~~**주차장 후기 차단 시 기존 주차장 후기**~~ → 그대로 둔다(승인 시 확정).
2. **신고 누적 자동 비공개**: 후기는 5명 누적 시 숨기지만 코스는 접수·보관만 한다(악의적 신고로 코스가 사라지는 것 방지).
3. **관리자 신고 조회**: 관리자 인증 스펙 → 조회 API 스펙 순으로 진행 예정.
