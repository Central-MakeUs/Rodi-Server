package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.review.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 후기 신고 요청. 사유는 필수이며, 신고 사유 폼(GET /reviews/report-form)의 선택지 코드를 그대로 보낸다. {@code OTHER}(기타)를 고르면 직접
 * 입력한 사유가 필수이고, 나머지 사유에서는 무시된다.
 */
public record ReviewReportRequest(
        @Schema(description = "신고 사유 코드") @NotNull ReportReason reason,
        @Schema(description = "직접 입력 사유(기타 선택 시 필수, 최대 100자)", example = "특정 지역 비하 표현이 있습니다.")
                @Size(max = ReportReason.TEXT_INPUT_MAX_LENGTH)
                String detail) {

    public ReviewReportRequest {
        // 직접 입력은 OTHER에서만 의미가 있다. 다른 사유로 온 detail은 저장하지 않는다.
        boolean acceptsDetail = reason != null && reason.requiresTextInput();
        detail = (!acceptsDetail || detail == null || detail.isBlank()) ? null : detail.trim();
    }

    /** 기타는 직접 입력이 반드시 있어야 한다. reason이 null이면 @NotNull이 먼저 잡는다. */
    @AssertTrue(message = "기타 사유는 직접 입력이 필요합니다")
    public boolean isDetailConsistent() {
        return reason == null || !reason.requiresTextInput() || detail != null;
    }
}
