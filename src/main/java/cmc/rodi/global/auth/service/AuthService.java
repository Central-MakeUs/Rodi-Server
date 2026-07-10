package cmc.rodi.global.auth.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.MemberStatus;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.policy.WithdrawalPolicy;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.NicknameAssigner;
import cmc.rodi.global.auth.dto.SocialLoginResponse;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClientResolver;
import cmc.rodi.global.auth.vo.Tokens;
import cmc.rodi.global.exception.BusinessException;
import java.time.LocalDateTime;
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
    private final NicknameAssigner nicknameAssigner;

    /**
     * 소셜 로그인. 신규면 가입 후 토큰 발급, 기존이면 상태에 따라 분기 — ACTIVE는 로그인, 탈퇴 유예기간(PENDING)이면 토큰 대신 복구 안내, 유예 경과
     * (LOCKED)면 재가입 대기 에러.
     */
    @Transactional
    public SocialLoginResponse login(SocialProvider provider, String credential) {
        OAuthUserInfo userInfo = socialClientResolver.resolve(provider).verify(credential);

        SocialAccount account =
                socialAccountRepository
                        .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                        .orElse(null);

        if (account == null) {
            Member member = register(userInfo);
            return SocialLoginResponse.success(
                    tokenService.issue(member), true, member.getNickname());
        }

        Member member = account.getMember();
        MemberStatus state =
                member.withdrawalState(LocalDateTime.now(), WithdrawalPolicy.RECOVERABLE_WINDOW);

        if (state == MemberStatus.WITHDRAWAL_LOCKED) {
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_LOCKED);
        }
        if (state == MemberStatus.WITHDRAWAL_PENDING) {
            return SocialLoginResponse.withdrawalPending(
                    member.getDeletedAt(),
                    member.getDeletedAt().plus(WithdrawalPolicy.RECOVERABLE_WINDOW));
        }

        // ACTIVE: 재로그인 — 공급자 프로필·refresh token을 최신값으로 갱신(email은 가입 시 값 유지)
        account.updateProviderInfo(
                userInfo.providerRefreshToken(),
                userInfo.providerNickname(),
                userInfo.providerProfileImageUrl());
        return SocialLoginResponse.success(tokenService.issue(member), false, member.getNickname());
    }

    /**
     * 계정 복구. 탈퇴 유예기간(PENDING) 내 소셜 재검증으로 deletedAt을 해제하고 토큰을 발급한다. 유예 경과(LOCKED)면 재가입 대기 에러, 복구 대상이
     * 없으면 not-recoverable.
     */
    @Transactional
    public SocialLoginResponse restore(SocialProvider provider, String credential) {
        OAuthUserInfo userInfo = socialClientResolver.resolve(provider).verify(credential);

        SocialAccount account =
                socialAccountRepository
                        .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                MemberErrorCode.WITHDRAWAL_NOT_RECOVERABLE));

        Member member = account.getMember();
        MemberStatus state =
                member.withdrawalState(LocalDateTime.now(), WithdrawalPolicy.RECOVERABLE_WINDOW);

        if (state == MemberStatus.WITHDRAWAL_LOCKED) {
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_LOCKED);
        }
        if (state == MemberStatus.WITHDRAWAL_PENDING) {
            member.restore();
            account.updateProviderInfo(
                    userInfo.providerRefreshToken(),
                    userInfo.providerNickname(),
                    userInfo.providerProfileImageUrl());
        }
        // ACTIVE(이미 정상) 또는 방금 복구 → 로그인 토큰 발급
        return SocialLoginResponse.success(tokenService.issue(member), false, member.getNickname());
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

    /** 소셜 신규 가입. 후보 풀에서 닉네임을 부여해 회원을 만들고 소셜 계정을 연결한다. */
    private Member register(OAuthUserInfo userInfo) {
        Member member = Member.createBySocial(userInfo.email());
        member.assignNickname(nicknameAssigner.assign());
        memberRepository.save(member);
        socialAccountRepository.save(
                SocialAccount.builder()
                        .member(member)
                        .provider(userInfo.provider())
                        .providerId(userInfo.providerId())
                        .email(userInfo.email())
                        .providerRefreshToken(userInfo.providerRefreshToken())
                        .providerNickname(userInfo.providerNickname())
                        .providerProfileImageUrl(userInfo.providerProfileImageUrl())
                        .build());
        return member;
    }
}
