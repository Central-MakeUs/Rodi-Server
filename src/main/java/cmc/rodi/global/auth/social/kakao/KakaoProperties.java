package cmc.rodi.global.auth.social.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오 연동 설정. accessToken 검증 방식이라 user-info URI만 사용(코드 교환 방식 전환 시 client-id 등 추가). */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoProperties(String userInfoUri) {}
