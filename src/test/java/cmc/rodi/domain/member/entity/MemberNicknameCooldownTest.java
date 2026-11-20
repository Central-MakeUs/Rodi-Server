package cmc.rodi.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 닉네임 변경 30일 제한(스펙 020, #80) — 변경 시각 기록과 다음 변경 가능 시각의 경계. */
class MemberNicknameCooldownTest {

    private static final Duration COOLDOWN = Duration.ofDays(30);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 22, 15, 0);

    private static Member memberWithNickname(String nickname) {
        Member member = Member.createBySocial("cooldown@kakao.com");
        member.assignNickname(nickname);
        return member;
    }

    @Test
    @DisplayName("자동 부여만 받은 회원은 제한이 없다(첫 변경 허용)")
    void 첫_변경_제한_없음() {
        Member member = memberWithNickname("차근차근_토끼");

        assertThat(member.getNicknameChangedAt()).isNull();
        assertThat(member.nicknameChangeableAt(CHANGED_AT, COOLDOWN)).isNull();
    }

    @Test
    @DisplayName("다른 닉네임으로 바꾸면 변경 시각을 남긴다")
    void 변경_시각_기록() {
        Member member = memberWithNickname("차근차근_토끼");

        member.changeNickname("초보_탈출", CHANGED_AT);

        assertThat(member.getNickname()).isEqualTo("초보_탈출");
        assertThat(member.getNicknameChangedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    @DisplayName("바꾼 시각 + 30일 정각부터 다시 바꿀 수 있고, 1초 전에는 그 시각을 돌려준다")
    void 시각_기준_경계() {
        Member member = memberWithNickname("차근차근_토끼");
        member.changeNickname("초보_탈출", CHANGED_AT);
        LocalDateTime changeableAt = CHANGED_AT.plusDays(30);

        assertThat(member.nicknameChangeableAt(changeableAt.minusSeconds(1), COOLDOWN))
                .isEqualTo(changeableAt);
        assertThat(member.nicknameChangeableAt(changeableAt, COOLDOWN)).isNull();
    }

    @Test
    @DisplayName("같은 값·대소문자만 다른 값은 변경으로 세지 않는다 — 변경 시각 그대로")
    void 같은값_대소문자_미기록() {
        Member member = memberWithNickname("kim_초보");
        member.changeNickname("lee_초보", CHANGED_AT);

        member.changeNickname("lee_초보", CHANGED_AT.plusDays(3));
        member.changeNickname("LEE_초보", CHANGED_AT.plusDays(5));

        assertThat(member.getNickname()).isEqualTo("LEE_초보");
        assertThat(member.getNicknameChangedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    @DisplayName("대소문자 무시 비교 — 같거나 대소문자만 다르면 같은 닉네임")
    void 대소문자_무시_비교() {
        Member member = memberWithNickname("Kim_초보");

        assertThat(member.isSameNicknameIgnoringCase("kim_초보")).isTrue();
        assertThat(member.isSameNicknameIgnoringCase("Kim_초보")).isTrue();
        assertThat(member.isSameNicknameIgnoringCase("Kim_초보2")).isFalse();
    }
}
