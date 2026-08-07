package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.practice.entity.VisitCertification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 방문 인증 필요 거리 = min(코스 전체 거리 × 40%, 5km). 기획 예시 표를 그대로 검증한다. */
class VisitCertificationTest {

    @ParameterizedTest(name = "코스 {0}m → 필요 {1}m")
    @CsvSource({
        "1300, 520", // 1.3km → 0.52km
        "2000, 800", // 2km → 0.8km
        "3800, 1520", // 3.8km → 1.52km
        "6600, 2640", // 6.6km → 2.64km
        "10400, 4160", // 10.4km → 4.16km
        "12500, 5000", // 12.5km → 정확히 상한
        "14800, 5000", // 14.8km → 5km 상한
        "40000, 5000" // 40km → 5km 상한
    })
    @DisplayName("필요 거리는 40%이되 5km를 넘지 않는다")
    void 필요_거리(int courseMeters, int expected) {
        assertThat(VisitCertification.requiredMeters(courseMeters)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "코스 {0}m, 주행 {1}m → 인증 {2}")
    @CsvSource({
        "2000, 799, false", // 필요 800m에 1m 모자람
        "2000, 800, true", // 정확히 도달
        "2000, 5000, true", // 초과 주행
        "40000, 4999, false", // 상한 5km에 모자람
        "40000, 5000, true" // 상한 도달
    })
    @DisplayName("인정 주행거리가 필요 거리에 도달해야 인증된다")
    void 인증_판정(int courseMeters, int certifiedMeters, boolean expected) {
        assertThat(VisitCertification.isCertified(courseMeters, certifiedMeters))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("주행거리가 없는 장소(주차장)는 인증 대상이 아니다")
    void 주행거리_없음() {
        assertThat(VisitCertification.requiredMeters(null)).isZero();
        assertThat(VisitCertification.isCertified(null, 10_000)).isFalse();
        assertThat(VisitCertification.isCertified(0, 10_000)).isFalse();
    }
}
