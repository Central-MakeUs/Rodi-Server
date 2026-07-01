package cmc.rodi.global.common.response;

import org.springframework.http.HttpStatus;

/**
 * 성공/실패 응답 코드 공통 계약. {@link SuccessCode}, {@link cmc.rodi.global.exception.ErrorCode}가 구현한다.
 */
public interface ResponseCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
