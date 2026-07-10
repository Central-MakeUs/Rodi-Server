# 회원 탈퇴 (단계적 탈퇴 · 복구)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-05 | Draft | 최초 작성(즉시 삭제안) |
| 2026-07-07 | Draft | PM 정책 반영 — 3일 복구 / 3일 익명화 / 10일 재가입 단계화 |

## 배경 / 목적

회원 탈퇴를 **단계적**으로 처리한다. 실수 탈퇴를 되돌릴 수 있는 유예기간을 두고, 이후 개인정보를 익명화/삭제하며, 일정 기간 후 동일 소셜 계정으로 재가입을 허용한다. 애플은 "Sign in with Apple" 앱에 계정 삭제 시 토큰 revoke를 요구한다(App Store 심사).

## 정책 타임라인

| 시점 | 처리 |
|------|------|
| **Day 0** (탈퇴 요청) | `member.deletedAt = now`, 서버 세션(refresh) 전체 폐기. **소셜 연결·개인정보 유지**(복구 대비) |
| **Day 0~3** (복구 가능) | 동일 소셜 재로그인 시 "탈퇴 처리 중, 복구?" 안내 → 복구 시 `deletedAt` 해제 |
| **Day 3** (익명화) | 개인정보 익명화(null) + **공급자 revoke(애플)/unlink(카카오)**. `anonymizedAt=now`. 소셜 연결은 유지(재가입 차단) |
| **Day 3~10** (잠금) | 복구·재가입 모두 불가. 재로그인 시 "이미 탈퇴 처리됨, N일 후 재가입 가능" |
| **Day 10** (재가입 허용) | `social_account` 삭제(=식별자 해제). 이후 동일 소셜 로그인은 **신규 가입** |

## 요구사항

- **기능**
  - 인증된 회원이 탈퇴를 요청한다(Day 0).
  - Day 0~3 재로그인 시 복구 안내를 받고, 복구를 선택하면 계정이 원상 복구된다.
  - Day 3에 개인정보 익명화 + 공급자 연결 해제, Day 10에 소셜 식별자 해제(재가입 허용).
- **비기능**
  - 유예/익명화/해제 시점 경과는 `member.deletedAt` 기준으로 계산.
  - 익명화·해제는 **일 배치 스케줄러**로 처리(단일 인스턴스).
  - 공급자 revoke/unlink는 외부 I/O — 실패 시 로깅·다음 배치 재처리 여지.

## 상태 파생

`member.deletedAt`(요청 시각) + `anonymizedAt`(익명화 시각, nullable)로 파생:
- `deletedAt == null` → **ACTIVE**
- `deletedAt != null` & `now < deletedAt + 3d` → **WITHDRAWAL_PENDING**(복구 가능)
- 그 외(`deletedAt + 3d` 경과) → **WITHDRAWAL_LOCKED**(복구 불가). `deletedAt + 10d` 경과분은 배치가 소셜 연결 해제.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| DELETE | /api/v1/members/me | 탈퇴 요청(Day 0) | JWT |
| POST | /api/v1/auth/oauth/{provider}/restore | 계정 복구(소셜 재검증) | 불필요 |
| POST | /api/v1/auth/oauth/{provider} | 로그인 — 탈퇴 대기 회원이면 복구 안내 응답 | 불필요 |

### 탈퇴 요청
```
DELETE /api/v1/members/me   (Authorization: Bearer)
→ 200 { isSuccess:true, code:"COMMON_200", data:null }
```

