package cmc.rodi.global.exception;

import cmc.rodi.global.common.logging.TraceIdFilter;
import cmc.rodi.global.common.notification.DiscordNotifier;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.common.response.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리. 모든 예외를 공통 응답 형식으로 변환하고 서버에 로그를 남긴다. 실패 응답에는 traceId가 자동 포함(ApiResponse.error)되고, 미처리
 * 5xx는 Discord로도 알린다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final DiscordNotifier discordNotifier;

    public GlobalExceptionHandler(DiscordNotifier discordNotifier) {
        this.discordNotifier = discordNotifier;
    }

    /** 비즈니스 예외 — 예측된 실패라 WARN 로깅. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ResponseCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorCode));
    }

    /**
     * @Valid 검증 실패 — 필드별 에러 메시지를 data에 담아 400 반환.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, fieldErrors));
    }

    /**
     * 읽을 수 없는 요청 본문(잘못된 JSON·무효 enum 값 등) — 클라이언트 입력 오류라 400 + WARN. 원인 메시지엔 요청 원문 값(민감정보 가능)이 섞일 수
     * 있어 예외 타입만 로깅한다(traceId는 로그 패턴이 자동 부착).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 요청 파라미터 누락·타입 불일치 — 클라이언트 입력 오류라 400 + WARN(서버 오류 알림 대상 아님). */
    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParam(Exception e) {
        log.warn("Invalid request parameter: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * 지원하지 않는 메서드(405)·매핑 없는 경로(404) — 클라이언트 오류라 4xx + WARN이다.
     *
     * <p>따로 잡지 않으면 아래 catch-all로 떨어져 <b>500 + Discord 서버오류 알림</b>이 된다. 엔드포인트를 없앤 뒤 구버전 앱이 그 경로를 계속
     * 부르면 호출마다 서버 장애로 잡히고, 정작 봐야 할 알림이 묻힌다.
     */
    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleUnmappedRequest(Exception e) {
        ErrorCode errorCode =
                (e instanceof HttpRequestMethodNotSupportedException)
                        ? ErrorCode.METHOD_NOT_ALLOWED
                        : ErrorCode.ENTITY_NOT_FOUND;
        log.warn("Unmapped request: {}", e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorCode));
    }

    /** 미처리 예외 — 서버 ERROR 로깅 + Slack 알림, 응답엔 traceId만. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        discordNotifier.sendServerError(
                MDC.get(TraceIdFilter.TRACE_ID), e, request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
