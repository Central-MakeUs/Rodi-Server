#!/usr/bin/env bash
# 개발 서버(pi2) 배포 스크립트 — 스펙 023. /opt/rodi-app 에서 실행된다(cd-dev.yml이 매 배포 시 전송·실행).
#
#   IMAGE_TAG=dev-<sha> bash deploy-dev.sh      # 지정 태그로 배포(수동 롤백도 같은 방법)
#
#   PULL=false IMAGE_TAG=dev-<sha> bash deploy-dev.sh   # GHCR 없이 pi2에 남아 있는 이미지로(직전 태그는 정리하지 않고 남겨 둔다)
#
# 새 이미지가 헬스체크를 통과하지 못하면, 마지막으로 성공한 태그로 자동 롤백한 뒤 실패(1)로 끝난다.
# 롤백이 필요 없으면 AUTO_ROLLBACK=false.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rodi-app}"
IMAGE_REPO="ghcr.io/central-makeus/rodi-server"
STATE_FILE="$APP_DIR/.last-successful-tag"   # 마지막으로 헬스체크를 통과한 태그(롤백 대상)
WAIT_TIMEOUT="${WAIT_TIMEOUT:-300}"

: "${IMAGE_TAG:?IMAGE_TAG를 지정하세요 (예: dev-<sha>)}"
export IMAGE_TAG
cd "$APP_DIR"
compose() { docker compose -f compose.dev.yaml "$@"; }

prev="$(cat "$STATE_FILE" 2>/dev/null || true)"
echo "▶ 배포: $IMAGE_TAG (직전 성공: ${prev:-없음})"

if [[ "${PULL:-true}" == "true" ]]; then
    compose pull app
fi
# --wait: 컨테이너 헬스체크가 healthy가 될 때까지 대기, 실패·타임아웃이면 0이 아닌 값
if compose up -d --wait --wait-timeout "$WAIT_TIMEOUT" app; then
    compose exec -T app curl -fsS http://localhost:8080/actuator/health   # pi2 호스트에 curl이 없어도 되게 컨테이너 안에서
    echo
    echo "$IMAGE_TAG" >"$STATE_FILE"
    # 디스크(16GB USB) 보호: 현재·직전 태그만 남기고 이 레포 이미지 정리
    docker image ls "$IMAGE_REPO" --format '{{.Repository}}:{{.Tag}}' \
        | grep -vx -e "$IMAGE_REPO:$IMAGE_TAG" -e "$IMAGE_REPO:${prev:-none}" \
        | xargs -r docker image rm >/dev/null 2>&1 || true
    docker image prune -f >/dev/null
    echo "✅ 배포 성공: $IMAGE_TAG"
    exit 0
fi

echo "::error::$IMAGE_TAG 헬스체크 실패 — 컨테이너 상태·로그:"
compose ps app || true
compose logs --no-color --tail 200 app || true

if [[ "${AUTO_ROLLBACK:-true}" == "true" && -n "$prev" && "$prev" != "$IMAGE_TAG" ]]; then
    echo "↩ 직전 성공 태그로 롤백: $prev"
    if IMAGE_TAG="$prev" compose up -d --wait --wait-timeout "$WAIT_TIMEOUT" app; then
        echo "::warning::롤백 완료 — 지금 실행 중인 버전은 $prev"
    else
        echo "::error::롤백도 실패 — 서버 확인 필요"
        compose logs --no-color --tail 100 app || true
    fi
fi
exit 1
