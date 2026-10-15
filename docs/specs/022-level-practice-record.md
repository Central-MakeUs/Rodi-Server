# 레벨별 연습 기록 (추후 구현)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-09-27 | Draft | [스펙 019](019-refinements-1.md) 1번으로 최초 작성 |
| 2026-09-28 | Approved | 019와 함께 리뷰·승인 — 방문 검사 순서(쿨다운 → 레벨)·담기 쿼리의 레벨 조건, `hasReview` 레벨 기준, 지난 레벨 기록 방문은 현재 레벨 기록에 기록(200)·응답 `practiceId`, `level` CHECK 제약, 미방문 사유는 레벨 검사 없이 저장(잠정) |
| 2026-10-01 | Approved | 보류 — 레벨별 연습 기록 화면이 도출되지 않음 |
| 2026-10-15 | Deferred | **추후 구현**으로 019에서 분리. 이슈·브랜치는 아직 만들지 않는다. 화면 도출 후 이 설계를 다시 확인하고 이슈를 연다 |

## 배경 / 목적

데모데이 이후 수정 요청 중 하나로, 연습 기록을 **레벨별로** 저장·표시해 달라는 요청이다. 지금은 (회원, 장소)마다 기록이 한 행이라, 레벨이 올라도 같은 코스의 연습 횟수·상태가 이어진다. 레벨마다 새로 시작하고 지난 레벨 기록은 남기는 것이 목적이다.

관련 스펙: [011 연습 코스 목록](011-practice-course.md) · [012 레벨 진행도](012-level-progress.md) · [013 앱 연동 보완](013-app-integration-refinements.md)

> **추후 구현이다.** 레벨별 연습 기록 화면이 아직 도출되지 않았다. 아래 설계는 2026-09-28에 승인된 내용 그대로이며, 화면이 나오면 다시 확인한 뒤 이슈를 열고 구현한다. 그 사이 다른 마이그레이션이 생기므로 **마이그레이션 번호는 구현 시점에 정한다**(아래 `Vnn`).

## 요구사항

### 기능 요구사항

- 연습 항목은 **(회원, 장소, 레벨)마다 한 행**이다. 레벨이 오르면 같은 코스를 다시 [연습하기]해도 **새 기록**(연습 횟수 0, `PLANNED`)으로 시작하고, 지난 레벨의 기록은 그대로 남는다.
- 내 연습 목록은 **레벨을 골라 조회**한다(`level` 파라미터). 생략하면 **현재 레벨**이다.
- **지난 레벨 기록으로 방문이 오면 현재 레벨 기록에 남긴다.** 레벨이 오른 뒤 예전 기록(`practiceId`)으로 같은 장소를 다녀와도 200이다. 서버가 그 장소의 현재 레벨 기록을 찾거나 새로 만들어 방문을 기록하고, 지난 레벨 기록은 바뀌지 않는다.
- **미방문 사유는 레벨과 관계없이 요청한 기록에 저장한다**(현재 동작 유지). 사유는 레벨·거리·인증에 영향이 없고, "그때 왜 못 갔나"라는 그 기록의 계획에 대한 답이라 그 레벨 기록에 남는 것이 맞다. 지난 레벨 기록은 다시 방문·[연습하기]되지 않으므로 거기 남긴 사유는 지워지지 않는다. **잠정 결정** — 409로 막을지는 화면 도출 후 정한다(미해결 질문 1).
- 담기는 온보딩(레벨 배정)을 마친 회원만 할 수 있다.

### 비기능 요구사항

- **하위호환을 깨는 변경이다** — 연습 목록은 기본이 현재 레벨이라 레벨업한 회원의 지난 기록이 기본 목록에서 빠진다. 앱 업데이트와 함께 나간다.
- 방문 기록의 잠금 순서(**연습 항목 → 회원**, ADR 0012)는 그대로 유지한다.
- 스키마 변경(`Vnn`)은 한 커밋에 담고, 그 커밋 시점에 빌드·기동 가능해야 한다.

## 도메인 모델

### member_practice — 레벨 컬럼 추가 (Vnn)

```sql
ALTER TABLE member_practice ADD COLUMN level VARCHAR(20);

-- 기존 기록은 회원의 현재 레벨 기록으로 본다(레벨 없는 회원은 SEED)
UPDATE member_practice mp
   SET level = COALESCE(m.level, 'SEED')
  FROM member m
 WHERE m.id = mp.member_id;

ALTER TABLE member_practice ALTER COLUMN level SET NOT NULL;

ALTER TABLE member_practice DROP CONSTRAINT uq_member_practice;
ALTER TABLE member_practice
    ADD CONSTRAINT uq_member_practice UNIQUE (member_id, place_id, level);

-- 레벨 값은 enum 밖의 값을 DB에서 막는다(후기 member_level의 ck_review_member_level과 같은 방식)
ALTER TABLE member_practice
    ADD CONSTRAINT ck_member_practice_level
        CHECK (level IN ('SEED', 'ROOKIE', 'OWNER', 'EXPLORER', 'NAVIGATOR'));

DROP INDEX idx_member_practice_recent;
CREATE INDEX idx_member_practice_recent
    ON member_practice (member_id, level, COALESCE(visited_at, created_at) DESC, id DESC);
```

