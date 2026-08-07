package cmc.rodi.domain.review.exception;

import cmc.rodi.global.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ResponseCode {
    LEVEL_CHANGED(HttpStatus.CONFLICT, "REVIEW_409_1", "레벨이 변경되어 이전 레벨에서 작성한 후기는 수정할 수 없습니다."),
    LEVEL_REQUIRED(HttpStatus.CONFLICT, "REVIEW_409_2", "온보딩을 완료한 후 후기를 작성할 수 있습니다."),
    NOT_REVIEW_OWNER(HttpStatus.FORBIDDEN, "REVIEW_403_1", "본인이 작성한 후기만 수정·삭제할 수 있습니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "REVIEW_400_1", "본인이 작성한 후기는 신고할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
