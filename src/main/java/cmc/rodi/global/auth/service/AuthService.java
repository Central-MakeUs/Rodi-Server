package cmc.rodi.global.auth.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.global.auth.vo.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 로그인·재발급·로그아웃 오케스트레이션. 검증→회원 조회/가입→토큰 발급을 하나의 흐름으로 묶는다. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialClientResolver socialClientResolver;
    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final TokenService tokenService;

    /** 소셜 로그인. 기존 계정이면 로그인, 없으면 신규 가입 후 토큰 발급. */
    @Transactional
    public TokenResponse login(SocialProvider provider, String credential) {
        OAuthUserInfo userInfo = socialClientResolver.resolve(provider).verify(credential);

        SocialAccount account =
                socialAccountRepository
                        .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                        .orElse(null);

        boolean isNewMember = account == null;
        Member member = isNewMember ? register(userInfo) : account.getMember();

        Tokens tokens = tokenService.issue(member);
        return TokenResponse.of(tokens, isNewMember);
    }

    /** refresh token으로 재발급(회전 + 재사용 탐지). 신규 가입이 아니므로 isNewMember=false. */
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        Tokens tokens = tokenService.reissue(refreshToken);
        return TokenResponse.of(tokens, false);
    }

    /** 로그아웃(해당 refresh token 세션만 폐기). */
    @Transactional
    public void logout(String refreshToken) {
        tokenService.logout(refreshToken);
    }

    /** 소셜 신규 가입. 닉네임 없이 회원을 만들고 소셜 계정을 연결한다(닉네임은 온보딩에서 설정). */
    private Member register(OAuthUserInfo userInfo) {
        Member member = memberRepository.save(Member.createBySocial(userInfo.email()));
        socialAccountRepository.save(
                SocialAccount.builder()
                        .member(member)
                        .provider(userInfo.provider())
                        .providerId(userInfo.providerId())
                        .email(userInfo.email())
                        .build());
        return member;
    }
}
