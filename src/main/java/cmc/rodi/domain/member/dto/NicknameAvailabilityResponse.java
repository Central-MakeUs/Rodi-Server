package cmc.rodi.domain.member.dto;

import cmc.rodi.global.common.response.ResponseCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

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
                                        + " MEMBER_400_4 금칙어 · MEMBER_400_5 30일 변경 제한 · MEMBER_409_3 중복",
                        example = "MEMBER_409_3")
                String reason,
        @Schema(description = "사용 불가 안내 문구(가능하면 null)", example = "이미 사용 중인 닉네임이에요.")
                String message,
        @Schema(
                        description =
                                "다음 닉네임 변경 가능 시각. 사유가 30일 변경 제한(MEMBER_400_5)일 때만 채우고, 그 밖에는 null",
                        example = "2026-11-21T15:00:00")
                LocalDateTime nicknameChangeableAt) {

    public static NicknameAvailabilityResponse available(String nickname) {
        return new NicknameAvailabilityResponse(nickname, true, null, null, null);
    }

    public static NicknameAvailabilityResponse unavailable(String nickname, ResponseCode reason) {
        return new NicknameAvailabilityResponse(
                nickname, false, reason.getCode(), reason.getMessage(), null);
    }

    /** 30일 변경 제한 — 다음 변경 가능 시각을 함께 준다. */
    public static NicknameAvailabilityResponse cooldown(
            String nickname, ResponseCode reason, LocalDateTime nicknameChangeableAt) {
        return new NicknameAvailabilityResponse(
                nickname, false, reason.getCode(), reason.getMessage(), nicknameChangeableAt);
    }
}