- **`level`** = 이 기록이 속한 레벨. 담을 때 회원의 현재 레벨로 정해지고 이후 바뀌지 않는다(후기의 `member_level` 스냅샷과 같은 성질).
- **백필은 회원의 현재 레벨**로 한다. 기존 기록이 마이그레이션 직후 기본 목록(현재 레벨)에서 사라지지 않게 하기 위함이다. 과거에 어느 레벨에서 방문했는지는 남아 있지 않아 복원할 수 없다.
- 레벨 없는 회원(온보딩 미완료)의 기록은 SEED로 채운다. 온보딩을 마친 회원은 원래 레벨이 비어 있으면 안 되므로 해당 행은 없거나 드물다고 보고 따로 처리하지 않는다.
- **`verified_level`은 유지한다.** 새 기록에서는 항상 `null` 또는 `level`과 같은 값이 된다(방문은 항상 현재 레벨 기록에 남고, 인증 레벨로 **기록의 `level`**을 쓴다 — 승급을 일으킨 방문도 승급 전 레벨 = 기록의 레벨). 백필된 기존 행은 `level`보다 낮은 `verified_level`을 가질 수 있어, 후기 배지 판정(`existsByMemberIdAndPlaceIdAndVerifiedLevel`)을 바꾸지 않으려면 이 컬럼이 필요하다.

**승급을 일으킨 방문** — 스펙 013 규칙 그대로다. SEED 기록으로 방문해 ROOKIE가 되면 그 방문과 인증은 SEED 기록에 남고, ROOKIE에서는 다시 [연습하기]로 새 기록을 만든다.

**엔티티 규칙**

| 메서드 | 규칙 |
|--------|------|
| `MemberPractice.builder().level(...)` | 담을 때 회원 현재 레벨 필수 |
| `isCurrentFor(Level memberLevel)` | `level == memberLevel`. 방문 대상 결정에 쓴다 |
| `markVisited(...)` | 인증 시 `verified_level`에 회원 레벨 대신 **기록의 `level`**을 넣는다(값은 같지만, 드문 동시 승급에서도 `verified_level ≤ level`을 지킨다) |

**서비스 규칙**

- **담기** — 기존 기록 조회(`findByMemberIdAndPlaceId`)와 동시 담기 대비 INSERT(`insertIfAbsent`)에 모두 `level`을 조건으로 더한다. `insertIfAbsent`의 충돌 기준은 새 유니크 `(member_id, place_id, level)`이다.
- **방문 기록의 처리 순서** — 잠금은 **요청 기록 → 현재 레벨 기록 → 회원** 순이다.
  1. 요청한 기록을 잠근다(소유 확인).
  2. 요청한 기록이 **쿨다운**(10분) 안이면 현재 상태로 200. 승급을 일으킨 방문을 앱이 재시도하는 경우다.
  3. 회원 레벨을 읽는다. 요청한 기록이 현재 레벨이 아니면 그 장소의 **현재 레벨 기록을 찾거나 만들고**(`insertIfAbsent` 후 조회) 잠가 방문 대상으로 삼는다. 이 기록도 쿨다운 안이면 200(옮겨 기록한 방문의 재시도).
  4. 회원을 잠그고 방문을 기록한다(누적 거리·승급).
  - 레벨이 없는 회원(온보딩 미완료, 백필된 SEED 기록만 있음)은 옮길 레벨이 없으므로 요청한 기록에 그대로 남긴다.
  - 교착이 없는 이유: 옮기는 방향은 항상 낮은 레벨 → 현재 레벨이고, 연습 항목을 모두 잠근 뒤에 회원을 잠그므로 ADR 0012의 순서(연습 항목 → 회원)를 지킨다.
  - 3에서 읽은 레벨이 4의 잠금 전에 다른 방문으로 오를 수 있다(서로 다른 장소를 동시에 완주한 경우). 이때는 대상 기록에 그대로 기록한다 — 승급 직전 레벨의 방문이라 "승급을 일으킨 방문은 승급 전 레벨 기록에 남는다"는 규칙과 같다.
- **방문 응답** — 실제로 방문이 남은 기록의 `practiceId`를 응답에 추가한다. 옮겨 기록했으면 요청한 id와 다르므로, 앱은 이 값으로 목록을 갱신한다.
- **미방문 사유** — 변경 없다. 레벨을 검사하지 않고 요청한 기록에 저장한다.

