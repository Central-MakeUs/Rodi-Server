package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalBatchServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock SocialClientResolver socialClientResolver;
    @Mock SocialClient socialClient;

    @InjectMocks MemberWithdrawalBatchService service;

    @Test
    @DisplayName("익명화: 공급자 연결 해제 후 개인정보 null·anonymizedAt 설정")
    void 익명화() {
        Member member = withdrawnMember("user@kakao.com");
        SocialAccount account = kakaoAccount(member, "refresh");
        when(memberRepository.findByDeletedAtBeforeAndAnonymizedAtIsNull(any()))
                .thenReturn(List.of(member));
        when(socialAccountRepository.findByMember(member)).thenReturn(List.of(account));
        when(socialClientResolver.resolve(SocialProvider.KAKAO)).thenReturn(socialClient);

        service.anonymizeExpired();

        verify(socialClient).revoke(account);
        assertThat(account.getProviderRefreshToken()).isNull(); // social_account 익명화
        assertThat(member.getEmail()).isNull(); // member 익명화
        assertThat(member.getAnonymizedAt()).isNotNull();
    }

    @Test
    @DisplayName("회원별 실패 격리: 한 회원 revoke 실패해도 다른 회원은 익명화")
    void 실패_격리() {
        Member failing = withdrawnMember("a@kakao.com");
        Member ok = withdrawnMember("b@kakao.com");
        SocialAccount failingAccount = kakaoAccount(failing, "r1");
        SocialAccount okAccount = kakaoAccount(ok, "r2");
        when(memberRepository.findByDeletedAtBeforeAndAnonymizedAtIsNull(any()))
                .thenReturn(List.of(failing, ok));
        when(socialAccountRepository.findByMember(failing)).thenReturn(List.of(failingAccount));
        when(socialAccountRepository.findByMember(ok)).thenReturn(List.of(okAccount));
        when(socialClientResolver.resolve(SocialProvider.KAKAO)).thenReturn(socialClient);
        doThrow(new BusinessException(AuthErrorCode.SOCIAL_UNLINK_FAILED))
                .when(socialClient)
                .revoke(failingAccount);

        service.anonymizeExpired();

        assertThat(failing.getAnonymizedAt()).isNull(); // 실패 → 익명화 안 됨(다음 회차 재시도)
        assertThat(ok.getAnonymizedAt()).isNotNull(); // 성공
    }

    @Test
    @DisplayName("해제: 재가입 기간 경과분의 소셜 식별자를 삭제한다")
    void 해제() {
        service.releaseExpired();

        verify(socialAccountRepository).deleteByMemberDeletedAtBefore(any(LocalDateTime.class));
    }

    private Member withdrawnMember(String email) {
        Member member = Member.createBySocial(email);
        member.withdraw(LocalDateTime.now().minusDays(4));
        return member;
    }

    private SocialAccount kakaoAccount(Member member, String refreshToken) {
        return SocialAccount.builder()
                .member(member)
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-123")
                .providerRefreshToken(refreshToken)
                .build();
    }
}
