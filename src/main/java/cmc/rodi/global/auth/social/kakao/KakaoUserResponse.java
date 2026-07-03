package cmc.rodi.global.auth.social.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 카카오 {@code /v2/user/me} 응답(필요 필드만). id = providerId, kakao_account.email = 선택 이메일. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(String email) {}

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }
}
