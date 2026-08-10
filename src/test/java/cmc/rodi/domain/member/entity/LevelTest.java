package cmc.rodi.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 레벨 임계값(0/50/150/300/600km)과 구간 진행률. 스펙 012의 표를 그대로 검증한다. */
class LevelTest {

    @ParameterizedTest(name = "{0}m → {1}")
    @CsvSource({
        "0, SEED",
        "49999, SEED",
        "50000, ROOKIE", // 경계 도달은 승급
        "149999, ROOKIE",
        "150000, OWNER",
        "300000, EXPLORER",
        "599999, EXPLORER",
        "600000, NAVIGATOR",
        "9999999, NAVIGATOR" // 최상위 위로는 더 없다
    })
    @DisplayName("누적 거리로 도달 레벨이 정해진다 — 경계값은 승급")
    void 도달_레벨(long meters, Level expected) {
        assertThat(Level.of(meters)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1}m → {2}%")
    @CsvSource({
        "SEED, 0, 0",
        "SEED, 25000, 50", // 0~50km 구간의 절반
        "ROOKIE, 100000, 50", // 스펙 예시: 루키 100km → 50%
        "ROOKIE, 50000, 0",
        "ROOKIE, 149999, 99", // 내림이라 100이 되지 않는다
        "OWNER, 225000, 50",
        "NAVIGATOR, 600000, 100", // 최상위는 계산 없이 100
        "NAVIGATOR, 812400, 100"
    })
    @DisplayName("진행률은 구간 비율을 내림한 값이고, 최상위는 항상 100이다")
    void 진행률(Level level, long meters, int expected) {
        assertThat(level.progressPercent(meters)).isEqualTo(expected);
    }

    @Test
    @DisplayName("다음 레벨과 목표 거리 — 최상위는 목표가 없다")
    void 다음_레벨() {
        assertThat(Level.ROOKIE.next()).isEqualTo(Level.OWNER);
        assertThat(Level.ROOKIE.nextStartMeters()).isEqualTo(150_000L);
        assertThat(Level.ROOKIE.isTop()).isFalse();

        assertThat(Level.NAVIGATOR.next()).isNull();
        assertThat(Level.NAVIGATOR.nextStartMeters()).isNull();
        assertThat(Level.NAVIGATOR.isTop()).isTrue();
    }
}
