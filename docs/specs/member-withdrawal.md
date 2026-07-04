# 회원 탈퇴 (Account Withdrawal)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-05 | Draft | 최초 작성 |

## 배경 / 목적

회원이 서비스를 탈퇴한다. **서버 DB만 지우는 게 아니라, 소셜 공급자(애플·카카오)에도 연결 해제/토큰 revoke를 먼저 요청**하고 성공했을 때만 내부 데이터를 정리해야 한다. 애플은 "Sign in with Apple" 채택 앱에 **계정 삭제 시 토큰 revoke를 필수로 요구**한다(App Store 심사 지침). 공급자 정리를 빠뜨리면 "탈퇴했는데 공급자엔 연결이 남는" 사고가 난다.

## 요구사항

- **기능 요구사항**
  - 인증된 회원이 자신의 계정을 탈퇴한다.
  - 회원에 연결된 **모든 소셜 계정을 공급자에서 해제/revoke**한다(애플 revoke, 카카오 unlink).
  - 공급자 정리가 **성공(200)** 하면 서버 데이터를 정리한다: 회원 soft delete(ADR 0004), 소셜 계정 삭제, 서버 refresh 토큰 전체 폐기.
  - 처리 결과를 응답 → 클라이언트가 로그아웃/화면 처리.
- **비기능 요구사항**
  - **순서 보장**: 플랫폼 revoke/unlink → 성공 시 DB 정리 → 응답. (플랫폼 실패 시 DB는 건드리지 않고 에러 반환)
  - 인증 필요(`@CurrentMember`). 본인 계정만.
  - 멱등성 고려: 이미 탈퇴(soft delete)된 회원 재요청은 안전하게 처리.

## 아키텍처 / 처리 흐름

`DELETE /api/v1/members/me` (인증 필요)

1. `@CurrentMember`로 회원 식별, 연결된 `social_account` 목록 조회.
2. 각 소셜 계정에 대해 **공급자 해제 호출**(전략: `SocialClient`에 `revoke(account)` 추가 또는 provider별 unlink 서비스):
   - **애플**: `POST https://appleid.apple.com/auth/revoke` — `client_id`, `client_secret`(.p8 서명 JWT), `token`=저장된 `provider_refresh_token`, `token_type_hint=refresh_token`.
   - **카카오**: `POST https://kapi.kakao.com/v1/user/unlink` — Admin Key 방식(`Authorization: KakaoAK {admin_key}`, `target_id_type=user_id`, `target_id={providerId}`).
3. 모든 공급자 해제가 성공하면:
   - `member` soft delete(`deletedAt` 설정, ADR 0004),
   - `social_account` 삭제(또는 마킹) — 같은 공급자 계정으로 재가입 가능하게,
   - 해당 회원의 `refresh_token` 전체 폐기(`revokeAllByMember`).
4. 실패 시: DB 변경 없이 에러 반환(어떤 공급자가 실패했는지 로깅).

- 트랜잭션: 공급자 호출은 외부 I/O라 DB 트랜잭션 밖에서 수행하고, 성공 후 DB 정리를 트랜잭션으로 묶는다.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| DELETE | /api/v1/members/me | 회원 탈퇴(공급자 revoke 후 정리) | JWT |

```json
// Request  DELETE /api/v1/members/me   (Authorization: Bearer <accessToken>)
// (본문 없음)

// Response 200
{ "isSuccess": true, "code": "COMMON_200", "message": "요청에 성공했습니다.", "data": null }
```

에러(안):
- `AUTH_401_6` 인증 필요
- (신규) 공급자 해제 실패 코드 — 예: `MEMBER_4xx_x` 또는 `AUTH_4xx_x`(탈퇴 처리 중 공급자 revoke 실패). 코드값은 구현 시 확정.

## 도메인 모델

- 신규 엔티티 없음. `member.deletedAt`(soft delete, 기존), `social_account.provider_refresh_token`(애플 revoke용, apple-login 스펙 V3에서 추가).
- 소셜 계정 삭제 정책(하드 삭제 vs soft) — 미해결 질문 참조.

## 설정 (환경변수)

| 키 | 설명 | 시크릿 |
|---|---|---|
| `KAKAO_ADMIN_KEY` | 카카오 unlink(Admin Key 방식) | **예** |
| (애플) | apple-login 스펙의 `APPLE_*` 재사용 | — |

## 완료 조건 (Acceptance Criteria)

- [ ] `DELETE /api/v1/members/me` → 공급자 revoke/unlink 성공 시 회원 soft delete + 소셜계정 삭제 + refresh 전체 폐기
- [ ] 애플 계정: 저장된 refresh_token으로 revoke 호출, 성공 확인
- [ ] 카카오 계정: Admin Key로 unlink 호출, 성공 확인
- [ ] 공급자 해제 실패 시 **DB 변경 없이** 에러 반환
- [ ] 탈퇴 후 해당 회원의 서버 토큰으로 접근 불가(401)
- [ ] 관련 테스트 통과 (`./gradlew test`) — 공급자 호출은 MockRestServiceServer로

## 범위 밖 (다음/후순위)

- 애플 **Server-to-Server 알림**(사용자가 애플에서 직접 연동 해제/계정 삭제 시 통지) 수신 처리 — 후순위.
- 탈퇴 데이터 보존 정책/유예기간, 재가입 제한 — 후순위.

## 미해결 질문

- **소셜 계정 삭제 방식**: 하드 삭제(재가입 깔끔) vs soft delete(감사 추적). member는 soft delete인데 social_account도 맞출지?
- **부분 실패 처리**: 한 회원에 여러 공급자가 연결된 경우(계정 연결은 후순위라 당장은 1개지만), 일부만 revoke 성공 시 롤백 정책.
- 공급자 revoke 실패 시 **재시도/수동 정리** 필요 여부(예: 애플 토큰 이미 만료).
- 탈퇴 에러 코드 체계 확정.
