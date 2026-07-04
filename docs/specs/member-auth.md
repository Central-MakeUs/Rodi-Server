# 회원 인증 (소셜 로그인 · JWT)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-07-03 | Draft | 최초 작성 (카카오 우선, 애플 확장 설계) |
| 2026-07-03 | Approved | 결정 반영(authorizationCode·해시·탈퇴 후순위·/api/v1) |

## 배경 / 목적

Rodi의 개인화 기능(찜·운전기록·온보딩)은 회원 식별이 전제다. 가입 마찰을 줄이기 위해 소셜 로그인(카카오·애플)을 제공하고, 이후 모든 API는 **서버 자체 JWT**로 인증한다. **새 소셜 공급자 추가**와 **한 회원의 여러 소셜 계정 연결**을 고려해 확장 가능하게 설계한다.

## 요구사항

**기능**
- 소셜 로그인/가입 — **카카오 우선**, 애플은 확장 설계(구현은 다음 이슈)
- 서버 자체 JWT 발급: **access + refresh**
- refresh 재발급(회전)
- 로그아웃 (refresh 무효화)
- 회원 탈퇴 (soft delete + PII 익명화 — [ADR 0004](../adr/0004-member-soft-delete.md))
- 소셜 계정 연결 (로그인 상태에서 다른 provider 추가)
- **랜덤 닉네임 자동 부여**(서버 생성, 유니크)

**비기능**
- access 짧게(기본 30분) / refresh 길게(기본 14일) — 설정값(prod는 env)
- **refresh는 DB 저장** → 로그아웃·탈퇴 시 폐기, 재발급 시 회전
- 민감정보(authorization code / id_token)는 **POST body**로만 (URL 노출 금지)
- 새 공급자 = `SocialClient` 구현 하나 추가로 확장 (코어 흐름 무변경)
- 세션리스(STATELESS, 기존 SecurityConfig)

## 아키텍처 / 인증 전략

```
AuthController
 └ AuthService  (오케스트레이션: 소셜검증 → 회원 조회/생성 → 토큰 발급)
      ├ SocialClient (interface)           ← provider별 구현, Map<SocialProvider, SocialClient>로 디스패치
      │    ├ KakaoClient (구현)            authorizationCode → 카카오 검증 → OAuthUserInfo
      │    └ AppleClient (확장, 다음 이슈)  id_token → Apple JWKS 서명검증 → OAuthUserInfo
      ├ MemberService (조회·가입·탈퇴·닉네임 발급)
      └ TokenService  (JWT access/refresh 발급·검증·회전, refresh DB 관리)
 + JwtAuthenticationFilter (OncePerRequestFilter): 매 요청 access 검증 → SecurityContext
```

- `SocialProvider` enum: `KAKAO`, `APPLE`(예약) …
- 공통 반환 `OAuthUserInfo(provider, providerId, email, nickname)` 로 공급자 차이 흡수.
- **안정 식별자는 `providerId`** — email은 선택(애플 최초 1회만, 카카오 동의 필요).

## API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/auth/oauth/{provider}` | 소셜 로그인/가입 | X |
| POST | `/api/v1/auth/oauth/{provider}/link` | 로그인 상태서 소셜 계정 연결 | O |
| POST | `/api/v1/auth/token/refresh` | refresh로 재발급(회전) | X |
| POST | `/api/v1/auth/logout` | refresh 무효화 | O |
| DELETE | `/api/v1/members/me` | 회원 탈퇴(soft delete + PII 익명화) | O |

`{provider}` = `kakao` | `apple`(예약). 응답은 공통 `ApiResponse<T>`.

> **이번 범위**: 로그인/가입·refresh·logout (카카오). **후순위**: `/link`(애플 이후), `DELETE /members/me`(탈퇴 정책 기획 확정 후).

```json
// POST /api/v1/auth/oauth/kakao  (Request)
{ "authorizationCode": "..." }        // 카카오: authorization code (애플: { "idToken": "..." })

// Response (200)
{ "isSuccess": true, "code": "AUTH_200", "message": "로그인 성공",
  "data": { "accessToken": "eyJ...", "refreshToken": "eyJ...", "isNewUser": true } }

// POST /api/v1/auth/token/refresh  (Request)
{ "refreshToken": "eyJ..." }
// Response: 새 accessToken + 회전된 refreshToken

// 실패 예: 유효하지 않은 code/토큰
{ "isSuccess": false, "code": "AUTH_401", "message": "인증에 실패했습니다.", "data": null, "traceId": "..." }
```

