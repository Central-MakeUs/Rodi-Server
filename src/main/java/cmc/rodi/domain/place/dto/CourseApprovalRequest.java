package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.ApprovalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 코스 승인 상태 변경 요청(스펙 016). 전이에 제약이 없어 어떤 상태로든 바꿀 수 있다. */
public record CourseApprovalRequest(
        @Schema(description = "바꿀 승인 상태") @NotNull ApprovalStatus approvalStatus) {}
