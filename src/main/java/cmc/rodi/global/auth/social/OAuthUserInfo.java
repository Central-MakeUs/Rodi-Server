package cmc.rodi.global.auth.social;

import cmc.rodi.global.auth.entity.SocialProvider;

/**
 * 소셜 공급자 검증 결과. 회원 식별 기준은 {@code provider + providerId}. 나머지는 부가정보로 모두 nullable — 카카오는
 * 프로필(닉네임·이미지), 애플은 refresh token을 채우고 서로 없는 값은 null이다.
 */
public record OAuthUserInfo(
        SocialProvider provider,
        String providerId,
        String email,
        String providerNickname,
        String providerProfileImageUrl,
        String providerRefreshToken) {}
