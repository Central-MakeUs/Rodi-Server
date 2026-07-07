package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.RefreshTokenRepository;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 단계적 탈퇴 배치의 영속성 통합 테스트. deletedAt을 과거로 세팅해 배치를 돌리고, 실제 DB 재조회로 익명화(Day3)·소셜 식별자 해제(Day10)를 확인한다.
 * 공급자 revoke/unlink는 외부 호출이라 SocialClientResolver를 mock으로 대체한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberWithdrawalBatchIntegrationTest {

    @Autowired MemberWithdrawalBatchService batchService;
    @Autowired MemberRepository memberRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @MockitoBean SocialClientResolver socialClientResolver;

    private final SocialClient socialClient = mock(SocialClient.class);

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        socialAccountRepository.deleteAll();
        memberRepository.deleteAll();
        when(socialClientResolver.resolve(any())).thenReturn(socialClient);
    }

    @Test
    @DisplayName("익명화(Day3): 유예 경과 회원 개인정보 null·anonymizedAt 설정·공급자 revoke, 식별자는 유지")
    void 익명화_배치() {
        Member member = memberRepository.save(withdrawnMember(4)); // deletedAt = now-4d
        SocialAccount account = socialAccountRepository.save(kakaoAccount(member));

        batchService.anonymizeExpired();

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getAnonymizedAt()).isNotNull();
        assertThat(reloaded.getEmail()).isNull();

        SocialAccount reloadedAccount =
                socialAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloadedAccount.getProviderRefreshToken()).isNull();
        assertThat(reloadedAccount.getProviderNickname()).isNull();
        assertThat(reloadedAccount.getProviderProfileImageUrl()).isNull();
        assertThat(reloadedAccount.getProviderId()).isEqualTo("kakao-1"); // 식별자는 재가입 차단 위해 유지

        verify(socialClient).revoke(any());
    }

    @Test
    @DisplayName("해제(Day10): 재가입 기간 경과 회원의 소셜 식별자 삭제 → 동일 계정 재가입 가능")
    void 해제_배치() {
        Member member = memberRepository.save(withdrawnMember(11)); // deletedAt = now-11d
        socialAccountRepository.save(kakaoAccount(member));

        batchService.releaseExpired();

        assertThat(
                        socialAccountRepository.findByProviderAndProviderId(
                                SocialProvider.KAKAO, "kakao-1"))
                .isEmpty(); // 식별자 해제 → 재로그인 시 신규 가입 취급
    }

    private Member withdrawnMember(int daysAgo) {
        Member member = Member.createBySocial("user@kakao.com");
        member.withdraw(LocalDateTime.now().minusDays(daysAgo));
        return member;
    }

    private SocialAccount kakaoAccount(Member member) {
        return SocialAccount.builder()
                .member(member)
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-1")
                .email("user@kakao.com")
                .providerNickname("카카오닉")
                .providerProfileImageUrl("http://img.example/p.jpg")
                .providerRefreshToken("apple-refresh")
                .build();
    }
}