## 도메인 모델

⚠️ **현재 ERD 변경**: `member`의 `oauth_provider`/`oauth_id` 제거 → `social_account`로 분리. (ERD 갱신 + 인증/소셜 모델 ADR 작성 필요)

- **member** (users): `id`, `email`(nullable), `nickname`(**unique**), `level`, `car_type`, 온보딩 속성, `deleted_at`(soft delete), created/updated. — provider 필드 없음.
- **social_account**: `id`, `member_id`(FK), `provider`(enum), `provider_id`(공급자 사용자 ID), `email`(공급자, nullable), `created_at`. **unique `(provider, provider_id)`**. member ↔ social_account = **1:N**.
- **refresh_token**: `id`, `member_id`(FK), `token`(**해시 저장 권장**), `expires_at`, `created_at`. 로그아웃/탈퇴 시 삭제, 재발급 시 회전.

## 소셜 검증 흐름 (카카오)

1. 앱이 카카오 로그인 → **authorization code**를 서버로 전달
2. 서버가 카카오에 토큰 교환(REST API key + client secret, 서버 보관) → 카카오 `/v2/user/me`
3. `providerId`(카카오 id), email, nickname 획득 → `OAuthUserInfo`
4. `(KAKAO, providerId)`로 social_account 조회 → 있으면 그 member 로그인 / 없으면 member(+랜덤 닉네임) + social_account 생성(가입)
5. access/refresh 발급(refresh DB 저장) → 반환

> 애플(다음 이슈): `id_token`을 Apple 공개키(JWKS)로 서명검증 → `sub`=providerId. client_secret은 ES256 서명 JWT(개발자 키 필요).

## 랜덤 닉네임 생성

- **형용사 + 명사 (+ 숫자)** 조합으로 서버 생성 (예: `초보너구리482`).
- `member.nickname` **유니크** 보장 — 충돌 시 재생성(숫자 재추첨, 최대 N회).

## 완료 조건 (Acceptance Criteria)

- [ ] 신규 카카오 로그인 → member + social_account 생성, access/refresh 발급, `isNewUser=true`
- [ ] 기존 카카오 로그인 → 동일 member 반환, `isNewUser=false`
- [ ] refresh 재발급 시 새 access + refresh **회전**(이전 refresh 재사용 불가)
- [ ] 로그아웃 후 해당 refresh 사용 불가(401)
- [ ] 탈퇴 시 `member.deleted_at` 설정 + PII 익명화 + refresh 폐기 → 이후 동일 소셜 재로그인은 **신규 가입** 취급
- [ ] 잘못된 code/토큰 → 표준 에러(`ApiResponse`, 4xx)
- [ ] JWT 필터가 보호 API에서 access 검증(없거나 만료 시 401)
- [ ] `./gradlew test` 통과 (Testcontainers)

## 범위 밖 (다음/후순위)

- **회원 탈퇴** — 후순위(탈퇴 정책 기획 확정 후). soft delete 컬럼(`member.deleted_at`)은 모델에 유지.
- **애플 로그인 실제 구현** — 확장 설계만
- **소셜 계정 연결(`/link`)** — 애플 도입 이후
- 온보딩(레벨·차종·선호 유형) — 별도 스펙
- 이메일/비밀번호 자체 회원가입 (소셜만 제공)

## 결정 사항 (2026-07-03)

1. 카카오 credential: **authorizationCode** (서버가 카카오와 교환, 시크릿 서버 보관)
2. refresh 저장: **해시 저장** (원문 미저장)
3. 회원 탈퇴: **후순위** — 소셜 로그인 우선 (soft delete 컬럼은 유지)
4. 닉네임: **운전 테마 단어 풀**로 임의 배정 (임시 — 단어/중복 정책은 기획 확인)
5. **`/api/v1` prefix**를 전 API 공통 컨벤션으로 CLAUDE.md에 명시

## 기획 확인 필요 (구현과 병행)

- 탈퇴 정책(재가입 즉시/유예), 닉네임 단어 풀·중복 규칙 상세
