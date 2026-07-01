package cmc.rodi.global.common.response;

import cmc.rodi.global.common.logging.TraceIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.slf4j.MDC;

/**
 * 모든 API의 공통 응답 래퍼. 성공/실패 모두 {@code isSuccess/code/message/data} 형태로 통일한다.
 * 실패 응답에는 요청 추적용 {@code traceId}가 함께 실린다(성공 시 생략).
 */
@Getter
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    /** 에러 응답에만 포함되는 요청 추적 ID(서버 로그와 대조용). 성공 응답에선 생략. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String traceId;

    private ApiResponse(boolean success, String code, String message, T data, String traceId) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(SuccessCode.OK, data);
    }

    public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
        return new ApiResponse<>(true, successCode.getCode(), successCode.getMessage(), data, null);
    }

    public static <T> ApiResponse<T> error(ResponseCode errorCode) {
        return error(errorCode, null);
    }

    /** 검증 실패 등 추가 데이터(필드 에러 등)를 실어 보낼 때. traceId는 MDC에서 자동 주입. */
    public static <T> ApiResponse<T> error(ResponseCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data,
                MDC.get(TraceIdFilter.TRACE_ID));
    }
}
