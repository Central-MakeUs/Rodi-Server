package cmc.rodi.global.auth.social.kakao;

import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 클라이언트가 전달한 카카오 access token을 {@code /v2/user/me}로 검증해 회원 식별 정보를 얻는다. */
@Component
public class KakaoSocialClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoSocialClient.class);

    private final RestClient restClient;
    private final String userInfoUri;

    public KakaoSocialClient(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.build();
        this.userInfoUri = properties.userInfoUri();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo verify(String accessToken) {
        KakaoUserResponse response;
        try {
            response =
                    restClient
                            .get()
                            .uri(userInfoUri)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .retrieve()
                            .body(KakaoUserResponse.class);
        } catch (RestClientResponseException e) {
            log.warn(
                    "카카오 사용자 조회 실패: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        } catch (RestClientException e) {
            log.warn("카카오 사용자 조회 오류", e);
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }

        if (response == null || response.id() == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_VERIFICATION_FAILED);
        }
        return new OAuthUserInfo(
                SocialProvider.KAKAO,
                String.valueOf(response.id()),
                response.email(),
                response.nickname(),
                response.profileImageUrl(),
                null); // 카카오는 refresh token 미저장
    }
}
