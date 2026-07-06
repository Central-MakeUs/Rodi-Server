package cmc.rodi.global.auth.social.apple;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플 로그인 설정. {@code issuer/token-uri/jwks-uri}는 고정값, {@code team-id/key-id/client-id/private-key}는
 * env로 주입한다. private-key(.p8)는 시크릿이므로 커밋 금지.
 */
@ConfigurationProperties(prefix = "oauth.apple")
public record AppleProperties(
        String issuer,
        String tokenUri,
        String jwksUri,
        String revokeUri,
        String teamId,
        String keyId,
        String clientId,
        String privateKey) {}
