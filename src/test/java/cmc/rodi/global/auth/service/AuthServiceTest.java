package cmc.rodi.global.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.global.auth.vo.Tokens;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CREDENTIAL = "kakao-access-token";
    private static final String PROVIDER_ID = "123456789";
    private static final String EMAIL = "user@kakao.com";

    @Mock SocialClientResolver socialClientResolver;
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock MemberRepository memberRepository;
    @Mock TokenService tokenService;
    @Mock SocialClient socialClient;

    @InjectMocks AuthService authService;

    private void stubSocialVerification() {
        when(socialClientResolver.resolve(SocialProvider.KAKAO)).thenReturn(socialClient);
        when(socialClient.verify(CREDENTIAL))
                .thenReturn(new OAuthUserInfo(SocialProvider.KAKAO, PROVIDER_ID, EMAIL));
    }

    @Test
    @DisplayName("신규 가입: 닉네임 없이 회원·소셜계정을 만들고 isNewMember=true")
    void 신규_가입() {
        stubSocialVerification();
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.empty());
        Member savedMember = Member.createBySocial(EMAIL);
        when(memberRepository.save(any(Member.class))).thenReturn(savedMember);
        when(tokenService.issue(savedMember)).thenReturn(new Tokens("access-jwt", "refresh-raw"));

        TokenResponse response = authService.login(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(response.isNewMember()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-raw");

        // 회원은 닉네임 없이(온보딩에서 설정) email만으로 생성
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getNickname()).isNull();
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo(EMAIL);

        // 소셜 계정은 (provider, providerId)로 회원에 연결
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(accountCaptor.capture());
        SocialAccount account = accountCaptor.getValue();
        assertThat(account.getMember()).isSameAs(savedMember);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderId()).isEqualTo(PROVIDER_ID);

        verify(tokenService).issue(savedMember);
    }

    @Test
    @DisplayName("기존 회원 로그인: 가입 없이 토큰만 발급하고 isNewMember=false")
    void 기존_회원_로그인() {
        stubSocialVerification();
        Member existing = Member.createBySocial(EMAIL);
        SocialAccount account =
                SocialAccount.builder()
                        .member(existing)
                        .provider(SocialProvider.KAKAO)
                        .providerId(PROVIDER_ID)
                        .email(EMAIL)
                        .build();
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.of(account));
        when(tokenService.issue(existing)).thenReturn(new Tokens("access-jwt", "refresh-raw"));

        TokenResponse response = authService.login(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(response.isNewMember()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
        verify(tokenService).issue(existing);
    }

    @Test
    @DisplayName("재발급: TokenService.reissue에 위임하고 isNewMember=false")
    void 재발급() {
        when(tokenService.reissue("refresh-raw"))
                .thenReturn(new Tokens("new-access", "new-refresh"));

        TokenResponse response = authService.reissue("refresh-raw");

        assertThat(response.isNewMember()).isFalse();
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(tokenService).reissue("refresh-raw");
    }

    @Test
    @DisplayName("로그아웃: TokenService.logout에 위임한다")
    void 로그아웃() {
        authService.logout("refresh-raw");

        verify(tokenService).logout("refresh-raw");
    }
}
