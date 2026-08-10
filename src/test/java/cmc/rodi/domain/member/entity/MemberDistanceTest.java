package cmc.rodi.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 누적 주행거리 반영과 승급 판정 — 두 단계 승급, 레벨 하향 없음, 온보딩 초기값. */
class MemberDistanceTest {

    private Member onboarded(Level level) {
        Member member = Member.createBySocial("distance@kakao.com");
        member.applyOnboarding(level, null);
        return member;
    }

    @Test
    @DisplayName("온보딩으로 부여된 레벨의 최소 기준값에서 시작한다")
    void 온보딩_초기값() {
        assertThat(onboarded(Level.SEED).getTotalDistanceMeters()).isZero();
        assertThat(onboarded(Level.ROOKIE).getTotalDistanceMeters()).isEqualTo(50_000);
        assertThat(onboarded(Level.NAVIGATOR).getTotalDistanceMeters()).isEqualTo(600_000);
    }

    @Test
    @DisplayName("기준에 도달하면 승급하고, 도달 전에는 거리만 쌓인다")
    void 승급() {
        Member member = onboarded(Level.SEED);

        assertThat(member.addDistance(49_999)).isFalse();
        assertThat(member.getLevel()).isEqualTo(Level.SEED);

        assertThat(member.addDistance(1)).isTrue(); // 50km 도달
        assertThat(member.getLevel()).isEqualTo(Level.ROOKIE);
        assertThat(member.getTotalDistanceMeters()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("한 번에 두 단계 이상 오르면 최종 도달 레벨이 된다")
    void 다단계_승급() {
        Member member = onboarded(Level.SEED);

        assertThat(member.addDistance(320_000)).isTrue();

        assertThat(member.getLevel()).isEqualTo(Level.EXPLORER); // SEED → EXPLORER
    }

    @Test
    @DisplayName("온보딩 레벨이 누적 거리보다 높아도 레벨은 내려가지 않는다")
    void 하향_없음() {
        Member member = Member.createBySocial("high@kakao.com");
        member.applyOnboarding(Level.NAVIGATOR, null); // 600km에서 시작
        long before = member.getTotalDistanceMeters();

        assertThat(member.addDistance(1_000)).isFalse(); // 이미 최상위
        assertThat(member.getLevel()).isEqualTo(Level.NAVIGATOR);
        assertThat(member.getTotalDistanceMeters()).isEqualTo(before + 1_000); // 누적은 계속된다
    }

    @Test
    @DisplayName("0 이하 거리는 누적도 승급도 없다(측정 없는 방문·주차장)")
    void 거리_없음() {
        Member member = onboarded(Level.ROOKIE);
        long before = member.getTotalDistanceMeters();

        assertThat(member.addDistance(0)).isFalse();
        assertThat(member.addDistance(-100)).isFalse();

        assertThat(member.getTotalDistanceMeters()).isEqualTo(before);
        assertThat(member.getLevel()).isEqualTo(Level.ROOKIE);
    }

    @Test
    @DisplayName("온보딩 전 회원은 거리만 쌓이고 레벨이 생기지 않는다")
    void 레벨_없는_회원() {
        Member member = Member.createBySocial("nolevel@kakao.com");

        assertThat(member.addDistance(100_000)).isFalse();

        assertThat(member.getLevel()).isNull();
        assertThat(member.getTotalDistanceMeters()).isEqualTo(100_000);
        assertThat(member.levelProgressPercent()).isZero();
    }
}
