package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.service.RefreshTokenService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 탈퇴 요청의 영속성 통합 테스트. 실제 DB로 라운드트립해, deletedAt이 커밋되고 세션이 폐기되는지 확인한다. (mock 단위 테스트로는 잡히지 않던
 * clearAutomatically 유실 버그의 회귀 방지)
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberWithdrawalIntegrationTest {

    @Autowired MemberWithdrawalService withdrawalService;
    @Autowired MemberRepository memberRepository;
    @Autowired RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("탈퇴 시 deletedAt이 DB에 저장되고 refresh 세션이 폐기된다")
    void 탈퇴_영속성() {
        Member member = memberRepository.save(Member.createBySocial("user@kakao.com"));
        String refresh = refreshTokenService.issue(member);

        withdrawalService.withdraw(member.getId());

        // deletedAt이 실제 DB에 반영됐는지 재조회로 확인 (버그: clearAutomatically로 유실됐었음)
        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();

        // 세션(refresh) 폐기 확인 — 폐기된 토큰으로 회전(재발급) 시도는 실패
        assertThatThrownBy(() -> refreshTokenService.rotate(refresh))
                .isInstanceOf(BusinessException.class);
    }
}
