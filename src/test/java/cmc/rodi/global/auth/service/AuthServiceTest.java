package cmc.rodi.global.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.NicknameAssigner;
import cmc.rodi.global.auth.dto.SocialLoginResponse;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.global.auth.vo.Tokens;
import cmc.rodi.global.exception.BusinessException;
import java.time.LocalDateTime;
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
    private static final String NICKNAME = "카카오닉";
    private static final String IMAGE_URL = "https://k.example/profile.jpg";
    private static final String ASSIGNED_NICKNAME = "차근차근 토끼";

    @Mock SocialClientResolver socialClientResolver;
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock MemberRepository memberRepository;
    @Mock TokenService tokenService;
    @Mock SocialClient socialClient;
    @Mock NicknameAssigner nicknameAssigner;

    @InjectMocks AuthService authService;

    private void stubSocialVerification() {
        when(socialClientResolver.resolve(SocialProvider.KAKAO)).thenReturn(socialClient);
        when(socialClient.verify(CREDENTIAL))
                .thenReturn(
                        new OAuthUserInfo(
                                SocialProvider.KAKAO,
                                PROVIDER_ID,
                                EMAIL,
                                NICKNAME,
                                IMAGE_URL,
                                null));
    }

    @Test
    @DisplayName("신규 가입: 후보 풀 닉네임을 부여해 회원·소셜계정을 만들고 isNewMember=true")
    void 신규_가입() {
        stubSocialVerification();
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.empty());
        when(nicknameAssigner.assign()).thenReturn(ASSIGNED_NICKNAME);
        when(tokenService.issue(any(Member.class)))
                .thenReturn(new Tokens("access-jwt", "refresh-raw"));

        SocialLoginResponse response = authService.login(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.SUCCESS);
        assertThat(response.isNewMember()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-raw");
        // 로그인 응답에 부여된 닉네임 포함
        assertThat(response.nickname()).isEqualTo(ASSIGNED_NICKNAME);

        // 회원은 후보 풀 닉네임 + email로 생성
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member saved = memberCaptor.getValue();
        assertThat(saved.getNickname()).isEqualTo(ASSIGNED_NICKNAME);
        assertThat(saved.getEmail()).isEqualTo(EMAIL);

        // 소셜 계정은 (provider, providerId)로 회원에 연결
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(accountCaptor.capture());
        SocialAccount account = accountCaptor.getValue();
        assertThat(account.getMember()).isSameAs(saved);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderId()).isEqualTo(PROVIDER_ID);
        // 공급자 프로필(카카오 닉네임·이미지)이 소셜 계정에 저장됨
        assertThat(account.getProviderNickname()).isEqualTo(NICKNAME);
        assertThat(account.getProviderProfileImageUrl()).isEqualTo(IMAGE_URL);

        verify(tokenService).issue(saved);
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

        SocialLoginResponse response = authService.login(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.SUCCESS);
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

    @Test
    @DisplayName("탈퇴 유예 중 로그인: 토큰 대신 WITHDRAWAL_PENDING 안내")
    void 탈퇴유예_로그인_복구안내() {
        stubSocialVerification();
        Member withdrawing = Member.createBySocial(EMAIL);
        withdrawing.withdraw(LocalDateTime.now().minusDays(1)); // 3일 이내 → PENDING
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.of(accountOf(withdrawing)));

        SocialLoginResponse response = authService.login(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.WITHDRAWAL_PENDING);
        assertThat(response.accessToken()).isNull();
        assertThat(response.withdrawalRequestedAt()).isNotNull();
        verify(tokenService, never()).issue(any());
    }

    @Test
    @DisplayName("탈퇴 유예 경과 로그인: WITHDRAWAL_LOCKED")
    void 탈퇴유예경과_로그인_잠금() {
        stubSocialVerification();
        Member withdrawn = Member.createBySocial(EMAIL);
        withdrawn.withdraw(LocalDateTime.now().minusDays(5)); // 3일 경과 → LOCKED
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.of(accountOf(withdrawn)));

        assertThatThrownBy(() -> authService.login(SocialProvider.KAKAO, CREDENTIAL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.WITHDRAWAL_LOCKED));
        verify(tokenService, never()).issue(any());
    }

    @Test
    @DisplayName("복구: 유예 중이면 deletedAt 해제 후 토큰 발급")
    void 복구_성공() {
        stubSocialVerification();
        Member withdrawing = Member.createBySocial(EMAIL);
        withdrawing.withdraw(LocalDateTime.now().minusDays(1)); // PENDING
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.of(accountOf(withdrawing)));
        when(tokenService.issue(withdrawing)).thenReturn(new Tokens("access-jwt", "refresh-raw"));

        SocialLoginResponse response = authService.restore(SocialProvider.KAKAO, CREDENTIAL);

        assertThat(withdrawing.isWithdrawn()).isFalse();
        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.SUCCESS);
        assertThat(response.accessToken()).isEqualTo("access-jwt");
    }

    @Test
    @DisplayName("복구: 유예 경과면 WITHDRAWAL_LOCKED")
    void 복구_잠금() {
        stubSocialVerification();
        Member withdrawn = Member.createBySocial(EMAIL);
        withdrawn.withdraw(LocalDateTime.now().minusDays(5)); // LOCKED
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.of(accountOf(withdrawn)));

        assertThatThrownBy(() -> authService.restore(SocialProvider.KAKAO, CREDENTIAL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.WITHDRAWAL_LOCKED));
    }

    @Test
    @DisplayName("복구: 대상 계정 없으면 WITHDRAWAL_NOT_RECOVERABLE")
    void 복구_대상없음() {
        stubSocialVerification();
        when(socialAccountRepository.findByProviderAndProviderId(SocialProvider.KAKAO, PROVIDER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.restore(SocialProvider.KAKAO, CREDENTIAL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.WITHDRAWAL_NOT_RECOVERABLE));
    }

    private SocialAccount accountOf(Member member) {
        return SocialAccount.builder()
                .member(member)
                .provider(SocialProvider.KAKAO)
                .providerId(PROVIDER_ID)
                .email(EMAIL)
                .build();
    }
}
