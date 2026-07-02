# ADR 0005: 배포 인프라 및 CI/CD

## 상태 (Status)

Accepted (2026-07-02, CI/CD 파이프라인 추가 2026-07-03)

## 배경

MVP 백엔드를 배포할 호스팅을 정해야 한다. 제약과 요구는 다음과 같다.

- **1인 개발 · 비용 민감**: 고정비를 최대한 낮추되, 운영 중 서비스가 갑자기 내려가는 리스크는 피하고 싶다.
- **한국 사용자 · 위치 기반 서비스**: 지연이 낮으면 좋다. 단, 위치 연산 정확도는 서버 위치와 무관(PostGIS가 좌표를 계산)하므로 서버 리전은 *지연·컴플라이언스*에만 영향.
- **추후 결제 기능 가능성**: 개인정보·결제 데이터의 국외 이전(데이터 주권) 이슈를 피하려면 국내(서울) 배치가 유리.
- **기존 AWS 계정의 프리티어는 소진**됨 → AWS 프리티어(EC2·RDS 12개월 무료)는 사용 불가.
- **도메인 보유**: 가비아 `stillstar.store`(기존 프로젝트에서 재사용) → Route53 불필요.

## 결정

**AWS Lightsail(서울, ap-northeast-2) 단일 인스턴스에 Docker Compose로 전체 스택을 올린다.**

- 플랜: **$7 / 1GB / 2vCPU / 40GB SSD / 2TB 전송** (고정 IP·IPv4·트래픽이 정액에 포함).
- 구성: 한 박스에서 **Caddy(자동 HTTPS) → Spring Boot(app) → PostGIS(db)** 를 Docker Compose로 실행.
  - 외부 공개는 **80/443(Caddy)** 뿐. app(8080)·db(5432)는 내부 네트워크 전용.
- DB는 **RDS 대신 컨테이너 PostGIS**(비용). **ALB·NAT Gateway·RDS 제외**(비용).
- 도메인: 가비아 DNS에 `api.stillstar.store` A레코드 → Lightsail 고정 IP. Caddy가 Let's Encrypt로 인증서 자동 발급.
- 1GB 대응: **swap 2GB** + JVM 힙 제한. 이미지 빌드는 서버가 아닌 **GitHub Actions**에서 수행하여, 서버는 "실행"만 담당.

## CI/CD 파이프라인

배포 자동화는 **GitHub Actions**로 구성한다 (레포가 GitHub, 무료, 통합).

- **CI (`ci.yml`)**: `main`·`develop` 대상 PR에서 `./gradlew build`(컴파일·테스트·Spotless 검증). 브랜치 보호의 필수 통과 조건.
- **CD (`cd.yml`)**: `main` push 시 `test → build-push → deploy` 순 실행.
  - **이미지 빌드 → GHCR push** (`ghcr.io/central-makeus/rodi-server`, 태그 `latest` + git SHA로 롤백 대비). 인증은 Actions 내장 `GITHUB_TOKEN`.
  - **배포**: Lightsail에 SSH → `compose.prod.yaml`·`Caddyfile` 전송 후 `docker compose pull && up -d`.
- **시크릿**: `SSH_HOST`·`SSH_KEY`(GitHub Secrets). GHCR는 `GITHUB_TOKEN`.
- 빌드를 러너에서 수행하므로 1GB 서버는 실행만 담당(서버 부하↓).

## 결과

### 긍정적

- **월 ~$7(약 만원)** 고정. 고정 IP·스토리지·트래픽이 정액에 포함되어 EC2처럼 IPv4·EBS·egress가 따로 붙지 않음(EC2 동급 구성은 ~$22).
- 서울 리전 → **한국 사용자 최저 지연 + 데이터 국내 보관**(결제·개인정보 컴플라이언스 유리).
- 단일 박스 Docker Compose라 구성·배포가 단순. 스냅샷으로 통째 백업/이전 가능.