### 로그인 응답 확장 (status 필드 추가)
로그인 성공/탈퇴대기를 `status`로 구분(성공 응답 하위호환 — 성공 시 토큰 필드 그대로, `status:"SUCCESS"` 추가).
```json
// 정상 로그인
{ "isSuccess": true, "code": "COMMON_200",
  "data": { "status": "SUCCESS", "accessToken": "...", "refreshToken": "...", "isNewMember": false,
            "withdrawalRequestedAt": null, "recoverableUntil": null } }

// 탈퇴 대기(Day 0~3) — 토큰 미발급
{ "isSuccess": true, "code": "COMMON_200",
  "data": { "status": "WITHDRAWAL_PENDING", "accessToken": null, "refreshToken": null, "isNewMember": false,
            "withdrawalRequestedAt": "2026-07-07T10:00:00", "recoverableUntil": "2026-07-10T10:00:00" } }
```
- 클라이언트: `status == "WITHDRAWAL_PENDING"` 이면 "탈퇴 처리 중입니다. 복구하시겠습니까?" 다이얼로그 → 예 → 복구 API.
- **Day 3~10 잠금** 회원 로그인 시도는 에러 `MEMBER_WITHDRAWAL_LOCKED`(409, "N일 후 재가입 가능").

### 계정 복구
```
POST /api/v1/auth/oauth/{provider}/restore   { "credential": "..." }
→ 소셜 credential 재검증 → 해당 회원이 WITHDRAWAL_PENDING이면 deletedAt 해제 → 토큰 발급
→ 200 { data: { status:"SUCCESS", accessToken, refreshToken, isNewMember:false, ... } }
```
- 대상이 복구 불가 상태면 `MEMBER_WITHDRAWAL_LOCKED`.

## 도메인 모델

- **member** 확장(마이그레이션 **V4**): `anonymized_at TIMESTAMP` 추가. (`deleted_at` 기존)
- 익명화 대상(null 처리): `member.email`, `member.nickname`, `social_account.provider_nickname`, `provider_profile_image_url`, `provider_refresh_token`(revoke 후).
- Day 10에 `social_account` 행 삭제(식별자 해제). member 행은 익명화 상태로 감사 보존.

## 스케줄러 / 공급자 해제

- `@Scheduled`(일 1회) → `WithdrawalScheduler`:
  1. **익명화**: `deletedAt <= now-3d` & `anonymizedAt is null` → 공급자 revoke/unlink → 개인정보 null → `anonymizedAt=now`.
  2. **해제**: `deletedAt <= now-10d` → `social_account` 삭제.
- 공급자 해제: `SocialClient`에 `revoke(SocialAccount)` 추가.
  - 애플: `POST /auth/revoke` (client_secret + 저장된 refresh_token)
  - 카카오: `POST /v1/user/unlink` (Admin Key + providerId)

## 설정 (환경변수)

| 키 | 설명 | 시크릿 |
|---|---|---|
| `KAKAO_ADMIN_KEY` | 카카오 unlink(Admin Key 방식) | **예** |
| (애플) | apple-login 스펙의 `APPLE_*` 재사용 | — |

## 완료 조건 (Acceptance Criteria)

- [ ] `DELETE /api/v1/members/me` → deletedAt 세팅 + refresh 전체 폐기
- [ ] Day 0~3 재로그인 → `status:WITHDRAWAL_PENDING` + 복구정보 반환(토큰 미발급)
- [ ] 복구 API → deletedAt 해제 후 정상 토큰 발급, 이후 로그인 정상
- [ ] 스케줄러: 3일 경과분 익명화(개인정보 null + 공급자 revoke/unlink + anonymizedAt)
- [ ] 스케줄러: 10일 경과분 social_account 삭제 → 동일 소셜 재로그인 시 신규 가입(isNewMember=true)
- [ ] Day 3~10 로그인 시도 → `MEMBER_WITHDRAWAL_LOCKED`
- [ ] 관련 테스트 통과(`./gradlew test`) — 공급자 호출은 MockRestServiceServer, 스케줄러는 단위 테스트

## 결정 사항 (2026-07-07)

- 로그인 응답에 `status` 필드로 복구 대기 표현(성공 응답 하위호환).
- 복구 인증은 **소셜 credential 재검증**.
- 공급자 revoke/unlink는 **Day 3(익명화 시점)**.
- 익명화는 개인정보 **완전 null**, member·social_account 행은 유지(감사·재가입 차단), social_account는 Day 10 삭제.
- 탈퇴 요청 즉시 **refresh 전체 폐기**.

## 범위 밖 (후순위)

- 애플 Server-to-Server 알림 수신, 재가입 제한(쿨다운) 세부, 탈퇴 사유 수집.
