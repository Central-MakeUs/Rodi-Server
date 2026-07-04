package cmc.rodi.global.auth.social.apple;

import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sign in with Apple. 클라이언트가 전달한 authorizationCode를 client_secret(.p8 서명)으로 토큰 교환하고, 받은 id_token을
 * 애플 공개키(JWKS)로 검증해 회원 식별 정보를 얻는다. JWKS는 메모리 캐시하며, 모르는 kid면 재조회한다.
 */
@Component
public class AppleSocialClient implements SocialClient, Locator<Key> {

    private static final Logger log = LoggerFactory.getLogger(AppleSocialClient.class);

    private final RestClient restClient;
    private final AppleProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;

    private volatile Map<String, Key> jwksCache = Map.of();

    public AppleSocialClient(
            RestClient.Builder builder,
            AppleProperties properties,
            AppleClientSecretGenerator clientSecretGenerator) {
        this.restClient = builder.build();
        this.properties = properties;
        this.clientSecretGenerator = clientSecretGenerator;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }

    @Override
    public OAuthUserInfo verify(String authorizationCode) {
        AppleTokenResponse token = exchange(authorizationCode);
        Claims claims = verifyIdToken(token.idToken());
        return new OAuthUserInfo(
                SocialProvider.APPLE,
                claims.getSubject(),
                claims.get("email", String.class),
                null, // 애플은 닉네임 미제공
                null, // 애플은 프로필 이미지 미제공
                token.refreshToken());
    }

    /** client_secret(.p8 서명) 생성. 설정 오류를 명확히 로깅한다(서버 설정 문제 → 500). */
    private String generateClientSecret() {
        try {
            return clientSecretGenerator.generate();
        } catch (RuntimeException e) {
            log.error(
                    "애플 client_secret 생성 실패 — 설정 확인(APPLE_PRIVATE_KEY/KEY_ID/TEAM_ID/CLIENT_ID)",
                    e);
            throw e;
        }
    }

    /** authorizationCode → 애플 토큰 엔드포인트 교환(id_token + refresh_token 확보). */
    private AppleTokenResponse exchange(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        form.add("client_id", properties.clientId());
        form.add("client_secret", generateClientSecret());
        try {
            AppleTokenResponse response =
                    restClient
                            .post()
                            .uri(properties.tokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(AppleTokenResponse.class);
            if (response == null || response.idToken() == null) {
                log.warn(
                        "애플 토큰 교환 응답에 id_token 없음(refresh_token 존재? {})",
                        response != null && response.refreshToken() != null);
                throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
            }
            return response;
        } catch (RestClientResponseException e) {
            log.warn(
                    "애플 토큰 교환 실패: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        } catch (RestClientException e) {
            log.warn("애플 토큰 교환 오류", e);
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }
    }

    /** id_token 서명(JWKS)·iss·aud·exp 검증 후 클레임 반환. 실패 원인을 세분화해 로깅한다. */
    private Claims verifyIdToken(String idToken) {
        Claims claims;
        try {
            claims =
                    Jwts.parser()
                            .keyLocator(this)
                            .requireIssuer(properties.issuer())
                            .build()
                            .parseSignedClaims(idToken)
                            .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("애플 id_token 만료: exp={}", e.getClaims().getExpiration());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        } catch (SignatureException e) {
            log.warn("애플 id_token 서명 검증 실패(공개키 불일치 등): {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        } catch (JwtException | IllegalArgumentException e) {
            // issuer 불일치, 형식 오류, kid 미발견(키 null) 등
            log.warn("애플 id_token 검증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.clientId())) {
            log.warn(
                    "애플 id_token aud 불일치: expected={}, actual={}",
                    properties.clientId(),
                    claims.getAudience());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }
        log.debug("애플 id_token 검증 성공: sub={}", claims.getSubject());
        return claims;
    }

    /** JWS 헤더의 kid로 JWKS에서 공개키를 찾는다. 캐시에 없으면 한 번 재조회한다. */
    @Override
    public Key locate(Header header) {
        if (!(header instanceof JwsHeader jwsHeader)) {
            return null;
        }
        String kid = jwsHeader.getKeyId();
        Key key = jwksCache.get(kid);
        if (key == null) {
            jwksCache = fetchJwks();
            key = jwksCache.get(kid);
            if (key == null) {
                log.warn("애플 JWKS에 해당 kid 없음: kid={}, 사용가능 kid={}", kid, jwksCache.keySet());
            }
        }
        return key;
    }

    private Map<String, Key> fetchJwks() {
        String json = restClient.get().uri(properties.jwksUri()).retrieve().body(String.class);
        JwkSet jwkSet = Jwks.setParser().build().parse(json);
        Map<String, Key> keys = new HashMap<>();
        for (Jwk<?> jwk : jwkSet.getKeys()) {
            keys.put(jwk.getId(), jwk.toKey());
        }
        return keys;
    }
}
