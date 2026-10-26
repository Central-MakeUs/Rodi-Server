# Raspberry Pi 개발 서버 배포 (develop → pi2)

## Status

| 날짜 | Status | 변경 내용 |
|------|--------|-----------|
| 2026-10-23 | Draft | 최초 작성 — 이슈 [#82](https://github.com/Central-MakeUs/Rodi-Server/issues/82). `develop`→pi2 / `main`→Lightsail 분리, `dev` 프로필, ARM64 이미지, 배포 연결 방식(Tailscale 추천), 내부 API 프로필 게이팅 |
| 2026-10-26 | Draft | 미해결 질문 결정 — Tailscale+SSH 확정, Tailscale 인증은 **OIDC(Workload Identity Federation)**, `@DevOnly`는 만들되 즉시 탈퇴 API 적용은 Cloudflare Tunnel 이후, pi2 `0.0.0.0:8080`, 카카오·애플 env 지원·JWT 분리·Discord 같은 웹훅. 네트워크 실측값 반영(pi `192.168.0.11`, pi2 `192.168.0.5`, 같은 공유기·DHCP 예약) |
| 2026-10-26 | **Approved** | 승인 — Issue #82 브랜치에서 구현 |
| 2026-10-26 | Approved | 구현 반영 — 호스트 키 검증을 첫 배포부터 적용(러너 OpenSSH + `known_hosts` 고정, Secret `PI2_SSH_HOST_KEY`; appleboy 대신), 배포 스크립트 `deploy/deploy-dev.sh`(헬스체크 실패 시 직전 성공 태그로 **자동 롤백**), `workflow_dispatch`의 `image_tag`로 수동 재배포·롤백, 복구 절차 문서화. 사용자 설정 상태 갱신(pi DB 개방·확인 완료). 로컬 검증 완료, 실배포 검증은 머지 후 |
| 2026-10-26 | Approved | Cloudflare Tunnel(`dev-api.stillstar.store`) 구성 완료 → pi2 app 포트 바인딩을 `127.0.0.1:8080`으로 전환(결정 사항 3). 운영 compose는 변경 없음 |

## 배경 / 목적

지금은 `main`·`develop` 둘 다 Lightsail(1GB) 한 대의 같은 `app`에 배포된다([ADR 0005](../adr/0005-hosting-aws-lightsail.md) "develop 자동 배포"). 서버 메모리 때문에 스테이징을 두지 못했고, 그래서 미완성 기능과 테스트용 API가 운영 서버에 그대로 올라간다.

가정용 Raspberry Pi 두 대로 개발 서버를 따로 세운다.

| 기기 | 역할 | 비고 |
|------|------|------|
| `pi` | PostgreSQL 16 + PostGIS 3.4 (`rodi-dev-postgres`, DB `rodi_dev`) | 로컬 DB 덤프 복원 완료(`flyway_schema_history` 포함). 추후 Minecraft 서버 동거 |
| `pi2` | Spring Boot API (`/opt/rodi-app`) | Pi 5 / 4GB / ARM64 / 16GB USB. 빌드는 하지 않고 이미지만 실행. 간헐 멈춤 이력 |

배포 흐름은 다음과 같이 바꾼다.

```
feat/* ──PR──▶ develop ──push──▶ cd-dev.yml ─ test → ARM64 이미지 → GHCR → pi2 배포 → health
develop ──PR──▶ main    ──push──▶ cd.yml     ─ (기존 그대로) test → amd64 이미지 → GHCR → Lightsail
```

## 요구사항

### 기능 요구사항

1. **브랜치별 배포 분리**: `develop` push는 pi2에만, `main` push는 Lightsail에만 배포한다.
2. **`dev` 프로필**: `SPRING_PROFILES_ACTIVE=dev`로 기동한다. DB는 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD` 환경변수로 받고, `ddl-auto: validate`, Flyway 활성, SQL 로그는 끈다. JWT·OAuth·알림 같은 공통 설정은 `application.yml`에서 그대로 상속한다.
3. **ARM64 이미지**: `develop` 이미지는 `linux/arm64`로 빌드해 GHCR에 push한다. 태그에는 커밋 SHA를 쓴다.
4. **pi2 배포**: 레포의 Compose 템플릿을 pi2로 보내고, 새 이미지로 `app`을 다시 띄운 뒤 헬스체크 통과를 확인한다. 실패하면 Actions 로그에서 컨테이너 로그를 볼 수 있어야 한다.
5. **내부 API 게이팅**: 테스트용·내부용 API는 `local`·`dev` 프로필에서만 빈이 만들어진다. `prod`에서는 엔드포인트 자체가 없다(404).

### 비기능 요구사항

- **인바운드 포트 공개 금지**: pi2의 SSH(22)나 어떤 포트도 인터넷에 공개하지 않는다(공유기 포트포워딩 없음).
- **운영 영향 없음**: `main`→Lightsail 배포 동작(이미지·compose·서버 스크립트)은 바뀌지 않는다. `local`·`prod` 프로필 동작도 그대로다. 즉시 탈퇴 API도 이번에는 그대로 둔다(결정 사항 2).
- **시크릿은 레포에 두지 않는다**: DB 비밀번호·JWT 시크릿 등은 pi2의 `/opt/rodi-app/.env`에만 둔다. 레포에는 키만 적은 예시 파일을 둔다.
- **최소 권한**: 워크플로 기본 권한은 `{}`로 두고, 잡별로 필요한 것만 연다(`contents: read`, push 잡만 `packages: write`).
- **메모리**: pi2(4GB) 기준 JVM 최대 힙 640MB, 컨테이너 상한 1GB에서 시작한다.

## 설계

### 1. 배포 연결 방식 — **Tailscale + SSH (추천)**

GitHub-hosted runner는 pi2의 사설 IP(`192.168.35.100`)에 직접 닿지 못한다. 세 가지 방식을 비교했다.

| | ① Self-hosted runner (pi2) | ② Tailscale + SSH | ③ pi2가 GHCR을 pull (Watchtower·타이머) |
|---|---|---|---|
| 인바운드 포트 | 불필요 (runner가 GitHub로 아웃바운드 연결) | 불필요 (WireGuard 아웃바운드, NAT 통과) | 불필요 |
| **public 레포 위험** | **높음** — fork PR이 워크플로를 바꿔 pi2에서 임의 코드를 실행할 수 있다. runner는 docker 그룹이라 사실상 root. GitHub도 public 레포에서는 권장하지 않는다 | 낮음 — fork PR에는 시크릿(Tailscale 키)이 전달되지 않아 tailnet에 들어올 수 없다 | 낮음 — CI가 서버에 접속하지 않는다 |
| 배포 결과 확인 | Actions에서 바로 | Actions에서 바로 (SSH로 health·로그) | **Actions는 성공해도 실제 배포 여부를 모른다.** 실패 로그는 pi2에만 남는다 |
| 기존 방식과의 일관성 | 별도 방식 | **운영 `cd.yml`과 같은 `scp`/`ssh` 패턴** | 별도 방식 |
| pi2 부담 | runner 상주 + 자동 업데이트 | `tailscaled` 상주(가벼움) | 폴링 에이전트 상주 |
| pi2 다운 시 | 잡이 runner를 기다리며 큐에 쌓임 | SSH 실패로 바로 실패 | 다음 폴링까지 지연, 알 수 없음 |
| 추가 설정 | runner 등록·서비스화 | Tailscale 계정·ACL 태그·OIDC credential | Watchtower 등 서드파티 |
| 부가 이득 | — | 맥북에서 pi·pi2에 어디서든 SSH 접근 가능 | — |

**결정: ②.** 이 레포는 **public**이라 ①은 위험하고, ③은 "배포 성공 여부를 Actions에서 확인" 요구를 채우지 못한다. ②는 인바운드 포트 없이 운영과 같은 SSH 배포 패턴을 그대로 쓸 수 있다.

- 워크플로가 `tailscale/github-action@v4`로 **임시(ephemeral) 노드**로 tailnet에 들어온다(태그 `tag:ci`). 잡이 끝나면 노드가 사라진다.
- **인증은 OIDC(Workload Identity Federation)**. GitHub가 잡마다 발급하는 OIDC 토큰을 Tailscale이 검증하므로 장기 비밀값(OAuth Client Secret)을 두지 않는다. Tailscale 쪽 subject를 `repo:Central-MakeUs/Rodi-Server:ref:refs/heads/develop`로 묶어 **이 레포의 develop 실행만** 접속할 수 있다. deploy 잡에 `id-token: write`가 필요하고, 잡에 `environment:`를 붙이지 않는다(붙이면 subject 형식이 바뀐다).
- Tailscale ACL로 `tag:ci`는 **`tag:server`(pi2)의 TCP 22만** 접근하게 제한한다.
- SSH는 Tailscale SSH가 아닌 **pi2의 OpenSSH + 배포 전용 키**(사용자 `deploy`, docker 그룹)를 쓴다. 러너 기본 `ssh`/`scp`로 접속하고(서드파티 action에 키를 넘기지 않음), **`known_hosts`를 레포 시크릿 `PI2_SSH_HOST_KEY`(pi2 공개 호스트 키)로 고정해 첫 배포부터 호스트 키를 검증**한다(`StrictHostKeyChecking yes`). 값이 없으면 배포 전에 실패한다.

### 2. 이미지 빌드 — ARM 호스티드 러너에서 네이티브 빌드

- build-push 잡을 **`ubuntu-24.04-arm`**(public 레포는 무료)에서 돌려 `linux/arm64`를 네이티브로 빌드한다. QEMU 에뮬레이션이 없어 Gradle 빌드가 느려지지 않는다.
- **Dockerfile은 그대로 재사용**한다. 베이스 `eclipse-temurin:21-jdk`/`21-jre`는 arm64를 지원하고, 멀티스테이지·UID 1001 비루트·`JAVA_OPTS` 구조를 유지한다. 힙 기본값(`MaxRAMPercentage=70`)도 운영용으로 그대로 두고, pi2는 compose에서 `JAVA_OPTS`를 덮어쓴다.
- `.dockerignore`에 `src/test`·`src/dev`·`deploy`·`db`·`.github`·`compose.yaml`을 추가한다. bootJar는 `src/main`만 쓰므로, 테스트 코드만 바뀌었을 때 이미지 레이어 캐시가 깨지지 않고 빌드 컨텍스트도 줄어든다(최종 이미지에는 jar만 들어가 크기는 그대로).
- **태그**: `:dev-<sha>`(배포용, 고정)와 `:develop`(이동 태그). 운영 `:<sha>`·`:main`과 접두어로 구분해, 아키텍처가 다른 이미지가 같은 태그를 덮어쓰지 않게 한다.
- 레포가 private으로 바뀌면 ARM 러너가 유료가 된다. 그때는 `ubuntu-latest` + QEMU로 바꾸고, 빌드 스테이지를 `FROM --platform=$BUILDPLATFORM`으로 고쳐 Gradle만 네이티브로 돌린다(jar는 아키텍처와 무관).

### 3. 워크플로

**`cd-dev.yml` (신규)** — `develop` push / `workflow_dispatch`, concurrency `cd-dev`(운영 `cd-deploy`와 별개).

| 잡 | 러너 | 권한 | 내용 |
|----|------|------|------|
| `test` | ubuntu-latest | contents: read | `./gradlew build` (운영 `cd.yml`과 동일) |
| `build-push` | ubuntu-24.04-arm | contents: read, packages: write | buildx로 `linux/arm64` 빌드 → GHCR push (`dev-<sha>`, `develop`), GHA 캐시(scope 분리) |
| `deploy` | ubuntu-latest | contents: read, packages: read, **id-token: write** | ① Tailscale 접속(OIDC) ② **사전 점검**: `tailscale status`·pi2 22번 포트 도달(재시도)·SSH 접속(`true` 실행)이 모두 통과해야 다음으로 ③ `deploy/compose.dev.yaml` → pi2 `/opt/rodi-app` 전송 ④ SSH: GHCR 로그인(잡의 `GITHUB_TOKEN`) → `pull` → `up -d --wait`(헬스체크 통과까지 대기, 최대 5분) → 로그아웃 → 이미지 정리 ⑤ **실패 시** SSH로 `docker compose ps`·`logs --tail 200` 출력 |

- 사전 점검(②)을 두는 이유: 네트워크·인증 문제(Tailscale 미접속, pi2 다운, 키 불일치)를 배포 단계 실패와 구분해 로그에서 바로 원인을 알 수 있게 한다.
- 배포 본체는 `deploy/deploy-dev.sh`(레포 관리, 매 배포 시 compose와 함께 전송). pull → `up -d --wait` → 헬스 확인 → 성공 태그 기록 → 현재·직전 태그만 남기고 이미지 정리. 실패하면 로그 출력 후 **직전 성공 태그로 자동 롤백**하고 잡은 실패로 끝난다(아래 "배포 실패와 복구").
- `workflow_dispatch` 입력 `image_tag`(예: `dev-<sha>`)를 주면 test·build를 건너뛰고 그 이미지를 배포한다(수동 롤백). OIDC subject가 develop이라 develop에서만 실행된다.
- 빌드 캐시는 `scope=dev-arm64`로 운영(amd64) 캐시와 분리한다.
- GHCR 로그인은 잡마다 발급되는 `GITHUB_TOKEN`을 쓰고 끝나면 `docker logout`한다. pi2에 PAT를 저장하지 않는다(패키지가 private이어도 동작).
- 배포 메타데이터(`DEPLOY_BRANCH`·`COMMIT`·`MESSAGE`·`REPO`)는 운영과 같은 방식(`envs`)으로 넘긴다. Discord 기동 알림의 환경 라벨은 `spring.profiles.active`라 자동으로 `[dev]`가 된다.

**`cd.yml` (최소 변경)** — 트리거에서 `develop`을 뺀다. 또 `workflow_dispatch`를 `main`이 아닌 브랜치에서 실행해도 운영에 배포되지 않도록 잡에 `if: github.ref == 'refs/heads/main'`을 둔다. 주석의 "main·develop 단일 app" 설명을 고친다. 그 밖의 스텝은 바꾸지 않는다.

`ci.yml`(PR 검사)은 바꾸지 않는다.

### 4. `dev` 프로필 — `application-dev.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
server:
  forward-headers-strategy: framework   # 추후 Cloudflare Tunnel(HTTPS 종단) 뒤에서 https 스킴 인식
```

- `prod`와 내용이 거의 같지만 프로필 그룹(`dev`가 `prod`를 포함)으로 합치지 않는다. 앞으로 `@Profile("prod")` 같은 분기가 생기면 개발 서버까지 운영으로 취급되기 때문이다. 10줄 정도의 중복은 감수한다.
- Flyway는 `application.yml` 공통 설정(활성, `baseline-on-migrate`)을 그대로 쓴다. 복원한 DB에는 `flyway_schema_history`가 있어 baseline은 적용되지 않고, 기본값 `validate-on-migrate`가 V1~V29 체크섬을 검증한다. 맞지 않으면 기동이 실패하므로 충돌이 조용히 넘어가지 않는다.

### 5. pi2 Compose — `deploy/compose.dev.yaml` + `deploy/.env.dev.example`

DB는 `pi`에 있으므로 `app` 서비스 하나만 둔다.

```yaml
services:
  app:
    image: ghcr.io/central-makeus/rodi-server:${IMAGE_TAG:-develop}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://${DB_HOST:?}:${DB_PORT:-5432}/${DB_NAME:-rodi_dev}
      DB_USERNAME: ${DB_USERNAME:?}
      DB_PASSWORD: ${DB_PASSWORD:?}
      JWT_SECRET: ${JWT_SECRET:?}
      # APPLE_*, KAKAO_ADMIN_KEY, DISCORD_*_WEBHOOK_URL, DEPLOY_* — 운영 compose와 같은 목록
      JAVA_OPTS: "-Xms256m -Xmx640m -XX:+ExitOnOutOfMemoryError"
    mem_limit: 1g
    ports:
      - "127.0.0.1:8080:8080"  # 외부는 Cloudflare Tunnel(dev-api.stillstar.store)로만. 2026-10-26까지는 0.0.0.0(LAN 테스트용)
    healthcheck:               # curl -fsS http://localhost:8080/actuator/health (JRE 이미지에 curl 포함 확인)
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 120s       # Pi는 기동·Flyway 검증이 느림
    logging:
      driver: json-file
      options: { max-size: "10m", max-file: "3" }   # 16GB USB 보호
```

- `DB_HOST`(pi의 IP, 현재 `192.168.0.11`)는 `.env`의 별도 변수로 둔다. 필수 값이 없으면 `:?`로 compose가 바로 실패한다.
- **메모리**: 힙 640MB + 메타스페이스·스레드·코드캐시를 합쳐 컨테이너 상한 1GB. 메모리가 부족하면 JVM을 바로 종료하고 `restart` 정책으로 다시 띄운다(반쯤 죽은 상태로 남지 않게).
- 운영 compose처럼 새 환경변수는 `environment`에 명시해야 컨테이너로 전달된다(`.env`가 자동 주입되지 않음).

### 6. 내부 API 게이팅

- 메타 애너테이션 **`@DevOnly`**(= `@Profile({"local", "dev"})`)를 `global`에 두고, 테스트용·내부용 컨트롤러에 붙인다. `prod`에서는 빈이 만들어지지 않아 엔드포인트가 404가 되고 Swagger에서도 빠진다.
- 경로는 기존 리소스 아래 그대로 둔다(경로 prefix로 나누지 않는다). 보안 설정은 바꾸지 않으며, JWT 인증도 그대로 필요하다.
- **이번에는 애너테이션과 테스트만 만든다.** 테스트는 테스트 소스에 둔 `@DevOnly` 샘플 컨트롤러로, `local`에서는 응답하고 `prod`에서는 404인지 확인한다.
- **`MemberHardDeleteController`(`DELETE /api/v1/members/me/hard`) 적용은 개발 서버 Cloudflare Tunnel 이후로 미룬다.** 터널 전에는 앱 debug 빌드가 붙을 수 있는 서버가 Lightsail뿐이라, 지금 막으면 테스트 흐름이 끊긴다. 그때까지 기존 동작(JWT 필요, 본인 계정만 삭제)을 유지하고, 이번 작업에서 그 인증 동작(토큰 없으면 401)이 테스트로 보장되는지 확인한다.
- 기존 `src/dev`(로컬 튜닝 페이지, jar 미포함)는 그대로 둔다. 로컬 전용 도구이고 개발 서버에 올릴 이유가 없다.
- 관리자 API는 운영에도 필요하므로 이 게이팅 대상이 아니다(권한 기반, 별도 결정).

### 7. 문서

- **ADR 0017** 작성: 배포 환경 분리(develop→pi2, main→Lightsail)와 Tailscale 선택. ADR 0005의 "develop 자동 배포" 절은 0017로 대체됐다고 표시한다.
- `README.md` 배포 절과 ADR 0005 참조 갱신.

### 8. 외부 연동 (개발 서버)

- 카카오·애플 로그인을 개발 서버에서도 테스트할 수 있게 `APPLE_*`·`KAKAO_ADMIN_KEY`를 compose에 전달한다. 실제 값은 pi2 `.env`에만 둔다.
- `JWT_SECRET`은 운영과 다른 값이다(개발 서버 토큰이 운영에서 통하지 않게).
- Discord는 운영과 **같은 웹훅**을 쓴다. 메시지 머리말이 활성 프로필이라 개발 서버는 `[Rodi][dev]`, 운영은 `[Rodi][prod]`로 구분된다(코드 변경 없음).

## 사용자 설정 (레포 밖)

Claude가 대신할 수 없는 설정이다. **PR 머지 전까지** 끝내야 deploy 잡이 성공한다(test·build-push는 설정 없이 동작).

| # | 항목 | 내용 | 상태 |
|---|------|------|------|
| 1 | 네트워크 | pi·pi2·맥북을 **같은 공유기**(`192.168.0.x`)로 모으고 DHCP 예약 — pi `192.168.0.11`, pi2 `192.168.0.5`. 포트포워딩 없음 | 완료 |
| 2 | pi DB 개방 | 바인딩 `192.168.0.11:5432:5432`, `DOCKER-USER` 체인으로 5432는 `192.168.0.5`만 허용(systemd `rodi-db-firewall`), pg_hba `scram-sha-256` 확인 | **완료** |
| 3 | DB 확인 | pi2에서 `psql -h 192.168.0.11` 접속(`place` 118건), 맥북에서는 5432 차단, `flyway_schema_history` 최신 29·전부 성공 | **완료** |
| 4 | pi2 준비 | `deploy` 사용자(docker 그룹)·`/opt/rodi-app` 소유권, 배포 SSH 키 등록, `/opt/rodi-app/.env`(`chmod 600`, `deploy/.env.dev.example` 참고), Wi-Fi 절전 끄기(`802-11-wireless.powersave 2`) | **`.env` 미작성** — 코드 머지 전 `.env.dev.example` 기준으로 작성 |
| 5 | Tailscale | pi2 태그 `tag:server`(Tailscale IP `100.81.233.83`). ACL: `tag:ci` → `tag:server` TCP 22만. OIDC credential: Issuer GitHub, Subject `repo:Central-MakeUs/Rodi-Server:ref:refs/heads/develop`, scope `auth_keys` 쓰기, 태그 `tag:ci` | 완료 |
| 6 | GitHub | Repository **Secrets**(전부 Secrets, Variables 사용 안 함): `TS_OAUTH_CLIENT_ID`·`TS_AUDIENCE`·`PI2_SSH_KEY`·`PI2_HOST`(`100.81.233.83`)·`PI2_SSH_USER`(`deploy`)·**`PI2_SSH_HOST_KEY`**(pi2의 `cat /etc/ssh/ssh_host_ed25519_key.pub` 출력 한 줄 — 지문 `SHA256:wmEh…n0PE`과 일치 확인). `TS_OAUTH_SECRET`은 쓰지 않는다 | **`PI2_SSH_HOST_KEY` 추가 필요** |
| 7 | 카카오 | 허용 IP에 집 공인 IP 추가(pi2에서 `curl -s ifconfig.me`). 가정 회선은 바뀔 수 있음 | 첫 배포 후 |
| 8 | (선택) 안정성 | 간헐 멈춤 원인 점검(`journalctl -b -1 -p err`, `vcgencmd get_throttled`), 하드웨어 watchdog | 선택 |

> `tag:server`는 범용 이름이라, 나중에 pi 등 다른 기기에도 같은 태그를 붙이면 CI가 그 기기의 22번에도 닿는다. 그때는 ACL 대상을 기기별 태그로 좁힌다.

## 배포 실패와 복구

**자동 롤백 (기본 동작)** — `deploy-dev.sh`가 `/opt/rodi-app/.last-successful-tag`에 마지막으로 헬스체크를 통과한 태그를 기록한다. 새 이미지가 `--wait`(최대 5분) 안에 `healthy`가 되지 못하면:
1. 실패한 컨테이너의 상태·로그(최근 200줄)를 출력한다 → Actions 로그에서 원인 확인.
2. 기록된 태그로 `up -d --wait` → 서비스 복구. Discord에는 롤백된 버전의 기동 알림이 간다.
3. 잡은 **실패**로 끝난다(롤백됐어도 배포는 실패했으므로).

첫 배포처럼 기록된 태그가 없으면 롤백하지 않고 실패만 한다.

**수동 롤백·재배포**
- (권장) Actions → `CD (dev · pi2)` → Run workflow(develop) → `image_tag`에 `dev-<sha>` 입력. GHCR에 있는 어떤 과거 이미지든 된다.
- pi2에서 직접(GHCR 없이, 남아 있는 직전 이미지로):
  ```bash
  cd /opt/rodi-app && PULL=false IMAGE_TAG=$(cat .last-successful-tag) bash deploy-dev.sh
  ```
  다른 과거 태그는 `docker login ghcr.io`(read:packages PAT) 후 `IMAGE_TAG=dev-<sha> bash deploy-dev.sh`.
- 가능한 태그 확인: `docker image ls ghcr.io/central-makeus/rodi-server`(pi2에는 현재·직전만 남음), 전체는 GitHub 패키지 페이지.

**자동 롤백의 한계 (Flyway)** — 롤백은 이미지만 되돌린다. 새 버전이 마이그레이션을 적용한 뒤 실패하면 DB는 새 스키마로 남는다. 이전 버전은 Flyway가 모르는 마이그레이션을 무시(`*:future` 기본값)해 기동하지만, 컬럼 삭제·이름 변경처럼 호환되지 않는 변경이면 Hibernate `validate`에서 실패할 수 있다. 그때는 롤백도 실패로 보고되고, DB를 수동 복구해야 한다(pi에서 덤프 복원 또는 해당 마이그레이션 되돌리기). 운영(`cd.yml`)에는 자동 롤백을 넣지 않았다 — 개발 서버에서 충분히 쓴 뒤 같은 방식의 도입을 검토한다([ADR 0017](../adr/0017-dev-server-raspberry-pi-and-tailscale-deploy.md)).

## 완료 조건 (Acceptance Criteria)

**설정·코드 (로컬 검증)**
- [x] `local` 프로필로 기존처럼 동작한다(테스트 기본 프로필, `application-local.yml` 변경 없음).
- [x] `dev` 프로필로 기동 시 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 접속하고 SQL 로그가 나오지 않는다.
- [x] 로컬 DB를 덤프해 새 PostGIS 컨테이너에 복원한 뒤(`pi`와 같은 절차) `dev`로 기동하면, Flyway가 마이그레이션 없이 검증만 통과하고 `/actuator/health`가 `UP`이다.
- [x] `prod` 프로필 설정은 바뀌지 않는다(`application-prod.yml` diff 없음).
- [x] `@DevOnly` 샘플 컨트롤러가 `local`(테스트 기본)에서는 응답하고, `prod` 프로필 컨텍스트에서는 404다(테스트).
- [x] 즉시 탈퇴 API는 기존 동작 그대로다 — 토큰 없이 호출하면 401, 토큰이 있으면 본인 계정만 삭제(기존·보강 테스트).
- [x] `docker buildx build --platform linux/arm64`로 이미지가 빌드되고, 컨테이너가 UID 1001로 실행된다.
- [x] `compose.dev.yaml`로 띄운 컨테이너가 헬스체크 `healthy`가 되고, `docker compose stop` → `start` 후에도 다시 `healthy`가 된다.
- [x] `./gradlew build` 통과.

- [x] 헬스체크 실패 이미지를 배포하면 실패 로그를 남기고 직전 성공 태그로 자동 롤백한 뒤 실패(1)로 끝난다(로컬 재현).

**워크플로**
- [x] `cd.yml`은 `main` push에서만 실행되고, `develop` push에서는 실행되지 않는다.
- [ ] `develop` push 시 `cd-dev.yml`의 test → build-push가 통과하고, GHCR에 `dev-<sha>` arm64 이미지가 올라간다.
- [x] deploy 잡이 `id-token: write` 외에 불필요한 권한을 갖지 않고, `TS_OAUTH_SECRET`을 참조하지 않는다.
- [ ] (사용자 설정 완료 후) deploy 잡이 Tailscale 접속 → 22번 도달·SSH 사전 점검 → 배포 → 헬스체크 통과로 성공한다. 사전 점검이 실패하면 배포 스텝 전에 멈추고, 헬스체크가 실패하면 잡이 실패하며 로그에 컨테이너 로그가 남는다.
- [x] `main` push의 `cd.yml` 실행 내용(스텝·이미지·서버 스크립트)은 바뀌지 않는다.

## 결정 사항

1. **배포 연결**: Tailscale + SSH. CI는 배포할 때만 tailnet에 들어오고(ephemeral), 인증은 OIDC, 접근은 `tag:server`의 TCP 22로 제한한다.
2. **즉시 탈퇴 게이팅**: `@DevOnly`와 테스트만 이번에 만든다. 실제 적용은 개발 서버 Cloudflare Tunnel 이후에 한다(그전까지 기존 동작과 인증 유지).
3. **pi2 8080**: 처음엔 `0.0.0.0:8080`(LAN 테스트용, 포트포워딩 없음)으로 열었고, Cloudflare Tunnel(`dev-api.stillstar.store`, HTTPS) 구성 후 **`127.0.0.1:8080`으로 전환**했다. 외부 접근은 터널로만 하고, LAN의 다른 기기에서 8080으로 직접 접근하지 않는다. 배포 헬스체크는 컨테이너 내부(`healthcheck`·`compose exec ... curl`)에서 하므로 바인딩과 무관하다.
4. **외부 연동**: 카카오·애플 env 지원(값은 pi2 `.env`), JWT 시크릿 분리, Discord 같은 웹훅(`[dev]` 라벨로 구분).

## 미해결 질문

- 없음
