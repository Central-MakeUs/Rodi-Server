package cmc.rodi.global.auth.social.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 {@code /v2/user/me} 응답(필요 필드만). id = providerId, kakao_account.email = 선택 이메일, profile =
 * 닉네임·프로필 이미지(동의 시).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(String email, Profile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {}

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }

    public String nickname() {
        return profile() == null ? null : profile().nickname();
    }

    public String profileImageUrl() {
        return profile() == null ? null : profile().profileImageUrl();
    }

    private Profile profile() {
        return kakaoAccount == null ? null : kakaoAccount.profile();
    }
}
