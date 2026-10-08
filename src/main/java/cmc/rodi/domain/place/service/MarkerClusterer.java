package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.dto.MarkerClusterResponse.Bounds;
import cmc.rodi.domain.place.dto.MarkerClusterResponse.Cluster;
import cmc.rodi.domain.place.dto.MarkerClusterResponse.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 지도 마커 클러스터링 — 고정 그리드(스펙 021, ADR 0015). 검색 범위의 장소를 받아 확대 단계별 묶음을 만든다. DB를 모르는 순수 계산이다.
 *
 * <ul>
 *   <li><b>격자</b>: Web Mercator(EPSG:3857) 원점에 고정된 정사각형 칸. 단계 {@code k}의 칸 크기는 {@code 2^k}.
 *   <li><b>단계</b>: 화면 폭 {@code W}에 맞는 단계는 {@code round(log2(W / targetColumns)) = k}. 가장 넓은
 *       단계({@code maxCellLevel})부터 묶지 않는 폭 바로 위 단계까지, 그리고 맨 아래에 묶지 않는 단계. 검색 범위와 무관하게 항상 같은 구성이다.
 *   <li><b>묶기</b>: 칸에 장소가 2개 이상이면 클러스터, 1개면 개별 장소.
 *   <li><b>클러스터 위치</b>: 묶인 장소들의 중심점을 칸 가장자리에서 {@code clusterInsetRatio}만큼 안쪽으로 제한 — 이웃 원이 붙지 않는다.
 *   <li><b>bounds</b>: 묶인 장소들의 범위를 최소 {@code noClusterWidth / 2}까지 넓힌다 — 범위에 맞춰 확대하면 반드시 풀린다.
 * </ul>
 *
 * <p>설정만 바꿔 새로 만들 수 있다(로컬 튜닝 페이지가 슬라이더 값으로 계산할 때).
 */
@Component
public class MarkerClusterer {

    private static final double EARTH_RADIUS = 6_378_137.0;

    /** 경도 1도의 Web Mercator X 길이. X가 경도에 정비례해, 화면 X 폭 = 경도 폭 × 이 값이다. */
    public static final double X_PER_LNG_DEGREE = EARTH_RADIUS * Math.PI / 180.0;

    private static final double SQRT2 = Math.sqrt(2);

    private static final Comparator<Cluster> CLUSTER_ORDER =
            Comparator.comparingDouble(Cluster::lat).reversed().thenComparingDouble(Cluster::lng);

    private final MarkerClusterProperties properties;

    public MarkerClusterer(MarkerClusterProperties properties) {
        this.properties = properties;
    }

    /** 묶을 장소 — id와 좌표(SRID 4326). */
    public record Point(long id, double lat, double lng) {}

    /** 확대 단계별 묶음. 넓은 화면용 → 좁은 화면용, 경도 폭 범위가 0부터 무한대까지 빈틈없이 이어진다. */
    public List<Level> cluster(List<Point> points) {
        List<Projected> projected = points.stream().map(Projected::of).toList();
        List<Long> allIds = points.stream().map(Point::id).sorted().toList();
        double noClusterWidth = properties.noClusterWidth();

        List<Level> levels = new ArrayList<>();
        int top = properties.maxCellLevel();
        if (upperWidth(top) > noClusterWidth) {
            int lowest = top;
            while (upperWidth(lowest - 1) > noClusterWidth) {
                lowest--;
            }
            for (int k = top; k >= lowest; k--) {
                double lower = Math.max(upperWidth(k - 1), noClusterWidth);
                Double upper = k == top ? null : toLngSpan(upperWidth(k));
                levels.add(gridLevel(projected, Math.scalb(1.0, k), toLngSpan(lower), upper));
            }
        }
        // 묶지 않는 단계. 가장 넓은 단계가 이미 묶지 않는 폭 이하라 그리드 단계가 없으면 상한 없이 이 단계만 쓴다
        Double noClusterUpper = levels.isEmpty() ? null : toLngSpan(noClusterWidth);
        levels.add(new Level(0, noClusterUpper, List.of(), allIds));
        return levels;
    }

    /** 단계 k를 쓰는 화면 폭의 상한 — {@code targetColumns · 2^k · √2}. 단계 k의 하한은 단계 k−1의 상한이다. */
    private double upperWidth(int k) {
        return properties.targetColumns() * Math.scalb(1.0, k) * SQRT2;
    }

    private Level gridLevel(
            List<Projected> points, double cell, double minLngSpan, Double maxLngSpan) {
        Map<CellKey, List<Projected>> cells = new LinkedHashMap<>();
        for (Projected p : points) {
            CellKey key = new CellKey((long) Math.floor(p.x / cell), (long) Math.floor(p.y / cell));
            cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(p);
        }
        List<Cluster> clusters = new ArrayList<>();
        List<Long> placeIds = new ArrayList<>();
        cells.forEach(
                (key, members) -> {
                    if (members.size() == 1) {
                        placeIds.add(members.get(0).id);
                    } else {
                        clusters.add(toCluster(key, cell, members));
                    }
                });
        clusters.sort(CLUSTER_ORDER);
        placeIds.sort(Comparator.naturalOrder());
        return new Level(minLngSpan, maxLngSpan, clusters, placeIds);
    }

    private Cluster toCluster(CellKey key, double cell, List<Projected> members) {
        double sumX = 0;
        double sumY = 0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Projected p : members) {
            sumX += p.x;
            sumY += p.y;
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        // 위치: 중심점을 칸 안쪽 영역으로 제한
        double inset = cell * properties.clusterInsetRatio();
        double cellMinX = key.x * cell;
        double cellMinY = key.y * cell;
        double x = clamp(sumX / members.size(), cellMinX + inset, cellMinX + cell - inset);
        double y = clamp(sumY / members.size(), cellMinY + inset, cellMinY + cell - inset);

        // bounds: 묶인 장소 범위를 최소 크기까지 넓힌다(중앙 기준)
        double minSize = properties.minBoundsSize();
        double[] spanX = widen(minX, maxX, minSize);
        double[] spanY = widen(minY, maxY, minSize);
        Bounds bounds =
                new Bounds(toLat(spanY[0]), toLng(spanX[0]), toLat(spanY[1]), toLng(spanX[1]));
        return new Cluster(toLat(y), toLng(x), members.size(), bounds);
    }

    private static double[] widen(double min, double max, double minSize) {
        if (max - min >= minSize) {
            return new double[] {min, max};
        }
        double center = (min + max) / 2;
        return new double[] {center - minSize / 2, center + minSize / 2};
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double toLngSpan(double width) {
        return width / X_PER_LNG_DEGREE;
    }

    private static double toLng(double x) {
        return Math.toDegrees(x / EARTH_RADIUS);
    }

    private static double toLat(double y) {
        return Math.toDegrees(2 * Math.atan(Math.exp(y / EARTH_RADIUS)) - Math.PI / 2);
    }

    private record CellKey(long x, long y) {}

    /** Web Mercator로 투영한 장소. */
    private record Projected(long id, double x, double y) {
        static Projected of(Point p) {
            double x = EARTH_RADIUS * Math.toRadians(p.lng());
            double y = EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(p.lat()) / 2));
            return new Projected(p.id(), x, y);
        }
    }
}
