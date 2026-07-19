# 기능 스펙 (Specs)

이 디렉토리는 Rodi의 **기능 명세(spec)**를 보관합니다. 기능 구현 전 여기서 설계를 확정하고, 승인 후 구현에 들어갑니다. (CLAUDE.md "작업 순서" 3·4단계와 연결)

## 규칙

- 파일명: `NNN-<기능-슬러그>.md` — 3자리 일련번호 접두사(ADR과 동일 방식). 예: `001-member-auth.md`, `005-place-search.md`. 번호는 기존 최대 +1.
- 새 스펙은 [TEMPLATE.md](TEMPLATE.md)를 복사해 작성한다. (`TEMPLATE.md`·`README.md`는 번호를 붙이지 않는다.)
- **구현 시작 전 스펙을 리뷰·승인**한다 (승인 전 코드 작성 금지).
- 스펙은 살아있는 문서다 — 구현 중 설계가 바뀌면 스펙을 갱신한다.
- 불명확한 부분은 코드보다 스펙의 "미해결 질문"에 먼저 정리/질문한다.

## Status 값

- `Draft` — 작성 중
- `Approved` — 승인됨, 구현 가능
- `Implemented` — 구현·검증 완료
- `Deprecated` — 더 이상 유효하지 않음

## 목록

| 기능 | 설명 | Status |
|------|------|--------|
| [회원 인증](001-member-auth.md) | 소셜 로그인(카카오·애플)·JWT·탈퇴·계정 연결 | Draft |
| [애플 로그인](002-apple-login.md) | Sign in with Apple(코드 교환·JWKS 검증) | Draft |
| [회원 탈퇴](003-member-withdrawal.md) | 단계적 탈퇴·복구·익명화 배치 | Draft |
| [온보딩](004-onboarding.md) | 닉네임 자동 부여·운전 경험/추가정보 수집·레벨 배정 | Draft |
| [코스 탐색](005-place-course-discovery.md) | 장소/코스/주차장 조회(마커·거리순 목록·상세)·북마크 | Draft |
| [마이페이지](006-mypage.md) | 프로필 요약(레벨·추천 태그·저장 수)·운전목표 수정·저장 목록 | Approved |
