package cmc.rodi.global.auth.social;

import cmc.rodi.global.auth.entity.SocialProvider;

/** 소셜 공급자 검증 결과. 회원 식별 기준은 {@code provider + providerId}, email은 nullable. */
public record OAuthUserInfo(SocialProvider provider, String providerId, String email) {}
