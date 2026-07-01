---
description: 대상 코드의 JUnit5 테스트를 작성하고 ./gradlew test로 확인한다
argument-hint: <테스트할 클래스/기능>
---

test-writer 서브에이전트를 사용해 **"$ARGUMENTS"** 에 대한 JUnit5 테스트를 작성한다.

- 기존 `src/test/`의 테스트 컨벤션을 따른다.
- 해당 기능의 `docs/specs/<기능>.md` 완료 조건이 있으면 그것을 테스트로 검증한다.
- 작성 후 `./gradlew test`로 통과를 확인하고, 결과(통과/실패)를 그대로 보고한다.
