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

    /**
     * 에러 응답의 {@code data}로 내보낼 값. 기본은 없음(null)이다.
     *
     * <p>에러에는 데이터를 싣지 않는 것이 기본이고([ADR 0013]), 앱이 실패 응답만으로 다음 행동을 정해야 하는 경우에만 <b>하위 예외가 재정의</b>해 값을
     * 준다 — 예: 닉네임 30일 제한의 다음 변경 가능 시각({@code NicknameChangeCooldownException}).
     */
    public Object getData() {
        return null;
    }

    /** 원인 예외를 보존해 던진다(디버깅용 stack chaining). */
    public BusinessException(ResponseCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
