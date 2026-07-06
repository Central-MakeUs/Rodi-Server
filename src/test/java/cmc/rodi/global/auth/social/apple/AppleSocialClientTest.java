package cmc.rodi.global.auth.social.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleSocialClientTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String CLIENT_ID = "com.rodi.app";
    private static final String KID = "test-kid";
    private static final String SUB = "apple-sub-123";
    private static final String EMAIL = "user@privaterelay.appleid.com";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RSAPrivateKey idTokenSigningKey; // 애플 역할: id_token 서명
    private RSAPublicKey idTokenPublicKey; // JWKS로 공개
    private String jwksJson;

    private MockRestServiceServer server;
    private AppleSocialClient client;

    @BeforeEach
    void setUp() throws Exception {
        // 애플 id_token 서명/검증용 RSA 키
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").genKeyPair();
        idTokenSigningKey = (RSAPrivateKey) rsa.getPrivate();
        idTokenPublicKey = (RSAPublicKey) rsa.getPublic();
        jwksJson =
                MAPPER.writeValueAsString(
                        Map.of(
                                "keys",
                                List.of(Jwks.builder().key(idTokenPublicKey).id(KID).build())));

        // client_secret 서명용 EC(P-256) 개인키 → PKCS8 base64
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        String ecPrivateKeyBase64 =
                Base64.getEncoder().encodeToString(ecGen.genKeyPair().getPrivate().getEncoded());

        AppleProperties properties =
                new AppleProperties(
                        ISSUER,
                        TOKEN_URI,
                        JWKS_URI,
                        REVOKE_URI,
                        "TEAMID",
                        "KEYID",
                        CLIENT_ID,
                        ecPrivateKeyBase64);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client =
                new AppleSocialClient(
                        builder, properties, new AppleClientSecretGenerator(properties));
    }

    @Test
    @DisplayName("provider는 APPLE")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.APPLE);
    }

    @Test
    @DisplayName("검증 성공: 토큰 교환 후 id_token을 JWKS로 검증해 sub·email·refresh 매핑")
    void 검증_성공() {
        String idToken = idToken(ISSUER, CLIENT_ID, Instant.now().plus(1, ChronoUnit.HOURS));
        expectTokenExchange(idToken, "apple-refresh-token");
        expectJwks();

        OAuthUserInfo info = client.verify("auth-code");

        assertThat(info.provider()).isEqualTo(SocialProvider.APPLE);
        assertThat(info.providerId()).isEqualTo(SUB);
        assertThat(info.email()).isEqualTo(EMAIL);
        assertThat(info.providerRefreshToken()).isEqualTo("apple-refresh-token");
        assertThat(info.providerNickname()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("토큰 교환이 400이면 SOCIAL_VERIFICATION_FAILED")
    void 토큰교환_실패() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertSocialVerificationFailed();
    }

    @Test
    @DisplayName("id_token의 aud가 다르면 SOCIAL_VERIFICATION_FAILED")
    void aud_불일치() {
        String idToken =
                idToken(ISSUER, "com.someone.else", Instant.now().plus(1, ChronoUnit.HOURS));
        expectTokenExchange(idToken, "r");
        expectJwks();

        assertSocialVerificationFailed();
    }

    @Test
    @DisplayName("id_token이 만료됐으면 SOCIAL_VERIFICATION_FAILED")
    void 만료() {
        String idToken = idToken(ISSUER, CLIENT_ID, Instant.now().minus(1, ChronoUnit.HOURS));
        expectTokenExchange(idToken, "r");
        expectJwks();

        assertSocialVerificationFailed();
    }

    @Test
    @DisplayName("revoke: 저장된 refresh token으로 애플 revoke 호출")
    void revoke_성공() {
        server.expect(requestTo(REVOKE_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.revoke(socialAccount("apple-refresh-token"));

        server.verify();
    }

    @Test
    @DisplayName("refresh token이 없으면 revoke를 스킵한다(호출 없음)")
    void revoke_스킵() {
        client.revoke(socialAccount(null)); // 서버 기대 미등록 → 호출이 있으면 실패

        server.verify();
    }

    private SocialAccount socialAccount(String refreshToken) {
        return SocialAccount.builder()
                .provider(SocialProvider.APPLE)
                .providerId(SUB)
                .providerRefreshToken(refreshToken)
                .build();
    }

    private String idToken(String issuer, String audience, Instant expiration) {
        return Jwts.builder()
                .header()
                .keyId(KID)
                .and()
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .subject(SUB)
                .claim("email", EMAIL)
                .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiration))
                .signWith(idTokenSigningKey, Jwts.SIG.RS256)
                .compact();
    }

    private void expectTokenExchange(String idToken, String refreshToken) {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                "{\"id_token\":\""
                                        + idToken
                                        + "\",\"refresh_token\":\""
                                        + refreshToken
                                        + "\"}",
                                MediaType.APPLICATION_JSON));
    }

    private void expectJwks() {
        server.expect(requestTo(JWKS_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));
    }

    private void assertSocialVerificationFailed() {
        assertThatThrownBy(() -> client.verify("auth-code"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(AuthErrorCode.SOCIAL_VERIFICATION_FAILED));
    }
}
