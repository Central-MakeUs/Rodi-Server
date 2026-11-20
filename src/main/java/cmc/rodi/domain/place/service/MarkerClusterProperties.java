package cmc.rodi.domain.place.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지도 마커 클러스터링 설정(스펙 021). 화면을 보며 조정하는 값이라 코드 대신 설정({@code marker-cluster.*})으로 둔다. 길이 단위는 Web
 * Mercator(EPSG:3857) — 한국 위도에서 지상 거리의 약 1.25배다.
 *
 * @param targetColumns 화면 가로에 들어갈 목표 칸 수. 칸 크기는 2배 단위로 맞추므로 실제로는 약 0.7~1.4배
 * @param noClusterWidth 화면 폭이 이 이하면 묶지 않는다(3857 단위)
 * @param clusterInsetRatio 클러스터 위치를 칸 가장자리에서 칸 크기의 이 비율만큼 안쪽으로 제한한다(0~0.5)
 * @param maxCellLevel 가장 넓은 단계의 칸 크기 지수 — 칸 = 2^maxCellLevel. 한반도 전체 화면용
 */
@ConfigurationProperties(prefix = "marker-cluster")
public record MarkerClusterProperties(
        int targetColumns, double noClusterWidth, double clusterInsetRatio, int maxCellLevel) {

    public MarkerClusterProperties {
        if (targetColumns < 1) {
            throw new IllegalArgumentException("marker-cluster.target-columns는 1 이상이어야 합니다");
        }
        if (!(noClusterWidth > 0)) {
            throw new IllegalArgumentException("marker-cluster.no-cluster-width는 0보다 커야 합니다");
        }
        if (!(clusterInsetRatio >= 0 && clusterInsetRatio <= 0.5)) {
            throw new IllegalArgumentException("marker-cluster.cluster-inset-ratio는 0~0.5여야 합니다");
        }
        if (maxCellLevel < 0 || maxCellLevel > 25) {
            throw new IllegalArgumentException("marker-cluster.max-cell-level은 0~25여야 합니다");
        }
    }

    /** 클러스터 {@code bounds}의 최소 가로·세로. 묶지 않는 폭의 절반이라, 범위에 맞춘 화면이 앱 여백을 더해도 묶지 않는 단계에 든다. */
    public double minBoundsSize() {
        return noClusterWidth / 2;
    }
}
