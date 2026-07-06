package cmc.rodi.global.auth.social;

import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;

/**
 * 소셜 공급자별 검증·연결해제 전략. {@code credential}은 공급자마다 다르다(카카오: access token, 애플: identity token 등). 새
 * 공급자는 이 인터페이스 구현을 추가하면 {@code SocialClientResolver}에 자동 등록된다(ADR 0008).
 */
public interface SocialClient {

    SocialProvider provider();

    OAuthUserInfo verify(String credential);

    /** 탈퇴 시 공급자 연결 해제(애플 revoke·카카오 unlink). 실패 시 예외를 던진다(배치가 다음 회차에 재시도). */
    void revoke(SocialAccount account);
}
