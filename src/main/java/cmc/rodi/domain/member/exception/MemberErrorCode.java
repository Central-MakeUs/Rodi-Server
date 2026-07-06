package cmc.rodi.domain.member.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ResponseCode {
    WITHDRAWAL_LOCKED(HttpStatus.CONFLICT, "MEMBER_409_1", "이미 탈퇴 처리된 계정입니다. 재가입은 탈퇴 10일 후 가능합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
