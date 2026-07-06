package cmc.rodi.global.auth.social.kakao;

import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.social.OAuthUserInfo;
import cmc.rodi.global.auth.social.SocialClient;
import cmc.rodi.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 카카오. 로그인은 access token을 {@code /v2/user/me}로 검증하고, 탈퇴는 Admin Key로 {@code /v1/user/unlink}를 호출해
 * 연결을 끊는다.
 */
@Component
public class KakaoSocialClient implements SocialClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoSocialClient.class);

    private final RestClient restClient;
    private final String userInfoUri;
    private final String unlinkUri;
    private final String adminKey;

    public KakaoSocialClient(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.build();
        this.userInfoUri = properties.userInfoUri();
        this.unlinkUri = properties.unlinkUri();
        this.adminKey = properties.adminKey();
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

    /** Admin Key + 회원번호(providerId)로 카카오 연결을 끊는다. */
    @Override
    public void revoke(SocialAccount account) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", account.getProviderId());
        try {
            restClient
                    .post()
                    .uri(unlinkUri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn(
                    "카카오 unlink 실패: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new BusinessException(AuthErrorCode.SOCIAL_UNLINK_FAILED);
        } catch (RestClientException e) {
            log.warn("카카오 unlink 오류", e);
            throw new BusinessException(AuthErrorCode.SOCIAL_UNLINK_FAILED);
        }
    }
}
