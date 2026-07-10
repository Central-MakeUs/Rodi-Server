# 애플 소셜 로그인 (Sign in with Apple)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-05 | Draft | 최초 작성 |

## 배경 / 목적

카카오에 이어 iOS 사용자를 위한 **Sign in with Apple**을 추가한다. 기존 소셜 로그인 추상화(`SocialClient` 전략, `SocialProvider.APPLE`, `OAuthUserInfo`, `SocialClientResolver`) 위에 `AppleSocialClient`를 더하는 국소 확장이다(ADR 0008).

애플은 "Sign in with Apple" 채택 앱에 **계정 삭제 시 토큰 revoke**를 요구한다(App Store 심사 지침). 따라서 로그인 시 **애플 refresh token을 확보·저장**해, 이후 회원탈퇴에서 revoke할 수 있게 한다. → **authorizationCode 교환 방식(B안)** 채택.

## 요구사항

- **기능 요구사항**
  - iOS 클라이언트가 Sign in with Apple로 받은 `authorizationCode`를 서버에 전달해 로그인/가입한다.
  - 서버는 애플과 토큰 교환 → id_token 검증으로 회원을 식별(신규 시 가입)하고, 서버 자체 토큰(access+refresh)을 발급한다.
  - 신규 가입 여부(`isNewMember`)를 응답으로 내려 온보딩 분기를 지원한다(카카오와 동일).
  - 애플 `refresh_token`을 소셜 계정에 저장한다(탈퇴 revoke 대비).
- **비기능 요구사항**
  - 회원 식별자는 애플 `sub`(provider user id). 이메일은 부가정보(nullable, 최초 1회·비공개 릴레이 가능).
  - id_token 검증: 서명(RS256, JWKS), `iss=https://appleid.apple.com`, `aud=client_id`, `exp` 만료.
  - client_secret(.p8 서명 JWT)과 애플 refresh_token은 **시크릿** — 커밋 금지, env/암호화 저장.
  - nonce는 미사용(단일사용 authorizationCode + 서버↔애플 TLS 교환으로 리플레이 위협 완화).

## 아키텍처 / 인증 전략

**방식: authorizationCode 교환 (B안)**

1. 클라이언트가 `authorizationCode`를 서버로 전달(`credential`).
2. 서버가 **client_secret** 생성 — `.p8` 개인키로 ES256 서명한 JWT(`iss`=Team ID, `sub`=client_id(Bundle ID), `aud`=`https://appleid.apple.com`, `iat/exp`≤6개월, header `kid`=Key ID). 캐시 후 만료 시 재생성.
3. 애플 토큰 엔드포인트(`https://appleid.apple.com/auth/token`)에 `grant_type=authorization_code` 교환 → `id_token` + `access_token` + `refresh_token`.
4. **id_token 검증**: 애플 JWKS(`https://appleid.apple.com/auth/keys`)로 서명 검증(kid로 공개키 선택, RS256) + `iss`/`aud`/`exp` 확인 → `sub`=providerId, `email`(nullable).
5. 회원 식별/가입(카카오와 동일 오케스트레이션) + **애플 refresh_token을 SocialAccount에 저장**.
6. 서버 JWT(access) + refresh 발급(기존 TokenService 재사용).

- `AppleSocialClient implements SocialClient`(`provider()`=APPLE, `verify(authorizationCode)`).
- JWKS/id_token 검증은 기존 **jjwt 0.13.0의 JWK/JWKS 지원**으로 처리(신규 의존성 없음). JWKS는 메모리 캐시, 모르는 kid면 갱신.

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/auth/oauth/apple | 애플 로그인/가입 | 불필요 |

기존 `POST /api/v1/auth/oauth/{provider}` 엔드포인트를 그대로 사용(`provider=apple`). 요청 DTO도 동일(`credential`), 애플의 경우 `credential`=`authorizationCode`.

```json
// Request  POST /api/v1/auth/oauth/apple
{ "credential": "<apple authorizationCode>" }

// Response 200 (카카오와 동일 구조)
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": { "accessToken": "...", "refreshToken": "...", "isNewMember": true }
}
```

에러: `AUTH_401_5`(애플 검증/교환 실패), `AUTH_400_1`(미지원 provider — 해당 없음), `COMMON_400`(credential 누락).

## 도메인 모델

- **SocialAccount** 확장 (마이그레이션 **V3**): 공급자별 부가정보 컬럼 추가(모두 nullable).
  - `provider_refresh_token` — 애플 refresh_token(탈퇴 revoke용). 애플만 채움. **1차 평문 저장**(암호화는 탈퇴 기능 시 도입).
  - `provider_nickname` — 공급자 프로필 닉네임(카카오톡 닉네임 등). **서비스 닉네임 `member.nickname`과 분리.**
  - `provider_profile_image_url` — 공급자 프로필 이미지 URL.
- `OAuthUserInfo` 확장(모두 nullable): `providerRefreshToken`, `providerNickname`, `providerProfileImageUrl`. 카카오는 프로필/닉네임·이미지 채움(refresh는 null), 애플은 refresh 채움(프로필은 애플이 거의 안 주므로 대개 null). `AuthService`가 값이 있으면 SocialAccount에 저장.
- 신규 엔티티 없음. 회원/소셜계정/토큰 구조는 member-auth 스펙 그대로.

