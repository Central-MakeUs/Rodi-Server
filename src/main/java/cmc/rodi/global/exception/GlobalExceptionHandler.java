package cmc.rodi.global.exception;

import cmc.rodi.global.common.logging.TraceIdFilter;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.common.response.ResponseCode;
import cmc.rodi.global.common.slack.SlackNotifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리. 모든 예외를 공통 응답 형식으로 변환하고 서버에 로그를 남긴다. 실패 응답에는 traceId가 자동 포함(ApiResponse.error)되고, 미처리
 * 5xx는 Slack으로도 알린다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SlackNotifier slackNotifier;

    public GlobalExceptionHandler(SlackNotifier slackNotifier) {
        this.slackNotifier = slackNotifier;
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

    /** 미처리 예외 — 서버 ERROR 로깅 + Slack 알림, 응답엔 traceId만. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        slackNotifier.sendServerError(
                MDC.get(TraceIdFilter.TRACE_ID), e, request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
