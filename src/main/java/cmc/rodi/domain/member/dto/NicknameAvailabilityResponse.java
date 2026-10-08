package cmc.rodi.domain.member.dto;

import cmc.rodi.global.common.response.ResponseCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 사용 가능 여부(스펙 020). 규칙 위반도 "쓸 수 있는가"에 대한 정상적인 답이라 200으로 내리고, 사유는 닉네임 변경(PATCH) 에러와 같은 코드로 준다.
 */
public record NicknameAvailabilityResponse(
        @Schema(description = "정규화(NFC·앞뒤 공백 제거)된 닉네임. 그대로 변경 요청에 쓰면 같은 판정을 받는다", example = "초보_탈출")
                String nickname,
        @Schema(description = "사용 가능 여부") boolean available,
        @Schema(
                        description =
                                "사용 불가 사유 코드(가능하면 null). MEMBER_400_2 길이 · MEMBER_400_3 문자 ·"
                                        + " MEMBER_400_4 금칙어 · MEMBER_409_3 중복",
                        example = "MEMBER_409_3")
                String reason,
        @Schema(description = "사용 불가 안내 문구(가능하면 null)", example = "이미 사용 중인 닉네임이에요.")
                String message) {

    public static NicknameAvailabilityResponse available(String nickname) {
        return new NicknameAvailabilityResponse(nickname, true, null, null);
    }

    public static NicknameAvailabilityResponse unavailable(String nickname, ResponseCode reason) {
        return new NicknameAvailabilityResponse(
                nickname, false, reason.getCode(), reason.getMessage());
    }
}
