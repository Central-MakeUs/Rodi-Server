---
description: 변경사항을 Rodi 커밋 컨벤션에 따라 커밋한다
argument-hint: (선택) 커밋 의도/메모
---

git-conventions 스킬의 커밋 컨벤션에 따라 현재 변경사항을 커밋한다.

- `git status`와 `git diff`로 변경 내용을 파악해 적절한 `<type>(<scope>): <subject>`를 결정한다.
- subject는 한글 명사형(50자 이내), 필요 시 body로 맥락 설명.
- 마지막에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` 트레일러를 붙인다.
- 추가 의도가 있으면 참고: "$ARGUMENTS"
- 변경 범위가 섞여 있으면 논리 단위로 나눠 커밋할지 먼저 제안한다.
- **브랜치 규칙**: `main`·`develop`에 직접 커밋하지 않는다. 현재 그 브랜치에 있으면 `develop`에서 `<type>/<도메인>-<작업내용>` 브랜치를 먼저 파자고 제안한다.
