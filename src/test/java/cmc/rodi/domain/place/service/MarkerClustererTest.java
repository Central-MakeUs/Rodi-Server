package cmc.rodi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import cmc.rodi.domain.place.dto.MarkerClusterResponse.Cluster;
import cmc.rodi.domain.place.dto.MarkerClusterResponse.Level;
import cmc.rodi.domain.place.service.MarkerClusterer.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 마커 클러스터링 순수 계산(스펙 021) — 단계 구성·칸 묶기·위치 제한·bounds 최소 크기. 좌표는 Web Mercator(3857) 기준으로 놓고 검증한다. */
class MarkerClustererTest {

    private static final double R = 6_378_137.0;
    private static final double X = MarkerClusterer.X_PER_LNG_DEGREE;
    private static final MarkerClusterProperties DEFAULTS =
            new MarkerClusterProperties(5, 1250, 0.25, 18);

    private final MarkerClusterer clusterer = new MarkerClusterer(DEFAULTS);

    /** 단계 k의 인덱스 — 가장 넓은 단계(18)가 0. */
    private static int index(int k) {
        return 18 - k;
    }

    @Nested
    @DisplayName("단계 구성")
    class Levels {

        @Test
        @DisplayName("기본값: 그리드 18~8과 묶지 않는 단계, 모두 12개를 넓은 순으로 준다")
        void 기본_12단계() {
            List<Level> levels = clusterer.cluster(List.of());

            assertThat(levels).hasSize(12);
            assertThat(levels.get(0).maxLngSpan()).isNull();
            assertThat(levels.get(11).minLngSpan()).isZero();
            assertThat(levels.get(11).maxLngSpan()).isCloseTo(1250 / X, within(1e-12));
        }

        @Test
        @DisplayName("경도 폭 범위가 빈틈·겹침 없이 이어진다")
        void 범위_연속() {
            List<Level> levels = clusterer.cluster(List.of());

            for (int i = 0; i + 1 < levels.size(); i++) {
                assertThat(levels.get(i + 1).maxLngSpan())
                        .isCloseTo(levels.get(i).minLngSpan(), within(1e-12));
                assertThat(levels.get(i).minLngSpan())
                        .isGreaterThan(levels.get(i + 1).minLngSpan());
            }
        }

        @Test
        @DisplayName("화면 폭 W는 round(log2(W / 5))번 단계에 든다 — 서울 시내(X 25,000)는 단계 12(칸 4,096)")
        void 화면폭_단계_선택() {
            Level level12 = clusterer.cluster(List.of()).get(index(12));
            double span = 25_000 / X;

            assertThat(span).isGreaterThanOrEqualTo(level12.minLngSpan());
            assertThat(span).isLessThan(level12.maxLngSpan());
            assertThat(level12.minLngSpan()).isCloseTo(5 * 4096 / Math.sqrt(2) / X, within(1e-12));
        }

        @Test
        @DisplayName("가장 좁은 묶는 단계의 하한은 묶지 않는 폭으로 잘린다")
        void 가장_좁은_단계_하한() {
            Level level8 = clusterer.cluster(List.of()).get(index(8));

            assertThat(level8.minLngSpan()).isCloseTo(1250 / X, within(1e-12));
            assertThat(level8.maxLngSpan()).isCloseTo(5 * 256 * Math.sqrt(2) / X, within(1e-12));
        }

        @Test
        @DisplayName("가장 넓은 칸이 묶지 않는 폭보다 작으면 묶지 않는 단계 하나뿐(상한 없음)")
        void 설정_반영() {
            MarkerClusterer narrow =
                    new MarkerClusterer(new MarkerClusterProperties(5, 1250, 0.25, 7));

            List<Level> levels = narrow.cluster(List.of(point(1, 100, 100), point(2, 120, 120)));

            assertThat(levels).hasSize(1);
            assertThat(levels.get(0).maxLngSpan()).isNull();
            assertThat(levels.get(0).placeIds()).containsExactly(1L, 2L);
        }
    }

