package cmc.rodi.global.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;

/**
 * 비즈니스 규칙 위반 예외. {@link ResponseCode}를 담아 전역 핸들러가 응답으로 변환한다. 공통({@link ErrorCode})·도메인별 ErrorCode
 * enum 모두 던질 수 있다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResponseCode errorCode;

    public BusinessException(ResponseCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 원인 예외를 보존해 던진다(디버깅용 stack chaining). */
    public BusinessException(ResponseCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
