# 닉네임 변경 · 검증 (길이 · 금칙어 · 중복)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-10-08 | Draft | 최초 작성 — 이슈 [#75](https://github.com/Central-MakeUs/Rodi-Server/issues/75), 설계 결정 [ADR 0014](../adr/0014-nickname-validation-and-profanity-filter.md) |
| 2026-10-08 | Draft | 미해결 질문 1·2 확정 — PATCH는 **보낸 필드만 수정**(생략=변경 없음, `drivingGoal` null/빈값=삭제), 중복은 **대소문자 무시**(`lower(nickname)` 유니크 인덱스) |
| 2026-10-08 | **Approved** | 승인 — 금칙어 단어 목록은 스펙에서 빼고 분류 기준·출처 표기만 유지. LDNOOBW는 커밋 `5faf2ba` 기준으로 **한 번 가공해 커밋**(A안) |
| 2026-10-08 | Implemented | 구현 완료 — V28·후보 풀 이관, `ProhibitedWords`·`NicknamePolicy`, `NicknameService`·확인 API, PATCH 닉네임. 허용 목록은 **위치 기반**(덮인 일치만 무시)으로 정정, 부분 수정은 **setter 호출 여부로 생략/null 구분**(구현 노트) |
| 2026-10-08 | Implemented | 스펙 번호 019 → **020** — #74의 `019-refinements-1.md`가 먼저 머지되어 번호 충돌을 피함 |
| 2026-10-15 | Implemented | develop(#77 머지) 위로 rebase — #77이 V27(`sort_tags` 이름 변경)을 먼저 써서 닉네임 이관 마이그레이션을 **V28**로 변경 |
| 2026-10-22 | Draft | **닉네임 변경 30일 제한** 추가(이슈 [#80](https://github.com/Central-MakeUs/Rodi-Server/issues/80)) — 바꾼 시각부터 정확히 30일 동안 다시 바꿀 수 없다. 첫 변경·같은 값·대소문자만 바꾸기는 제한에 세지 않는다. 마이페이지에 `nicknameChangeableAt` 추가 |
| 2026-10-22 | **Approved** | 변경 제한 승인 — 구현 시작 |
| 2026-10-22 | Approved | 다음 변경 가능 시각을 **PATCH 성공·`MEMBER_400_5` 실패 응답·확인 API·마이페이지** 네 곳에 `nicknameChangeableAt`으로 통일해 준다 — 앱이 확인 API를 안 써도 변경 요청 응답만으로 안내 가능. 실패 응답 data는 `BusinessException.getData()`를 재정의한 전용 예외로(ADR 0013 보완) |

## 배경 / 목적

닉네임은 가입 시 990개 후보 풀에서 **자동 부여만** 되고, 사용자가 바꿀 방법이 없다([스펙 004](004-onboarding.md)에서 "닉네임 변경은 다음 업데이트"로 보류). 이번에 마이페이지에서 닉네임을 바꿀 수 있게 한다.

자유 입력을 열면 욕설·비속어·운영자 사칭이 들어올 수 있으므로 **형식·금칙어·중복 검증**을 함께 넣는다. 금칙어 출처와 판정 구조는 [ADR 0014](../adr/0014-nickname-validation-and-profanity-filter.md)에서 정했다.

닉네임의 단어 구분은 **공백 대신 `_`** 로 바꾼다. 기존 닉네임("차근차근 토끼")도 이관한다("차근차근_토끼").

### 범위 밖

- 기존 닉네임의 금칙어 소급 검사 — 후보 풀만 존재하므로 불필요하다.
- 운영 중 금칙어 즉시 추가(관리 화면·DB 테이블) — 재배포로 반영한다.
- 닉네임 외 텍스트(후기·운전 목표 등)의 금칙어 필터링.

## 요구사항

### 기능 요구사항

1. **닉네임 변경**: `PATCH /api/v1/members/me`에 `nickname`을 보내면 검증을 통과한 경우 바뀐다.
2. **사용 가능 여부 확인**: 저장하지 않고 같은 검증만 돌려, 앱이 입력 중에 "사용 가능 / 불가 사유"를 보여줄 수 있게 한다.
3. **형식 규칙**
   - 입력값을 **NFC로 정규화**하고 **앞뒤 공백을 제거**한 뒤 검증·저장한다.
   - 길이 **2~10자**. 허용 문자가 아래로 제한되므로 grapheme 수와 `String.length()`가 같다.
   - 허용 문자: **완성형 한글(가–힣), 영문 대소문자, 숫자, `_`**.
   - `_`는 **처음·끝에 올 수 없고**, **연속(`__`)도 안 된다**.
   - 공백은 허용하지 않는다. 서버가 `_`로 **자동 변환하지 않고 거부**한다.
   - 자모 단독(ㅋㅋ·ㅅㅂ), 이모지, 그 밖의 특수문자는 거부한다.
4. **금칙어**: 정규화한 닉네임이 금칙어에 걸리면 거부한다(판정 방식은 [도메인 모델](#금칙어-판정)).
5. **중복 불가**: 다른 회원이 쓰고 있는 닉네임이면 거부한다. 영문은 **대소문자를 무시**하고 비교한다(`Drive_Kim`과 `drive_kim`은 같은 닉네임). **본인의 현재 닉네임**은 허용한다(변경 없음으로 처리). 본인 닉네임의 대소문자만 바꾸는 것(`kim` → `Kim`)도 허용한다.
6. **판정 순서와 사유**: (지금 닉네임과 대소문자만 다르거나 같으면 제한 없이 통과) → **변경 제한** → 형식 → 금칙어 → 중복 순으로 검사하고, **처음 걸린 사유 하나**를 돌려준다. 두 API가 같은 판정 로직과 사유 코드를 쓴다. 변경 제한을 맨 앞에 두는 이유: 어차피 바꿀 수 없는데 형식 오류를 먼저 알려주면 헛수고가 된다.
7. **부분 수정**: `PATCH /members/me`는 **보낸 필드만 수정**한다. 필드를 생략하면 그대로 두고, `drivingGoal`을 `null`이나 빈 문자열로 보내면 운전 목표를 지운다.
8. **기존 닉네임 이관**: 기존 회원 닉네임과 후보 풀의 공백을 `_`로 바꾼다. 신규 가입자는 `_` 형식 닉네임을 받는다.
9. **변경 제한(30일)** — 2026-10-22 추가
   - 닉네임을 바꾸면 **바꾼 시각부터 정확히 30일** 동안 다시 바꿀 수 없다(예: 10/22 15:00에 바꾸면 11/21 15:00부터 가능). 날짜 단위로 자르지 않는다.
   - **첫 변경은 바로 가능**하다. 가입 때 자동 부여된 닉네임을 처음 바꾸는 것은 제한하지 않는다. 기존 회원도 모두 바로 한 번 바꿀 수 있다.
   - **같은 값**(지금 닉네임과 똑같음)을 보내면 변경이 아니다. 제한 중에도 200이고 아무것도 바뀌지 않으며, 30일도 다시 시작하지 않는다.
   - **대소문자만 다른 값**(`kim` → `Kim`)은 **바꾸되 변경으로 세지 않는다.** 제한 중에도 바로 바뀌고, 30일을 새로 시작하거나 연장하지 않는다. 중복 판정(대소문자 무시)에서 같은 닉네임으로 보기 때문이다.
   - 제한 중에 다른 닉네임으로 바꾸려 하면 **400 `MEMBER_400_5`**. `drivingGoal`을 함께 보냈어도 **아무것도 저장하지 않는다**(기존 원자성 규칙).
   - 앱이 "N일 후 변경 가능"을 보여줄 수 있도록 다음 변경 가능 시각 **`nicknameChangeableAt`** 을 네 곳에서 같은 이름으로 준다(지금 바꿀 수 있으면 `null`). 앱은 확인 API를 쓰지 않아도 변경 요청 응답만으로 안내할 수 있다.
     | 응답 | 위치 |
     |---|---|
     | PATCH 성공(200) | `data.nicknameChangeableAt` (`data.nickname`과 함께) |
     | PATCH 실패 `MEMBER_400_5`(400) | `data.nicknameChangeableAt` — 이 에러만 data를 싣는다. 다른 에러는 `data: null` 그대로 |
     | 확인 API | `data.nicknameChangeableAt` — 사유가 `MEMBER_400_5`일 때만, 그 밖에는 `null` |
     | 마이페이지 | `data.nicknameChangeableAt` |
   - **확인 API**도 제한 중이면 `available: false`, `reason: MEMBER_400_5`를 준다. 같은 값·대소문자만 다른 값은 제한 중에도 `available: true`다.
   - 탈퇴 후 복구하면 변경 기록은 유지된다. 재가입은 새 회원이라 제한이 없다.

### 비기능 요구사항

- 두 API 모두 **JWT 필요**. 확인 API도 인증을 요구한다. 본인 닉네임 판정에 회원 식별이 필요하고, 비로그인 대량 조회(닉네임 열거)를 막는다.
- 금칙어 파일은 **기동 시 메모리에 불변으로 로드**한다(후보 풀과 같은 방식). 판정마다 파일·DB를 읽지 않는다.
- 동시에 같은 닉네임으로 바꾸는 요청은 `lower(nickname)` 유니크 인덱스가 막고, 그 경우도 **409 중복**으로 응답한다(500이 아니다).
- 응답 메시지에 **어떤 금칙어에 걸렸는지 노출하지 않는다**.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| PATCH | `/api/v1/members/me` | 회원 부분 수정 — `nickname` 필드 추가 | JWT |
| GET | `/api/v1/members/nickname/availability?nickname=` | 닉네임 사용 가능 여부 확인 | JWT |

두 엔드포인트 모두 `MemberController`에 둔다. 확인 API는 단발 엔드포인트라 소유 컨트롤러에 둔다는 기준(CLAUDE.md)을 따른다.

### 사유 코드

두 API가 공유한다. PATCH는 에러 응답의 `code`로, 확인 API는 `data.reason`으로 같은 값을 준다.

| 사유 | 에러 코드 | HTTP (PATCH) | 메시지 |
|------|-----------|--------------|--------|
| 길이 위반 | `MEMBER_400_2` | 400 | 2자 이상 10자 이하만 사용 가능해요. |
| 허용되지 않은 문자·`_` 위치 | `MEMBER_400_3` | 400 | 닉네임은 한글·영문·숫자·_만 사용할 수 있어요. (_는 처음·끝·연속 사용 불가) |
| 금칙어 | `MEMBER_400_4` | 400 | 사용할 수 없는 단어가 포함되어 있어요. |
| 중복 | `MEMBER_409_3` | 409 | 이미 사용 중인 닉네임이에요. |
| 변경 제한(30일) | `MEMBER_400_5` | 400 | 닉네임은 30일에 한 번만 변경할 수 있어요. |

- `MEMBER_400_1`(자기 차단)·`MEMBER_409_2`(이미 온보딩)가 쓰이고 있고, `MEMBER_409_1`은 [ADR 0013](../adr/0013-withdrawal-locked-as-status.md)에서 제거돼 재사용하지 않는다.
- 형식 위반을 Bean Validation(`COMMON_400` + 필드 맵)이 아니라 **도메인 에러 코드**로 주는 이유: 확인 API와 사유를 맞추고, 앱이 코드만으로 안내 문구를 고를 수 있게 하기 위해서다.

### 1. 닉네임 변경 — `PATCH /api/v1/members/me`

```json
// Request
{ "nickname": "초보_탈출" }

// Response 200 — 현재 닉네임과 다음 변경 가능 시각(2026-10-22 추가, 이전엔 data null)
{
  "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.",
  "data": { "nickname": "초보_탈출", "nicknameChangeableAt": "2026-11-21T15:00:00" }
}

// Response 400 — 바꾼 뒤 30일 이내(2026-10-22 추가). 이 에러만 data를 싣는다
{
  "isSuccess": false, "code": "MEMBER_400_5", "message": "닉네임은 30일에 한 번만 변경할 수 있어요.",
  "data": { "nicknameChangeableAt": "2026-11-21T15:00:00" }, "traceId": "..."
}

// Response 409 — 다른 회원이 사용 중
{ "isSuccess": false, "code": "MEMBER_409_3", "message": "이미 사용 중인 닉네임이에요.", "data": null }
```

- `nickname`을 **보내지 않으면** 닉네임은 바뀌지 않는다.
- `nickname: null`이나 빈 문자열은 닉네임 삭제가 아니라 **길이 위반(`MEMBER_400_2`)** 이다. 닉네임은 지울 수 없다.
- 기존 `drivingGoal`과 같이 보낼 수 있다. 둘 중 하나라도 검증에 실패하면 **아무것도 저장하지 않는다**.
- **보낸 필드만 수정한다.** 요청 본문에서 필드의 생략과 `null`을 구분한다.

| `drivingGoal` 요청 값 | 동작 |
|---|---|
| 생략 | 변경 없음 |
| `null` 또는 `""` | 운전 목표 삭제 (기존 동작) |
| 문자열 | 변경 (기존 검증: 30 grapheme) |

  `nickname`도 생략하면 변경 없음이다. 둘 다 생략한 `{}`는 아무것도 바꾸지 않고 200이다.
- **하위 호환**: 지금 앱이 운전 목표 화면에서 `drivingGoal`을 항상 담아 보낸다면 동작은 같다. 앱이 목표를 지울 때 **필드를 생략**하는 방식이었다면 이제는 지워지지 않으므로, 배포 전에 앱 팀과 확인한다.

### 2. 사용 가능 여부 확인 — `GET /api/v1/members/nickname/availability`

```json
// GET /api/v1/members/nickname/availability?nickname=초보_탈출
// Response 200 — 사용 가능
{
  "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.",
  "data": { "nickname": "초보_탈출", "available": true, "reason": null, "message": null }
}

// GET /api/v1/members/nickname/availability?nickname=차근차근_토끼
// Response 200 — 사용 불가
{
  "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.",
  "data": {
    "nickname": "차근차근_토끼",
    "available": false,
    "reason": "MEMBER_409_3",
    "message": "이미 사용 중인 닉네임이에요."
  }
}
```

- 규칙 위반은 모두 **200 + `available: false`** 로 준다. "이 닉네임을 쓸 수 있는가"라는 질문에 대한 정상적인 답이기 때문이다.
- `data.nickname`은 정규화(NFC·앞뒤 공백 제거)를 거친 값이다. PATCH에 그대로 보내면 같은 판정을 받는다.
- 본인의 현재 닉네임은 `available: true`다.
- `nickname` 파라미터가 **없으면** 400 `COMMON_400`이다(기존 파라미터 누락 처리). 빈 문자열은 길이 위반으로 판정한다.
- 확인 시점과 저장 시점 사이에 다른 사람이 먼저 가져갈 수 있다. 최종 판정은 PATCH가 한다.

## 도메인 모델

스키마 변경은 닉네임 값 이관(V28)과 변경 시각 컬럼 추가(V29, 2026-10-22)다. 새 테이블은 없다.

### 구성요소 (`domain/member`)

| 구성요소 | 위치 | 역할 |
|----------|------|------|
| `NicknamePolicy` | `policy` | 정규화·형식·금칙어 판정. 결과로 위반 사유(또는 통과)를 돌려준다. DB를 모른다. |
| `ProhibitedWords` | `policy` | 금칙어 세 파일을 기동 시 로드하고 `contains(normalized)`를 제공한다. |
| `NicknameService` | `service` | `NicknamePolicy` + 대소문자 무시 중복 조회(본인 제외) + 본인 예외를 묶어 두 API에 제공한다. |
| `MemberUpdateRequest` | `dto` | 필드 생략과 `null`을 구분할 수 있게 바꾼다([구현 노트](#구현-노트)). |
| `NicknameAvailabilityResponse` | `dto` | `nickname`·`available`·`reason`·`message` |
| `MemberErrorCode` | `exception` | 위 사유 코드 4개 추가. 변경 제한 `MEMBER_400_5` 추가(2026-10-22) |
| `Member` | `entity` | `nicknameChangedAt`(마지막 사용자 변경 시각)과 `nicknameChangeableAt(now)`(다음 변경 가능 시각, 지금 가능하면 null) — 2026-10-22 |
| `NicknamePolicy` | `policy` | 변경 제한 기간 상수 `CHANGE_COOLDOWN = 30일` — 2026-10-22 |
| `MyPageResponse` | `dto` | `nicknameChangeableAt` 추가 — 2026-10-22 |
| `MemberUpdateResponse` | `dto` | PATCH 성공 응답 — `nickname`·`nicknameChangeableAt` (이전엔 data null) — 2026-10-22 |
| `NicknameAvailabilityResponse` | `dto` | `nicknameChangeableAt` 추가(사유가 30일 제한일 때만) — 2026-10-22 |
| `NicknameChangeCooldownException` | `exception` | `MEMBER_400_5` 전용 예외. `getData()`로 `{ nicknameChangeableAt }`을 에러 응답 data에 싣는다 — 2026-10-22 |

- `MemberProfileService.update`가 `NicknameService`로 검증한 뒤 `Member.changeNickname(...)`을 호출한다. 저장 시 UNIQUE 위반(`DataIntegrityViolationException`)은 `MEMBER_409_3`으로 바꾼다. 커밋 시점이 아니라 서비스 안에서 잡을 수 있도록 **변경 직후 flush**한다.
- 후보 풀(`NicknameAssigner`)은 변경하지 않는다. 대신 **풀 전체가 `NicknamePolicy`를 통과하는지 테스트로 보장**한다.

### 형식 판정

```
입력 → NFC 정규화 → 앞뒤 공백 제거(strip) = 저장값 후보
  1) 길이 2~10            아니면 MEMBER_400_2
  2) ^[가-힣A-Za-z0-9]+(_[가-힣A-Za-z0-9]+)*$   아니면 MEMBER_400_3
```

2번 정규식이 "허용 문자만", "`_` 처음·끝 금지", "`__` 금지"를 한 번에 표현한다. 공백·자모·이모지는 모두 여기서 걸린다.

### 금칙어 판정

**비교용 정규화** — 저장값과 별개로, 비교에만 쓴다.

```
저장값 후보 → 소문자 → [가-힣a-z]만 남김(_·숫자 제거) = 비교 키
예) "씨_1발"  → "씨발"     "Ass_Kim" → "asskim"
```

**판정** (비교 키 기준)

1. **정확 일치 금칙어**에 비교 키 전체가 있으면 → 금칙어.
2. 비교 키에 **포함 금칙어**가 들어 있으면 → 금칙어. 단, 그 일치가 **허용 목록 단어 하나에 통째로 덮이면** 무시한다.
   - 허용 단어를 지운 뒤 검사하지 않는 이유: "초보지랄"에서 "초보지"를 지우면 "랄"만 남아 "지랄"을 놓친다.

금칙어·허용 목록 파일도 로드할 때 **같은 비교용 정규화**를 거친다. 정규화 결과가 빈 항목은 버린다.

**리소스 파일** (`src/main/resources/nickname/`)

| 파일 | 판정 | 출처 |
|------|------|------|
| `prohibited-contains.txt` | 포함 | LDNOOBW(ko·en) + 직접 작성 |
| `prohibited-exact.txt` | 정확 일치 | LDNOOBW(ko·en) + 직접 작성 |
| `allowed.txt` | 허용 목록 | 직접 작성 |

- 형식: 한 줄에 하나, `#`으로 시작하는 줄은 주석, 빈 줄은 무시.
- 파일 안에서 **출처별로 구역을 나눈다**(`# --- LDNOOBW ko ---`, `# --- LDNOOBW en ---`, `# --- Rodi 직접 작성 ---`). 두 출처에 다 있는 단어는 LDNOOBW 구역에만 둔다.
- 전체 단어 목록은 스펙에 싣지 않고, 구현 PR에서 파일로 검토한다.

**LDNOOBW 가공 (한 번만)** — LDNOOBW는 언어별 텍스트 파일(한 줄에 단어 하나)로 제공되고, 마지막 커밋이 2020-07-13(`5faf2ba`)으로 사실상 갱신이 멈췄다. 그래서 **커밋 `5faf2ba` 기준으로 한 번 가공한 결과 파일만 레포에 둔다.** 서버는 기동 시 이 파일만 읽고, 빌드·실행 중에 GitHub에 접속하지 않는다.

1. `ko`·`en` 원본을 커밋 `5faf2ba` 기준으로 받는다.
2. 빈 줄·앞뒤 공백을 지우고, 정규화 결과가 비는 항목(🖕)을 뺀다.
3. 아래 분류 기준으로 포함 / 정확 일치로 나눈다.
4. 직접 작성 항목과 합치고 중복을 없앤다.
5. 사람이 읽을 수 있는 원래 표기로 파일에 쓴다. 비교용 정규화는 서버가 로드할 때 한다.

**출처 표기** — LDNOOBW 항목이 든 두 파일 상단에 CC BY 4.0이 요구하는 표기를 넣는다.

```
# Includes words from "List of Dirty, Naughty, Obscene, and Otherwise Bad Words"
#   © 2012–2020 Shutterstock, Inc.
#   https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words
#   (commit 5faf2ba42d7b1c0977169ec3611df25a3c08eb13, files: ko, en)
#   Licensed under CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/
# Changes: split into contains/exact lists, normalized (lowercase, letters only),
#   emoji entries removed, Rodi-authored words added.
```

**분류 기준** (LDNOOBW 항목에 적용하는 기본 규칙)

| 조건 | 분류 |
|------|------|
| 비교 키가 한 글자 | 정확 일치. 단, 일반 단어에 쓰이지 않는 `좆`·`좃`·`썅`은 포함 |
| 영문이고 비교 키가 4자 이하(`ass`·`sex`·`anal` 등) | 정확 일치. 단, `fuck`·`shit`·`cunt`·`slut`·`porn` 등 일반 단어 속에 거의 안 나오는 강한 표현은 포함 |
| 영문 5자 이상이지만 일반 단어 속에 들어 있음(`rapist`←therapist, `semen`←basement, `cialis`←social, `twink`←twinkle 등) | 정확 일치 |
| 일상어와 겹치는 한국어(`망가`·`미친`·`유모`·`노모`·`에로`·`고자`·`야사`·`호로`·`자위` 등) | 정확 일치 |
| 그 밖 | 포함 |

**직접 작성 항목** — 아래 분류로 작성하고, 표기 변형(`씌발`·`뷩신` 등)도 넣는다.

- 욕설(씨발·병신·개새끼 계열과 그 변형, 패드립), 성적 표현, 혐오·비하(`김여사` 같은 운전 관련 비하 포함)
- **운영자 사칭**: `운영자`·`관리자`·`운영팀`·`고객센터`·`공식계정`·`로디`·`rodi`·`admin`·`official` 등
- 일상어와 겹치는 것(`시바`·`십팔`·`졸라`·`호구` 등)은 정확 일치에 둔다.

**허용 목록** — 포함 금칙어를 품은 정상 단어. 주요 항목은 다음과 같다.

| 단어 | 걸리는 포함 금칙어 |
|------|-------------|
| 초보지 | 보지 — **"초보지만"·"초보지킴이"처럼 '초보' 뒤에 '지'가 오면 걸린다.** 초보 운전자 서비스라 특히 중요하다 |
| 시발점, 시발역 | 시발 |
| 멜로디 | 로디 |
| 새끼손가락 | 새끼 |
| 등신대 | 등신 |
| 정액제 | 정액 |

운영 중 미탐·오탐이 발견되면 파일을 고쳐 재배포한다.

### 변경 제한 (2026-10-22 추가)

```
요청 닉네임 → 정규화(NFC·strip)
  지금 닉네임과 같음              → 변경 없음(200). 제한·검증 없이 통과
  지금 닉네임과 대소문자만 다름    → 형식·금칙어·중복 검증 후 바꿈. nicknameChangedAt은 그대로
  그 밖(실제 변경)
    now < nicknameChangedAt + 30일 → MEMBER_400_5
    형식 → 금칙어 → 중복 검증 통과 → 바꾸고 nicknameChangedAt = now
```

- 대소문자만 다른 값도 형식·금칙어·중복 검증은 그대로 거친다(대소문자만 바꿔 금칙어·중복이 새로 생기지는 않지만, 같은 검증 경로를 탄다).
- `nicknameChangedAt`이 `null`(아직 직접 바꾼 적 없음)이면 제한이 없다.
- 30일은 `Duration.ofDays(30)` — 바꾼 시각에 그대로 더한다.

### 마이그레이션 V29 — 닉네임 변경 시각 (2026-10-22 추가)

```sql
ALTER TABLE member ADD COLUMN nickname_changed_at TIMESTAMP;
```

- NULL 허용. 기존 회원은 모두 NULL이라 **바로 한 번 바꿀 수 있다**(첫 변경 허용 규칙과 같다).
- 가입 시 자동 부여(`assignNickname`)는 이 값을 채우지 않는다.

### 마이그레이션 V28 — 닉네임 공백을 `_`로 + 대소문자 무시 유니크

> V26(`widen_text_columns_for_graphemes`)이 현재 최대 번호다.

```sql
UPDATE member SET nickname = replace(nickname, ' ', '_') WHERE nickname LIKE '% %';

ALTER TABLE member DROP CONSTRAINT uq_member_nickname;
CREATE UNIQUE INDEX uq_member_nickname_lower ON member (lower(nickname));
```

- **유니크 기준을 `lower(nickname)`으로 바꾼다.** 기존 제약은 대소문자를 구분해 `Kim`과 `kim`이 공존할 수 있다. 기존 닉네임은 모두 한글이라 인덱스를 만들 때 충돌이 없다. `NULL`(익명화 회원)은 여러 개여도 된다(기존과 같다).
- 엔티티 `Member.nickname`의 `@Column(unique = true)`는 더 이상 실제 제약과 맞지 않으므로 뺀다. 스키마는 Flyway가 관리한다.

- 후보 풀은 **단어 사이 공백 1칸** 형식이다(공백 1개 946개, 2개 44개, 연속·앞뒤 공백 없음). 변환 결과는 모두 형식 규칙을 통과한다.
- 지금 `_`가 든 닉네임은 없으므로(사용자 입력 경로가 없었다) 변환해도 **UNIQUE 충돌이 생기지 않는다**.
- 익명화된 회원(`nickname IS NULL`)은 대상이 아니다.
- 후보 풀 파일 `nicknames.txt`도 같은 커밋에서 공백을 `_`로 바꾼다. 배포 직후 가입자가 옛 형식을 받지 않도록 하기 위해서다.

## 완료 조건 (Acceptance Criteria)

**닉네임 변경 (PATCH)**

- [x] `{"nickname": "초보_탈출"}`을 보내면 200이고, 마이페이지 조회 닉네임이 `초보_탈출`이다.
- [x] 앞뒤 공백이 있는 `"  초보_탈출 "`은 `초보_탈출`로 저장된다.
- [x] NFD(자모 분해)로 보낸 한글 닉네임이 NFC로 저장된다.
- [x] 길이: 1자·11자는 400 `MEMBER_400_2`, 2자·10자는 200이다. `null`·빈 문자열도 `MEMBER_400_2`다.
- [x] 형식: `초보 탈출`(공백)·`_초보`·`초보_`·`초보__탈출`·`ㅋㅋ초보`·`초보😀`·`초보!`는 400 `MEMBER_400_3`이다.
- [x] 금칙어: `씨발`·`씨_발`·`시1발`·`개새끼운전`·`운영자`·`ROdi`는 400 `MEMBER_400_4`다.
- [x] 정확 일치: `미친`은 거부되고 `미친듯이`는 통과한다. `ass`는 거부되고 `class`는 통과한다.
- [x] 허용 목록: `시발점`·`멜로디`·`새끼손가락`·`초보지만`은 통과한다.
- [x] 다른 회원이 쓰는 닉네임은 409 `MEMBER_409_3`이고, 본인 현재 닉네임은 200(변경 없음)이다.
- [x] 다른 회원이 `Drive_Kim`을 쓰고 있으면 `drive_kim`은 409다. 본인의 `kim`을 `Kim`으로 바꾸는 것은 200이다.
- [x] UNIQUE 제약 위반(동시 변경 상황)이 발생하면 500이 아니라 409 `MEMBER_409_3`이다.
- [x] `nickname`을 생략하고 `drivingGoal`만 보내면 닉네임은 그대로다.
- [x] `drivingGoal`을 생략하고 `nickname`만 보내면 운전 목표는 그대로다.
- [x] `drivingGoal: null`·`""`은 기존처럼 운전 목표를 지운다.
- [x] `{}`는 200이고 아무것도 바뀌지 않는다.
- [x] `nickname`과 `drivingGoal`을 같이 보내고 하나가 검증에 실패하면 둘 다 저장되지 않는다.

**사용 가능 여부 확인 (GET)**

- [x] 사용 가능한 닉네임은 `available: true`, `reason`·`message`가 null이다.
- [x] 위 PATCH의 각 위반 사례가 200 + `available: false` + 같은 사유 코드(`reason`)로 응답된다.
- [x] 본인 현재 닉네임은 `available: true`다.
- [x] `data.nickname`에 정규화된 값(앞뒤 공백 제거)이 담긴다.
- [x] `nickname` 파라미터가 없으면 400 `COMMON_400`, JWT가 없으면 401이다.

**이관·후보 풀**

- [x] Flyway V28 적용 후 `차근차근 토끼`였던 회원 닉네임이 `차근차근_토끼`로 조회된다.
- [x] V28 적용 후 DB에 대소문자만 다른 닉네임을 직접 넣으면 유니크 인덱스 위반이 난다.
- [x] 후보 풀 990개 전부가 `_` 형식이고, 전부 `NicknamePolicy`를 통과한다(형식·금칙어).
- [x] 신규 가입 시 `_` 형식 닉네임이 부여된다.

**변경 제한 (2026-10-22 추가)**

- [ ] 처음 바꾸는 회원(`nicknameChangedAt` 없음)은 바로 바꿀 수 있고, 바꾼 뒤 `nicknameChangedAt`이 기록된다.
- [ ] 바꾼 뒤 30일 이내에 다른 닉네임으로 바꾸면 400 `MEMBER_400_5`이고 닉네임은 그대로다.
- [ ] 바꾼 시각 + 30일 정각부터는 다시 바꿀 수 있다(1초 전은 거부).
- [ ] 제한 중 같은 닉네임을 보내면 200, 아무것도 바뀌지 않고 `nicknameChangedAt`도 그대로다.
- [ ] 제한 중 대소문자만 바꾸면(`kim` → `Kim`) 200, 닉네임은 바뀌고 `nicknameChangedAt`은 그대로다.
- [ ] 제한 중 `nickname`과 `drivingGoal`을 같이 보내면 400 `MEMBER_400_5`이고 둘 다 저장되지 않는다.
- [ ] 제한 중 형식이 틀린 닉네임을 보내도 `MEMBER_400_5`가 먼저 나온다.
- [ ] 마이페이지 `nicknameChangeableAt`은 제한 중이면 `nicknameChangedAt + 30일`, 아니면 `null`이다.
- [ ] 확인 API는 제한 중이면 `available: false`·`reason: MEMBER_400_5`·`nicknameChangeableAt`, 같은 값·대소문자만 다른 값은 `available: true`다. 다른 사유면 `nicknameChangeableAt`은 `null`이다.
- [ ] PATCH 성공 응답 data에 `nickname`과 `nicknameChangeableAt`이 있다(운전 목표만 바꿔도, 제한이 없으면 `null`).
- [ ] `MEMBER_400_5` 실패 응답 data에 `nicknameChangeableAt`이 있고, 다른 에러(예: 409 중복)는 `data: null`이다.
- [ ] Flyway V29 적용 후 기존 회원은 `nickname_changed_at`이 NULL이고 바로 바꿀 수 있다.

**금칙어 로드**

- [x] 주석(`#`)·빈 줄은 무시되고, 정규화 결과가 빈 항목은 로드되지 않는다.
- [x] LDNOOBW 항목이 든 파일 상단에 출처·라이선스가 적혀 있다.
- [x] `./gradlew test` 통과.

## 구현 노트

- **생략과 `null` 구분**: 처음엔 record + `Optional` 필드로 시도했으나, Jackson이 record의 **생략한 필드도 `Optional.empty()`로 채워** 둘을 구분할 수 없었다(닉네임만 보내면 운전 목표가 지워짐). `MemberUpdateRequest`를 setter 기반 클래스로 바꿔 **setter 호출 여부 = 필드를 보냈는지**로 판정한다. Jackson은 명시한 `null`에도 setter를 부르고, 생략한 필드에는 부르지 않는다. `@GraphemeSize`는 필드에 그대로 붙어 기존 400 응답(`data.drivingGoal`)이 유지된다.
- **원자성**: 닉네임 판정을 어떤 필드도 바꾸기 전에 끝낸다. 운전 목표 길이는 요청 검증(Bean Validation)에서 서비스 진입 전에 걸린다.
- **경합 409**: 닉네임을 바꾼 직후 flush해 유니크 인덱스(`uq_member_nickname_lower`) 위반을 서비스 안에서 잡고 `MEMBER_409_3`으로 바꾼다. 제약 이름으로 확인해 다른 무결성 오류는 그대로 올린다. 테스트는 중복 조회를 "없음"으로 바꿔(spy) 재현했다.
- **허용 목록 판정**: 허용 단어를 지우고 검사하면 "초보지랄"의 "지랄"을 놓쳐, 포함 일치가 허용 단어 하나에 통째로 덮일 때만 무시하도록 바꿨다.
- **금칙어 가공 중 조정**: 영어 사전 대조로 `twat`(sweetwater)을 정확 일치로, 허용 목록에 `prodig`·`aphrodi`(rodi)·`badminton`(admin)·`최후장`(후장)·`자지러`(자지)를 더했다. `뒤져`(뒤지다=찾다)는 정확 일치로 뒀다.
- **예시 닉네임**: 중복 예시였던 `Rodi_Kim`은 사칭 금칙어 `rodi`에 먼저 걸려 `Drive_Kim`으로 바꿨다.

## 미해결 질문

없음.

## 확정된 결정 (리뷰 반영)

- **PATCH는 보낸 필드만 수정한다**(2026-10-08). 필드 생략과 `null`을 구분하고, `drivingGoal`의 `null`/빈값 삭제 동작은 유지한다. 닉네임만 바꾸는 요청이 운전 목표를 지우는 사고를 막는다.
- **금칙어 목록은 스펙에 싣지 않는다**(2026-10-08). 분류 기준·출처 표기만 정하고, 단어는 구현 PR에서 파일로 검토한다. 직접 작성 항목에 표기 변형도 넣는다.
- **중복은 대소문자를 무시한다**(2026-10-08). 사칭 방지가 목적이며, V28에서 유니크 기준을 `lower(nickname)`으로 바꾼다.
- **닉네임 변경은 30일에 한 번**(2026-10-22, 처음엔 제한 없음으로 결정했다가 변경). 바꾼 시각부터 정확히 30일, 첫 변경은 바로 가능, 같은 값·대소문자만 바꾸기는 제한에 세지 않음, 제한 중 거부는 `MEMBER_400_5`, 다음 변경 가능 시각은 마이페이지 `nicknameChangeableAt`으로 준다.
