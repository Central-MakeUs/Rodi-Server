# ADR 0006: 코드 품질·의존성 도구

## 상태 (Status)

Accepted (2026-07-03)

## 배경

1인 개발이지만 코드 품질·일관성·보안을 자동으로 지키고 싶다. public 레포로 전환해 오픈소스용 무료 도구를 쓸 수 있다. CodeRabbit이 이미 PR을 AI로 리뷰하므로, 겹치지 않고 실익 있는 도구만 선별한다.

## 결정

아래 도구를 도입하고 Gradle `check`/`build`와 CI에 연결한다.

- **Spotless** (google-java-format, AOSP 4-space): 포맷 자동화·검증. `build`에 연결(미포맷 시 실패), `spotlessApply`로 교정.
- **SonarCloud** (`org.sonarqube` 플러그인): 정적분석(버그·스멜·보안·중복). public 무료. **현재 CMC SonarQube 조직 권한 대기로 보류** — CI의 sonar 단계는 `SONAR_TOKEN`이 있을 때만 실행(가드), 없으면 자동 스킵.
- **JaCoCo**: 테스트 커버리지 측정 → XML 리포트를 SonarCloud에 입력.
- **CodeRabbit**: PR AI 리뷰(한국어, assertive). 프로젝트 컨벤션·Flyway·시크릿 관련 path 지침 제공. 사소한 포맷은 Spotless/Sonar가 처리하도록 톤 지정.
- **Dependabot**: gradle·github-actions·docker 의존성 주간 업데이트 PR(대상 `develop`).

## 결과

### 긍정적

- 포맷·정적분석·AI 리뷰·의존성 업데이트가 자동화되어 1인 개발에도 품질 게이트 확보.
- 도구 간 역할 분리(Spotless=형식, Sonar=정적분석, CodeRabbit=맥락 리뷰)로 중복 노이즈 최소화.
- 그린필드에 도입해 레거시 경고 부채 없음.

### 부정적

- SonarCloud는 외부(CMC) 권한에 의존 → 활성화 지연.
- 도구 수 증가로 초기 설정·학습 비용.

### 중립적

- SonarCloud 활성화 시 `SONAR_TOKEN` 등록 + `sonar.projectKey`/`organization`을 실제 값으로 정정 필요.

## 검토한 대안

### 대안 1 — SpotBugs + Checkstyle + PMD 조합

로컬 정적분석 조합이나 SonarCloud와 기능이 대부분 중복되고 CodeRabbit과도 겹쳐 노이즈만 증가. SonarCloud 단일로 대체해 선택하지 않음.

### 대안 2 — 정적분석 없이 CodeRabbit만

AI 리뷰만으로는 커버리지·정량 지표·보안 규칙이 부족. 무료(public)인 SonarCloud를 더함.

## 참조

- [ADR 0005](0005-hosting-aws-lightsail.md) (CI/CD 파이프라인)
- [build.gradle](../../build.gradle) · [.coderabbit.yaml](../../.coderabbit.yaml) · [.github/dependabot.yml](../../.github/dependabot.yml)
