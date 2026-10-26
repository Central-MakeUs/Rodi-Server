# ADR 0017: 개발 서버 분리 — Raspberry Pi(pi2) + Tailscale OIDC 배포

## 상태 (Status)

Accepted (2026-10-26) — [ADR 0005](0005-hosting-aws-lightsail.md)의 "develop 자동 배포" 절을 대체한다(나머지 0005 결정은 유지).

## 배경

[ADR 0005](0005-hosting-aws-lightsail.md)에서 `main`·`develop`을 Lightsail(1GB) 한 대의 같은 `app`에 배포하기로 했다. 서버에 앱을 두 개 올릴 메모리가 없어 스테이징을 두지 못했고, 그래서 미완성 기능과 테스트용 API(예: 즉시 탈퇴)가 운영 서버에 그대로 올라갔다.

가정용 Raspberry Pi 5 두 대(4GB, ARM64)로 개발 서버를 따로 세울 수 있게 됐다. `pi`는 PostgreSQL+PostGIS, `pi2`는 Spring Boot를 맡는다. 문제는 두 가지다.

- pi2는 **가정용 사설망**(공유기 NAT 뒤)이라 GitHub-hosted runner가 직접 SSH로 접속할 수 없다. SSH 포트를 인터넷에 공개하고 싶지 않다.
- 이 레포는 **public**이다.

## 결정

1. **브랜치별 배포 분리**: `develop` → pi2(`cd-dev.yml`), `main` → Lightsail(`cd.yml`, 기존 방식 그대로). 두 워크플로는 concurrency 그룹도 따로 둔다(`cd-dev` / `cd-deploy`).
2. **연결은 Tailscale + SSH**: 배포 잡만 Tailscale에 **임시(ephemeral) 노드**로 들어와 pi2에 SSH로 배포한다. 인바운드 포트는 열지 않는다.
   - 인증은 **OIDC(Workload Identity Federation)**. 장기 비밀값(OAuth Client Secret)을 두지 않고, Tailscale 쪽 subject를 `repo:Central-MakeUs/Rodi-Server:ref:refs/heads/develop`로 묶는다.
   - ACL로 `tag:ci`는 `tag:server`(pi2)의 TCP 22만 접근한다.
   - SSH는 러너 기본 OpenSSH로 하고, `known_hosts`를 레포 시크릿(pi2 공개 호스트 키)으로 고정해 **첫 배포부터 호스트 키를 검증**한다.
3. **ARM64 이미지**: ARM 호스티드 러너(`ubuntu-24.04-arm`, public 레포 무료)에서 네이티브로 빌드한다. Dockerfile은 그대로 쓴다. 태그는 `dev-<sha>`·`develop`으로 운영(`<sha>`·`main`)과 접두어를 나눠, 아키텍처가 다른 이미지가 같은 태그를 덮지 않게 한다.
4. **배포 실패 시 자동 롤백**: pi2의 배포 스크립트(`deploy/deploy-dev.sh`)가 마지막으로 헬스체크를 통과한 태그를 기록해 둔다. 새 이미지가 헬스체크를 통과하지 못하면 그 태그로 되돌린 뒤 잡을 실패로 끝낸다(서비스는 살리고, 실패는 숨기지 않는다).
5. **내부 API는 프로필로 게이팅**: `@DevOnly`(= `@Profile({"local","dev"})`)를 붙인 빈은 운영에서 만들어지지 않는다. 관리자 API는 운영에도 필요하므로 대상이 아니다(권한으로 막을 대상).

## 결과

### 긍정적

- 운영과 개발 서버가 분리된다. develop에 올린 변경이 운영 사용자에게 바로 닿지 않는다.
- 인바운드 포트 없이 배포한다. GitHub에 Tailscale 비밀값이 없고, develop 이외 브랜치나 fork PR은 tailnet에 들어올 수 없다.
- 배포 성공 여부(헬스체크)와 실패 로그를 Actions에서 바로 본다. 사전 점검(22번 도달·SSH)으로 네트워크 문제와 앱 문제가 구분된다.
- 개발 서버 비용이 0원이다(전기료 제외). 4GB라 Lightsail보다 메모리 여유가 있다.

### 부정적

- Tailscale(외부 서비스)에 의존한다. Tailscale 장애 시 개발 서버 배포만 멈춘다(운영 영향 없음).
- 가정용 회선·전원·Wi-Fi에 의존해 가용성이 낮다(pi2 간헐 멈춤 이력). 개발 서버 용도라 감수한다.
- 공인 IP가 바뀌면 카카오 허용 IP를 다시 등록해야 한다.
- 레포가 private으로 바뀌면 ARM 러너가 유료가 된다 → `ubuntu-latest` + QEMU로 바꾸고, 빌드 스테이지를 `FROM --platform=$BUILDPLATFORM`으로 고쳐 Gradle만 네이티브로 돌린다.

### 중립적

- 자동 롤백은 **이미지만** 되돌린다. 새 버전이 Flyway 마이그레이션을 적용한 뒤 실패하면 DB는 새 스키마로 남는다. 이전 버전은 Flyway가 모르는 마이그레이션을 무시(`*:future`)하지만, 컬럼 삭제·이름 변경처럼 호환되지 않는 변경이면 Hibernate `validate`에서 기동이 실패할 수 있다. 그때는 DB를 수동으로 복구해야 한다(개발 DB라 감수, 스펙 023 "배포 실패와 복구" 참고).
- 운영(`cd.yml`)에는 자동 롤백을 넣지 않았다. 운영 배포 방식을 이번에 바꾸지 않기로 했고, 개발 서버에서 충분히 써 본 뒤 같은 스크립트 방식을 운영에 적용할지 검토한다.

## 검토한 대안

### 대안 1 — Self-hosted runner (pi2)

인바운드 포트가 필요 없고 설정이 단순하다. 그러나 **public 레포**에서는 fork PR이 워크플로를 바꿔 pi2에서 임의 코드를 실행할 수 있다(runner는 docker 그룹이라 사실상 root). GitHub도 public 레포에는 권장하지 않아 채택하지 않았다.

### 대안 2 — pi2가 GHCR을 직접 pull (Watchtower·타이머)

CI가 서버에 접속하지 않아 가장 단순하다. 그러나 Actions가 성공해도 실제 배포·헬스체크 결과를 알 수 없고, 실패 로그가 pi2에만 남는다. "배포 성공 여부를 Actions에서 확인"하는 요구를 채우지 못해 채택하지 않았다.

### 대안 3 — Tailscale OAuth Client Secret

동작은 같지만 GitHub Secrets에 장기 비밀값이 남는다. 유출되면 어디서든 tailnet에 기기를 추가할 수 있어 OIDC를 택했다.

## 참조

- 스펙: [023 Pi 개발 서버 배포](../specs/023-pi-dev-server-deploy.md) · 이슈 [#82](https://github.com/Central-MakeUs/Rodi-Server/issues/82)
- [ADR 0005](0005-hosting-aws-lightsail.md) (운영 인프라 — "develop 자동 배포" 절은 이 ADR로 대체)
- 워크플로: [cd-dev.yml](../../.github/workflows/cd-dev.yml) · [cd.yml](../../.github/workflows/cd.yml)
- 배포 구성: [deploy/compose.dev.yaml](../../deploy/compose.dev.yaml) · [deploy/deploy-dev.sh](../../deploy/deploy-dev.sh)
- [tailscale/github-action](https://github.com/tailscale/github-action) · [Tailscale Workload identity federation](https://tailscale.com/kb/1581/workload-identity-federation)
