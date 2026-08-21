package cmc.rodi.domain.place.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 사용자 등록 코스 관련 오류(스펙 014~016).
 *
 * <p><b>미승인 코스는 여기 코드를 쓰지 않는다</b> — 공통 {@code COMMON_404}로 없는 장소와 똑같이 응답해 존재 여부를 흘리지 않는다. 반면 삭제는 이미
 * 담아둔 사용자에게 알려주는 게 목적이라 전용 코드로 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum CourseErrorCode implements ResponseCode {
    NOT_COURSE_OWNER(HttpStatus.FORBIDDEN, "COURSE_403_1", "본인이 등록한 코스만 삭제할 수 있습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE_404_1", "코스를 찾을 수 없습니다."),
    COURSE_DELETED(HttpStatus.NOT_FOUND, "COURSE_404_2", "삭제된 코스입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
