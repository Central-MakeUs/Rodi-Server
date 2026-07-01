---
name: git-conventions
description: Rodi의 commit/PR/issue/브랜치 작성 규칙. 커밋 메시지를 쓰거나 PR·이슈를 작성·생성하거나 브랜치를 만들 때 이 컨벤션을 따른다. "커밋해줘", "PR 만들어줘", "이슈 작성해줘", "브랜치 파줘" 등에서 활성화.
---

# Git 컨벤션 (commit / PR / issue / branch)

## 커밋 메시지

### 형식

```
<type>(<scope>): <subject>

[body]
```

### Type

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `style` | 포맷팅, 세미콜론 등 코드 스타일 |
| `test` | 테스트 코드 추가/수정 |
| `docs` | 문서 수정 (README 등) |
| `chore` | 빌드, 의존성, 설정 변경 |
| `remove` | 파일/코드 삭제 |

### Scope (선택)

도메인 단위로 작성한다: `auth`, `course`, `parking`, `waypoint`, `region`, `member` 등.

### 규칙

- subject는 한글로, **명사형으로 끝내기** (`~추가`, `~수정`, `~삭제`)
- subject는 50자 이내
- body는 필요할 때만 — 이유나 변경 맥락을 설명
- Claude가 작성하는 커밋은 마지막에 트레일러를 붙인다:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

### 예시

```
feat(auth): 카카오 소셜 로그인 구현
fix(course): 단일 장소 코스 조회 시 waypoint null 오류 수정
chore: hibernate-spatial 의존성 추가
refactor(parking): 난이도 계산 로직 분리
docs: API 명세 README 업데이트
```

## Issue

`.github/ISSUE_TEMPLATE/general.md`의 단일 양식을 사용한다 (설명 → 발생한 문제 → 기대 동작 → 재현 방법 → 추가 정보).

### 종류 (라벨)

| 라벨 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 개발 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `chore` | 설정, 의존성 변경 |
| `docs` | 문서 작성/수정 |

### 제목 형식

`[type] 제목` (커밋과 달리 대괄호 라벨 사용):

```
[feat] 카카오 소셜 로그인 구현
[fix] 단일 장소 코스 조회 시 waypoint null 오류
[chore] PostGIS Docker 환경 설정
```

### 브랜치·커밋 연결

- 이슈 생성 후 브랜치 이름에 이슈 번호를 넣으면 추적이 편하다: `feat/#12-auth-kakao-login`
- PR 본문에 `closes #12`를 쓰면 머지 시 이슈가 자동 close된다.

## Pull Request

`.github/PULL_REQUEST_TEMPLATE.md` 형식을 따른다.
- **base 브랜치**: 기능/수정은 `develop`, 운영 긴급 수정(hotfix)은 `main`(+`develop`).
- 제목은 커밋과 동일한 `<type>(<scope>): <subject>` 형식.
- 본문: 요약(무엇을·왜) · 변경 사항 · 관련 스펙(`docs/specs/`)·이슈(`closes #N`) · 체크리스트.

## Branch 전략

```
main
└── develop
    ├── feat/auth-kakao-login
    ├── feat/course-route-api
    ├── fix/parking-difficulty-calc
    └── chore/hibernate-spatial-setup
```

### 종류

| 브랜치 | 설명 | 병합 대상 |
| --- | --- | --- |
| `main` | 배포용, 항상 안정 상태 유지 | - |
| `develop` | 개발 통합 브랜치 | `main` |
| `feat/` | 기능 개발 | `develop` |
| `fix/` | 버그 수정 | `develop` |
| `hotfix/` | 운영 긴급 수정 | `main` + `develop` |
| `chore/` | 설정, 의존성 변경 | `develop` |
| `refactor/` | 리팩토링 | `develop` |

### 네이밍

```
<type>/<도메인>-<작업내용>      예) feat/auth-kakao-login, fix/course-waypoint-null
<type>/#<이슈번호>-<도메인>-<작업내용>   예) feat/#12-auth-kakao-login (이슈 추적 시)
```

### 작업 흐름

```
1. develop에서 브랜치 생성   git checkout -b feat/course-route-api develop
2. 작업 후 커밋             git commit -m "feat(course): 루트 코스 목록 조회 API 구현"
3. develop으로 PR 생성 → 리뷰 → Merge
4. 배포 시점에 develop → main PR
```

### 규칙 요약

- `main` 직접 push 금지
- `develop` 직접 push 지양 (PR 경유)
- 브랜치는 작업 단위로 잘게 (하나의 브랜치 = 하나의 기능/수정)
- 머지 후 브랜치 삭제

## 주의

- commit/merge/push는 **사용자가 명시적으로 요청할 때만** 실행한다.
- push·PR·issue 생성은 외부로 나가는 작업이라 실행 전 확인을 받는다 (원격 연결 필요).