    @Nested
    @DisplayName("묶기")
    class Grouping {

        @Test
        @DisplayName("같은 칸 3개는 클러스터(count 3), 다른 칸 1개는 개별 장소")
        void 클러스터와_개별() {
            List<Point> points =
                    List.of(
                            point(1, 1000, 1000),
                            point(2, 1500, 1200),
                            point(3, 2000, 2500),
                            point(4, 10_000, 10_000));

            Level level12 = clusterer.cluster(points).get(index(12));

            assertThat(level12.clusters()).hasSize(1);
            assertThat(level12.clusters().get(0).count()).isEqualTo(3);
            assertThat(level12.placeIds()).containsExactly(4L);
        }

        @Test
        @DisplayName("모든 단계에서 클러스터 count 합 + 개별 장소 수 = 장소 수")
        void 개수_불변() {
            Random random = new Random(76);
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                points.add(
                        new Point(
                                i,
                                33 + random.nextDouble() * 5.5,
                                125 + random.nextDouble() * 5.5));
            }

            for (Level level : clusterer.cluster(points)) {
                int clustered = level.clusters().stream().mapToInt(Cluster::count).sum();
                assertThat(clustered + level.placeIds().size()).isEqualTo(300);
            }
        }

        @Test
        @DisplayName("묶지 않는 단계에는 모든 장소가 개별로, id 오름차순으로 있다")
        void 묶지_않는_단계() {
            List<Point> points = List.of(point(2, 0, 0), point(1, 1, 1));

            Level last = clusterer.cluster(points).get(11);

            assertThat(last.clusters()).isEmpty();
            assertThat(last.placeIds()).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("장소가 1개면 모든 단계에서 개별 장소다")
        void 단일_장소() {
            for (Level level : clusterer.cluster(List.of(point(7, 500, 500)))) {
                assertThat(level.clusters()).isEmpty();
                assertThat(level.placeIds()).containsExactly(7L);
            }
        }

        @Test
        @DisplayName("격자 고정: 멀리 있는 장소가 더해져도 같은 칸의 클러스터는 같다")
        void 격자_고정() {
            List<Point> base = List.of(point(1, 1000, 1000), point(2, 1500, 1200));
            List<Point> more = new ArrayList<>(base);
            more.add(point(3, 900_000, 900_000));
            more.add(point(4, 900_100, 900_100));

            Cluster alone = clusterer.cluster(base).get(index(12)).clusters().get(0);

            assertThat(clusterer.cluster(more).get(index(12)).clusters()).contains(alone);
        }

        @Test
        @DisplayName("결정적: 입력 순서가 달라도 응답이 같다")
        void 결정적() {
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                points.add(point(i, (i * 7919) % 50_000, (i * 104_729) % 50_000));
            }
            List<Point> shuffled = new ArrayList<>(points);
            Collections.shuffle(shuffled, new Random(1));

            assertThat(clusterer.cluster(shuffled)).isEqualTo(clusterer.cluster(points));
        }
    }

    @Nested
    @DisplayName("클러스터 위치·bounds")
    class Position {

        @Test
        @DisplayName("중심점이 칸 안쪽에 있으면 그대로 중심점이다")
        void 중심점_그대로() {
            Cluster cluster =
                    clusterer
                            .cluster(List.of(point(1, 1800, 2000), point(2, 2200, 2000)))
                            .get(index(12))
                            .clusters()
                            .get(0);

            assertThat(xOf(cluster.lng())).isCloseTo(2000, within(1e-6));
            assertThat(yOf(cluster.lat())).isCloseTo(2000, within(1e-6));
        }

        @Test
        @DisplayName("공유 경계에 몰린 이웃 두 칸의 클러스터는 칸 크기의 절반 이상 떨어진다")
        void 경계_몰림_간격() {
            // 단계 12 칸(4,096): 왼쪽 칸 [0, 4096)의 오른쪽 끝, 오른쪽 칸 [4096, 8192)의 왼쪽 끝
            List<Point> points =
                    List.of(
                            point(1, 4000, 2000),
                            point(2, 4050, 2000),
                            point(3, 4150, 2000),
                            point(4, 4200, 2000));

            List<Cluster> clusters = clusterer.cluster(points).get(index(12)).clusters();

            assertThat(clusters).hasSize(2);
            List<Double> xs = clusters.stream().map(c -> xOf(c.lng())).sorted().toList();
            assertThat(xs.get(1) - xs.get(0)).isGreaterThanOrEqualTo(4096 / 2.0 - 1e-6);
            // 칸 가장자리에서 1/4 안쪽 경계로 당겨진다
            assertThat(xs.get(0)).isCloseTo(3072, within(1e-6));
            assertThat(xs.get(1)).isCloseTo(5120, within(1e-6));
        }

        @Test
        @DisplayName("bounds는 묶인 장소를 모두 포함한다")
        void bounds_포함() {
            Cluster cluster =
                    clusterer
                            .cluster(List.of(point(1, 100, 100), point(2, 3900, 3800)))
                            .get(index(12))
                            .clusters()
                            .get(0);

            assertThat(xOf(cluster.bounds().swLng())).isLessThanOrEqualTo(100 + 1e-6);
            assertThat(yOf(cluster.bounds().swLat())).isLessThanOrEqualTo(100 + 1e-6);
            assertThat(xOf(cluster.bounds().neLng())).isGreaterThanOrEqualTo(3900 - 1e-6);
            assertThat(yOf(cluster.bounds().neLat())).isGreaterThanOrEqualTo(3800 - 1e-6);
        }

        @Test
        @DisplayName("같은 좌표 2개의 bounds는 최소 크기(묶지 않는 폭의 절반)이고, 그 경도 폭은 묶지 않는 단계 범위에 든다")
        void bounds_최소_크기() {
            List<Level> levels =
                    clusterer.cluster(List.of(point(1, 1000, 1000), point(2, 1000, 1000)));
            Cluster cluster = levels.get(index(8)).clusters().get(0);

            double width = xOf(cluster.bounds().neLng()) - xOf(cluster.bounds().swLng());
            double height = yOf(cluster.bounds().neLat()) - yOf(cluster.bounds().swLat());
            assertThat(width).isCloseTo(625, within(1e-6));
            assertThat(height).isCloseTo(625, within(1e-6));

            double lngSpan = cluster.bounds().neLng() - cluster.bounds().swLng();
            assertThat(lngSpan).isLessThan(levels.get(11).maxLngSpan());
        }

        @Test
        @DisplayName("원 위치 제한이 0이면 경계에 몰려도 중심점 그대로다")
        void 위치_제한_0() {
            MarkerClusterer noInset =
                    new MarkerClusterer(new MarkerClusterProperties(5, 1250, 0, 18));

            Cluster cluster =
                    noInset.cluster(List.of(point(1, 4000, 2000), point(2, 4050, 2000)))
                            .get(index(12))
                            .clusters()
                            .get(0);

            assertThat(xOf(cluster.lng())).isCloseTo(4025, within(1e-6));
        }
    }

    @Test
    @DisplayName("설정 검증: 원 위치 제한 비율은 0~0.5, 칸 수는 1 이상")
    void 설정_검증() {
        assertThatThrownBy(() -> new MarkerClusterProperties(5, 1250, 0.6, 17))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarkerClusterProperties(0, 1250, 0.25, 17))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Web Mercator 좌표 (x, y)에 있는 장소. */
    private static Point point(long id, double x, double y) {
        double lng = Math.toDegrees(x / R);
        double lat = Math.toDegrees(2 * Math.atan(Math.exp(y / R)) - Math.PI / 2);
        return new Point(id, lat, lng);
    }

    private static double xOf(double lng) {
        return R * Math.toRadians(lng);
    }

    private static double yOf(double lat) {
        return R * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }
}
