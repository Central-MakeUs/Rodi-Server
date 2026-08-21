package cmc.rodi.domain.practice.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PracticeErrorCode implements ResponseCode {
    NOT_PRACTICE_OWNER(HttpStatus.FORBIDDEN, "PRACTICE_403_1", "본인의 연습 항목만 변경·삭제할 수 있습니다."),
    SKIP_REASON_ALREADY_SET(HttpStatus.CONFLICT, "PRACTICE_409_1", "이미 등록한 미방문 사유는 변경할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