### 공급자 프로필 저장 (카카오)
- 카카오 `/v2/user/me`의 `kakao_account.profile.nickname`, `profile_image_url`을 파싱해 저장(동의항목 **프로필 정보** 활성화 필요).
- **`member.nickname`(서비스 닉네임, 온보딩 설정)과 완전히 분리** — member=서비스 신원, social_account=공급자 프로필 스냅샷.

### 공급자 간 동일인 판단 (account identity)
- **자동 병합 없음** — 공급자마다 `sub`가 다르고 공유 식별자가 없다. 이메일은 **식별 기준으로 쓰지 않는다**(ADR 0008; 애플 릴레이·카카오 동의 이슈). 따라서 **카카오 계정과 애플 계정은 기본적으로 별개 회원**이다.
- 같은 사람으로 묶으려면 **명시적 계정 연결**(이미 로그인한 회원에 다른 공급자 계정을 붙임) — 1:N 스키마로 지원 가능하나 **후순위**. 본 스펙 범위 밖.

## 설정 (환경변수)

| 키 | 설명 | 시크릿 |
|---|---|---|
| `APPLE_TEAM_ID` | 애플 Team ID (client_secret `iss`) | 아니오 |
| `APPLE_KEY_ID` | .p8 Key ID (client_secret header `kid`) | 아니오 |
| `APPLE_CLIENT_ID` | Bundle ID (client_secret `sub`, id_token `aud`) | 아니오 |
| `APPLE_PRIVATE_KEY` | .p8 개인키 내용 (client_secret 서명) | **예** |

- `application.yml`에 `oauth.apple.{issuer, token-uri, jwks-uri, team-id, key-id, client-id, private-key}` 매핑, 시크릿은 `${APPLE_PRIVATE_KEY}` 등 env 주입.
- 운영 전달은 `deploy/compose.prod.yaml` app.environment에 명시 필요(.env 자동주입 안 됨).

## 완료 조건 (Acceptance Criteria)

- [ ] `POST /api/v1/auth/oauth/apple`에 유효한 authorizationCode → 200 + 서버 토큰 발급, 신규 시 `isNewMember=true`
- [ ] id_token 검증 실패(서명/iss/aud/exp) → `AUTH_401_5`
- [ ] 애플 `refresh_token`이 SocialAccount에 저장됨(탈퇴 revoke 대비)
- [ ] 기존 회원(같은 `sub`) 재로그인 시 가입 없이 로그인, `isNewMember=false`
- [ ] client_secret이 .p8로 정상 서명·캐시되고 만료 시 재생성
- [ ] 관련 테스트 통과 (`./gradlew test`) — 토큰 교환/JWKS는 MockRestServiceServer·테스트 키로 검증

## 범위 밖 (다음/후순위)

- **회원탈퇴(revoke)**: 저장한 애플 refresh_token으로 `https://appleid.apple.com/auth/revoke` 호출 → 성공 시 서버 DB 삭제. 별도 탈퇴 스펙에서 다룸(플랫폼 revoke → 200 → DB 삭제 → 프론트 처리 순서).
- 카카오 unlink(탈퇴 시) 역시 탈퇴 스펙에서.
- 계정 연결(한 회원에 애플+카카오) — 후순위.
- 애플 Server-to-Server 알림(계정 삭제 통지) 수신 — 후순위.

## 결정 사항 (2026-07-05)

- **B안(authorizationCode 교환)** 채택 — 탈퇴 시 애플 토큰 revoke가 필요하므로 refresh token 확보.
- **nonce 미사용** — B 구조상 리플레이 위협이 완화되어 프론트/서버 부가작업 대비 이득 적음.
- 신규 의존성 없이 **jjwt로 JWKS/id_token 검증**.
- 엔드포인트·응답은 기존 소셜 로그인과 **동일**(`{provider}=apple`, `credential`=authorizationCode).
- **refresh_token 저장은 1차 평문** — 탈퇴 기능 구현 시 앱 레벨 암호화 도입.
- **매 로그인마다 authorizationCode 교환** — 흐름 단순화 + refresh 최신 유지(iOS는 매 로그인 code 제공).
- 프론트는 **`authorizationCode` 전송**(둘 다 보내도 서버는 code만 사용).
- 공급자 프로필(카카오 닉네임·이미지)은 **social_account에 저장**, `member.nickname`과 분리.
- 공급자 간 동일인 **자동 병합 안 함**(별개 회원). 계정 연결은 후순위.
- **이메일 정책**:
  - **카카오는 이메일 미수집** — null 전제(코드는 nullable 유지, 이메일에 의존하지 않음).
  - **애플은 이메일 최대 확보 → 가입 시 즉시 저장.** 애플 이메일은 사실상 최초 인증 시 제공되고 재로그인 땐 없을 수 있으므로, **첫 가입 시점에 저장**하고 이후 로그인에선 덮어쓰지 않는다(릴레이 주소여도 저장). 식별은 여전히 `sub`.

## 프론트 확인 필요 (구현과 병행)

- 프론트가 `credential`로 **`authorizationCode`를 전송**하는지 최종 확인(iOS `ASAuthorizationAppleIDCredential.authorizationCode`). 둘 다 보내도 서버는 code만 사용.
- 카카오 **프로필 정보 동의항목** 활성화 및 클라이언트 동의 스코프 확인(닉네임·프로필 이미지 저장을 위해).
