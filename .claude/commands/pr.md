---
description: 현재 브랜치로 PR을 작성한다 (PULL_REQUEST_TEMPLATE 형식)
argument-hint: (선택) PR 제목/요약
---

git-conventions 스킬의 PR·브랜치 규칙에 따라 현재 브랜치의 변경을 PR로 작성한다.

- **base 브랜치**: 기능/수정은 `develop`, 운영 긴급 수정(hotfix)은 `main`(+`develop`). 자동으로 가정하지 말고 base를 확인한다.
- 제목: 커밋과 동일한 `<type>(<scope>): <subject>` 형식.
- 본문: `.github/PULL_REQUEST_TEMPLATE.md` 형식 — 요약(무엇을·왜) · 변경 사항 · 관련 스펙(`docs/specs/`)·이슈(`closes #N`) · 체크리스트.
- 추가 의도가 있으면 참고: "$ARGUMENTS"
- `gh pr create`는 외부로 나가는 작업이므로 실행 전 본문을 보여주고 확인을 받는다. (원격 연결 필요)
