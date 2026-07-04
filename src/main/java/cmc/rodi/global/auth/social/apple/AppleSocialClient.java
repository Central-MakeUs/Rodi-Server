package cmc.rodi.global.auth.social.apple;

import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
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

    /** authorizationCode → 애플 토큰 엔드포인트 교환(id_token + refresh_token 확보). */
    private AppleTokenResponse exchange(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        form.add("client_id", properties.clientId());
        form.add("client_secret", clientSecretGenerator.generate());
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

    /** id_token 서명(JWKS)·iss·aud·exp 검증 후 클레임 반환. */
    private Claims verifyIdToken(String idToken) {
        try {
            Claims claims =
                    Jwts.parser()
                            .keyLocator(this)
                            .requireIssuer(properties.issuer())
                            .build()
                            .parseSignedClaims(idToken)
                            .getPayload();
            if (claims.getAudience() == null
                    || !claims.getAudience().contains(properties.clientId())) {
                throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("애플 id_token 검증 실패", e);
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }
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
