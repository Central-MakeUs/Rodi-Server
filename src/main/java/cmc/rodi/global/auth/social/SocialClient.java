package cmc.rodi.global.auth.social;

import cmc.rodi.global.auth.entity.SocialProvider;

/**
 * 소셜 공급자별 검증 전략. {@code credential}은 공급자마다 다르다(카카오: access token, 애플: identity token 등). 새 공급자는 이
 * 인터페이스 구현을 추가하면 {@code SocialClientResolver}에 자동 등록된다(ADR 0008).
 */
public interface SocialClient {

    SocialProvider provider();

    OAuthUserInfo verify(String credential);
}