### 부정적

- **1GB RAM은 JVM+PostGIS에 빠듯** → swap·힙 튜닝 필요, 트래픽 증가 시 상향 필요.
- DB 자가운영(백업·패치·장애복구 직접). 관리형(RDS) 대비 운영 부담. — 이는 Lightsail이 아닌 "컨테이너 DB 선택"에서 오는 부담.
- Lightsail은 실행 중 **제자리 리사이즈 불가** → 업그레이드는 스냅샷 기반(아래 계획).

### 중립적

- 프리티어 소진으로 무료 선택지가 없어진 상황의 "유료 최소" 절충안.
- 백업 전략(스냅샷 vs `pg_dump→S3`), 이미지 공개범위(GHCR public/private)는 후속 결정.

## 향후 계획 (업그레이드 · 재검토)

- **스케일업(1GB → 2GB, $12)**: 스냅샷 생성 → 상위 플랜으로 새 인스턴스 생성 → **고정 IP 재연결** → 기존 삭제. 데이터·IP·도메인 유지, 다운타임 수 분. RAM 압박이 상시화되면 실행.
- **오라클 서울 무료 재검토**: 오라클 Always Free는 홈 리전 한정인데 현재 가입 시 **서울 홈 리전이 미표시**. 주기적으로 서울 홈이 열리는지 확인 → 열리면 $0 서울 이전 검토.
- **관리형 DB(RDS) 전환**: DB 운영 부담이 커지거나 가용성이 중요해지면 RDS로 분리 검토(비용 증가 감수).
- **트래픽 성장 시**: Lightsail 상위 플랜 → 필요 시 EC2 + ALB/RDS 정식 구성으로 이전.

## 검토한 대안

### 대안 1 — Oracle Cloud Always Free (일본 리전)

ARM 4코어/24GB RAM을 **영구 $0**로 제공해 RAM 여유가 크다. 그러나 (1) **서울 홈 리전 미표시** + Always Free는 홈 리전 한정이라 한국 무료 배치 불가, (2) 일본 배치 시 데이터 국외(결제·개인정보 컴플라이언스 부담), (3) 무료 계정 리스크(ARM 인스턴스 확보 실패·유휴 회수·계정 정지·무 SLA)로 실서비스 안정성이 낮아 선택하지 않음. (서울 홈이 열리면 재검토 — 위 향후 계획 참조.)

### 대안 2 — AWS EC2 + RDS

정석 구성이나 프리티어 소진으로 RDS가 유료(~$15/월)라 고정비 부담이 크고, 단일 개발자 MVP에는 과함. 트래픽 성장 후 재검토 대상.

### 대안 3 — AWS EC2 단일 박스 (Docker PostGIS)

Lightsail과 유사한 단일 박스 구성이나, **Public IPv4(~$3.6/월)·EBS·egress가 별도 과금**되어 동급(2GB) 기준 ~$22/월로 더 비싸고 네트워킹 설정도 번거로움. 동일 목적에 Lightsail이 더 저렴·단순해 선택하지 않음.

## 참조

- [ADR 0003](0003-region-hierarchy-and-postgis.md) (PostGIS — 위치 연산은 서버 리전과 무관)
- [ADR 0006](0006-code-quality-tools.md) (코드 품질·의존성 도구) · [ADR 0007](0007-flyway-db-migration.md) (Flyway)
- 워크플로: [ci.yml](../../.github/workflows/ci.yml) · [cd.yml](../../.github/workflows/cd.yml)
- 배포 구성: [deploy/compose.prod.yaml](../../deploy/compose.prod.yaml) · [deploy/Caddyfile](../../deploy/Caddyfile)
- [compose.yaml](../../compose.yaml) (로컬 개발 DB — prod 구성과 별개)
- AWS Lightsail 요금 / Oracle Cloud Always Free 정책 (외부, 생성 시점 콘솔에서 확인)
