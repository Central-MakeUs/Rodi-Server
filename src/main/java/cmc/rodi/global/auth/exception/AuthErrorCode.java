package cmc.rodi.global.auth.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ResponseCode {
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_1", "유효하지 않은 access token입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_2", "유효하지 않은 refresh token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_3", "만료된 refresh token입니다."),
    REFRESH_TOKEN_REUSE_DETECTED(
            HttpStatus.UNAUTHORIZED, "AUTH_401_4", "토큰 재사용이 감지되어 모든 세션이 폐기되었습니다. 다시 로그인해주세요."),
    SOCIAL_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_401_5", "소셜 로그인 검증에 실패했습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_401_6", "인증이 필요합니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_400_1", "지원하지 않는 소셜 공급자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
