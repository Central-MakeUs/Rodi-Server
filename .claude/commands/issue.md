---
description: 이슈를 작성한다 (General Issue 템플릿 + [type] 제목)
argument-hint: <이슈 내용 또는 버그/기능 설명>
---

git-conventions 스킬의 Issue 규칙에 따라 "$ARGUMENTS" 내용으로 이슈를 작성한다.

- 양식: `.github/ISSUE_TEMPLATE/general.md` (설명 → 발생한 문제 → 기대 동작 → 재현 방법 → 추가 정보).
- 제목: `[type] 제목` 형식 (`feat`/`fix`/`refactor`/`chore`/`docs` 중 적절한 라벨). 예: `[feat] 카카오 소셜 로그인 구현`.
- 가능하면 관련 도메인(scope)을 제목에 드러낸다.
- `gh issue create`는 외부로 나가는 작업이므로 실행 전 내용을 보여주고 확인을 받는다. (원격 연결 필요)
- 작성 후, 이어서 작업할 거면 브랜치 네이밍을 안내한다: `<type>/#<이슈번호>-<도메인>-<작업내용>`.
