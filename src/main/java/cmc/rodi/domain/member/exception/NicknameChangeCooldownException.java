package cmc.rodi.domain.member.exception;

import cmc.rodi.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 닉네임 30일 변경 제한에 걸림(스펙 020, #80). 에러 응답 {@code data}에 다음 변경 가능 시각을 싣는다 — 앱이 확인 API 없이 변경 요청의 실패
 * 응답만으로 "N일 후 변경 가능"을 안내할 수 있게 하기 위해서다.
 */
public class NicknameChangeCooldownException extends BusinessException {

    private final LocalDateTime nicknameChangeableAt;

    public NicknameChangeCooldownException(LocalDateTime nicknameChangeableAt) {
        super(MemberErrorCode.NICKNAME_CHANGE_COOLDOWN);
        this.nicknameChangeableAt = nicknameChangeableAt;
    }

    @Override
    public Object getData() {
        return new CooldownData(nicknameChangeableAt);
    }

    /** {@code MEMBER_400_5} 에러 응답의 data. */
    public record CooldownData(
            @Schema(description = "다음 닉네임 변경 가능 시각", example = "2026-11-21T15:00:00")
                    LocalDateTime nicknameChangeableAt) {}
}
