package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** U1: 운전 목표 수정(마이페이지) — 반영·빈값 삭제·없는 회원 404를 실제 DB로 검증. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberProfileServiceIntegrationTest {

    @Autowired MemberProfileService memberProfileService;
    @Autowired MemberRepository memberRepository;

    @Test
    @DisplayName("운전 목표 수정이 저장되고 재조회 시 반영된다")
    void 운전목표_수정() {
        Member member = memberRepository.save(Member.createBySocial("goal@kakao.com"));

        memberProfileService.update(member.getId(), new MemberUpdateRequest("고속도로 합류 연습"));

        Member found = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(found.getDrivingGoal()).isEqualTo("고속도로 합류 연습");
    }

    @Test
    @DisplayName("빈값으로 수정하면 운전 목표가 삭제된다(null)")
    void 빈값이면_목표_삭제() {
        Member member = memberRepository.save(Member.createBySocial("clear@kakao.com"));
        member.applyOnboarding(Level.SEED, "기존 목표");
        memberRepository.save(member);

        memberProfileService.update(member.getId(), new MemberUpdateRequest("  ")); // 공백=빈값

        Member found = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(found.getDrivingGoal()).isNull();
    }

    @Test
    @DisplayName("없는 회원 수정 시 404")
    void 없는_회원_404() {
        assertThatThrownBy(
                        () -> memberProfileService.update(999_999L, new MemberUpdateRequest("목표")))
                .isInstanceOf(BusinessException.class);
    }
}
