package cmc.rodi.global.auth.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.jwt.TokenProvider;
import cmc.rodi.global.auth.service.RefreshTokenService.Rotation;
import cmc.rodi.global.auth.vo.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토큰 발급/재발급/폐기 파사드. access(JWT)와 refresh(DB) 계층을 묶어 AuthService·필터가 이 하나만 의존한다. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;

    /** 로그인/가입 시 access + refresh 발급. */
    @Transactional
    public Tokens issue(Member member) {
        String access = tokenProvider.createAccessToken(member.getId());
        String refresh = refreshTokenService.issue(member);
        return new Tokens(access, refresh);
    }

    /** refresh로 재발급(회전 + 재사용 탐지). 회전 과정에서 이미 회원을 찾으므로 함께 돌려준다 — 호출부가 응답을 채우려고 다시 조회하지 않게 한다. */
    @Transactional
    public Reissued reissue(String refreshToken) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        String access = tokenProvider.createAccessToken(rotation.member().getId());
        return new Reissued(new Tokens(access, rotation.rawRefreshToken()), rotation.member());
    }

    /** 재발급 결과 — 토큰과 그 주인. */
    public record Reissued(Tokens tokens, Member member) {}

    /** 로그아웃(해당 refresh 폐기). */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}
