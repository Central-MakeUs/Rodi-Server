package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.practice.entity.PracticeStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 방문 여부 상태 변경 요청. 상태와 GPS 측정값만 다룬다 — 미방문 사유는 별도 API(POST /practices/{id}/skip-reason)로 제출한다. */
public record PracticeStatusUpdateRequest(
        @Schema(
                        description = "바꿀 상태",
                        allowableValues = {"VISITED", "NOT_VISITED"},
                        example = "VISITED")
                @NotNull
                PracticeStatus status,
        @Schema(
                        description =
                                "앱이 GPS로 측정한 인정 주행거리(m). 코스 경로선 150m 이내에서 이동한 거리만."
                                        + " 측정 없이 다녀왔어요만 누른 경우 생략(=0). 인증 여부는 서버가 판정한다.",
                        example = "2100")
                @PositiveOrZero
                Integer certifiedDistanceMeters) {

    /** PLANNED로 되돌리는 건 [연습하기] 재등록이 하므로 상태 변경에서는 받지 않는다. */
    @JsonIgnore
    @Schema(hidden = true) // 검증 메서드일 뿐이라 요청 스키마에 노출하지 않는다
    @AssertTrue(message = "VISITED 또는 NOT_VISITED만 지정할 수 있습니다")
    public boolean isTransitionAllowed() {
        return status == null || status != PracticeStatus.PLANNED;
    }
}