## API 명세

| Method | Path | 설명 | 인증 | 변경 |
|--------|------|------|------|------|
| POST | /api/v1/places/{placeId}/practices | 연습 담기 | JWT | 현재 레벨 기록으로 담기, 레벨 없으면 409 |
| GET | /api/v1/members/me/practices | 내 연습 목록 | JWT | `level` 파라미터·응답 `level` 추가 |
| POST | /api/v1/practices/{practiceId}/visits | 방문 기록 | JWT | 지난 레벨 기록이면 현재 레벨 기록에 기록, 응답 `practiceId` 추가 |

```json
// GET /api/v1/members/me/practices?level=SEED&size=20   (level 생략 시 현재 레벨)
{
  "items": [
    {
      "practiceId": 12, "placeId": 1, "placeName": "한강 코스",
      "practiceTypes": ["STRAIGHT", "LANE_CHANGE"],
      "level": "SEED",
      "status": "VISITED", "visitCount": 2, "lastActivityAt": "2026-08-05T18:20:00",
      "hasReview": true, "isDeleted": false
    }
  ],
  "hasNext": false, "nextCursor": null, "totalCount": 1
}
```

- `level`은 유효한 레벨 enum. 잘못된 값이면 400. 레벨 없는 회원이 생략하면 빈 목록이다.
- `totalCount`는 그 레벨의 기록 수다.
- `hasReview`는 **이 기록의 레벨에서** 그 장소에 후기를 썼는지다(`review.member_level = practice.level`). 후기는 레벨마다 새로 쓸 수 있고 수정도 같은 레벨에서만 되므로(`isEditable`) 레벨별 기록과 기준을 맞춘다. 목록은 한 레벨만 조회하므로 `findReviewedPlaceIds`에 레벨 조건 하나만 더한다.

**에러**

| 상황 | 코드 |
|------|------|
| 레벨 없는 회원(온보딩 미완료)이 담기 | 409 `PRACTICE_409_2` 온보딩 후 이용 가능 |

## 완료 조건 (Acceptance Criteria)

- [ ] `Vnn` 적용 후 기존 기록의 `level`이 회원의 현재 레벨(없으면 SEED)로 채워지고, 유니크가 `(member_id, place_id, level)`이 된다.
- [ ] SEED 회원이 담은 코스를 ROOKIE 승급 후 다시 담으면 **새 행**(visitCount 0, PLANNED, level=ROOKIE)이 생기고 SEED 행은 그대로다.
- [ ] 같은 레벨에서 다시 담으면 기존처럼 행을 늘리지 않고 PLANNED로 되돌린다(연습 횟수 유지).
- [ ] 목록은 `level` 생략 시 현재 레벨 기록만, `level=SEED`면 SEED 기록만 반환하고 `totalCount`도 그 레벨 기준이다. 잘못된 `level`은 400.
- [ ] ROOKIE 회원이 SEED 기록으로 방문하면 200이고, 그 장소의 ROOKIE 기록이 생기거나(없으면) 갱신된다. 응답 `practiceId`는 ROOKIE 기록이고 SEED 기록은 바뀌지 않는다.
- [ ] 지난 레벨 기록에도 미방문 사유를 제출할 수 있고(200), 그 기록에만 저장된다.
- [ ] `Vnn` 후 `level`에 enum 밖의 값을 넣으면 DB가 거부한다(`ck_member_practice_level`).
- [ ] 레벨 없는 회원의 담기는 409 `PRACTICE_409_2`.
- [ ] 승급을 일으킨 방문은 승급 전 레벨 기록에 남고, 인증 레벨도 승급 전 레벨이다(기존 규칙 유지).
- [ ] 승급을 일으킨 방문을 쿨다운 안에 재시도하면 409가 아니라 200(현재 상태 그대로)이다.
- [ ] 목록의 `hasReview`는 그 레벨에서 쓴 후기만 본다 — SEED에서 후기를 쓴 장소의 ROOKIE 기록은 `false`.
- [ ] 관련 테스트 통과 (`./gradlew test`).

## 미해결 질문

1. **지난 레벨 기록의 미방문 사유를 409로 막을지** — 화면 도출 후 결정. 현재 설계는 허용(레벨 검사 없이 그 기록에 저장)이다. 지난 기록을 완전히 고정하려면 409 `PRACTICE_409_3`으로 막고 앱이 지난 레벨 목록에서 "안 했어요"를 숨긴다.
2. **화면 도출 후 설계 재확인** — 목록의 레벨 선택 UI, 지난 레벨 기록에서 가능한 동작(방문·담기·사유)이 위 설계와 맞는지.
