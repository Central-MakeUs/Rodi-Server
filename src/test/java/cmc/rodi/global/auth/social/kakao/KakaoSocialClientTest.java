package cmc.rodi.global.auth.social.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoSocialClientTest {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";
    private static final String ADMIN_KEY = "test-admin-key";
    private static final String ACCESS_TOKEN = "kakao-access-token";
    private static final String PROVIDER_ID = "123456789";

    private MockRestServiceServer server;
    private KakaoSocialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client =
                new KakaoSocialClient(
                        builder, new KakaoProperties(USER_INFO_URI, UNLINK_URI, ADMIN_KEY));
    }

    @Test
    @DisplayName("provider는 KAKAO")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("검증 성공: id·email을 OAuthUserInfo로 매핑하고 Bearer 헤더를 보낸다")
    void 검증_성공() {
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andRespond(
                        withSuccess(
                                "{\"id\":123456789,\"kakao_account\":{\"email\":\"user@kakao.com\"}}",
                                MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.verify(ACCESS_TOKEN);

        assertThat(info.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(info.providerId()).isEqualTo("123456789");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        server.verify();
    }

    @Test
    @DisplayName("이메일 미동의: email은 null, providerId는 정상 매핑")
    void 이메일_없음() {
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\":111}", MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.verify(ACCESS_TOKEN);

        assertThat(info.providerId()).isEqualTo("111");
        assertThat(info.email()).isNull();
    }

    @Test
    @DisplayName("카카오가 401을 주면 SOCIAL_VERIFICATION_FAILED")
    void 검증_실패_401() {
        server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertSocialVerificationFailed();
    }

    @Test
    @DisplayName("응답에 id가 없으면 SOCIAL_VERIFICATION_FAILED")
    void id_없음() {
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertSocialVerificationFailed();
    }

    @Test
    @DisplayName("revoke: Admin Key 헤더로 unlink 호출")
    void unlink_성공() {
        server.expect(requestTo(UNLINK_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        client.revoke(socialAccount());

        server.verify();
    }

    @Test
    @DisplayName("unlink 실패(4xx)면 SOCIAL_UNLINK_FAILED")
    void unlink_실패() {
        server.expect(requestTo(UNLINK_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.revoke(socialAccount()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(AuthErrorCode.SOCIAL_UNLINK_FAILED));
    }

    private SocialAccount socialAccount() {
        return SocialAccount.builder()
                .provider(SocialProvider.KAKAO)
                .providerId(PROVIDER_ID)
                .build();
    }

    private void assertSocialVerificationFailed() {
        assertThatThrownBy(() -> client.verify(ACCESS_TOKEN))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(AuthErrorCode.SOCIAL_VERIFICATION_FAILED));
    }
}
