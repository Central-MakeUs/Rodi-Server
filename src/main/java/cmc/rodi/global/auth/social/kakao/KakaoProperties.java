package cmc.rodi.global.auth.social.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 연동 설정. 검증은 accessToken 방식(user-info URI), 탈퇴 unlink는 Admin Key 방식(unlink URI + admin-key).
 * admin-key는 시크릿이므로 커밋 금지.
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoProperties(String userInfoUri, String unlinkUri, String adminKey) {}
