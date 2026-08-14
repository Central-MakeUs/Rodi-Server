package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 승인 상태 변경 결과(스펙 016). */
public record CourseApprovalResponse(
        @Schema(description = "코스 id") Long courseId,
        @Schema(description = "변경된 승인 상태") ApprovalStatus approvalStatus,
        @Schema(description = "마지막 승인 시각. 승인된 적 없으면 null") LocalDateTime approvedAt) {

    public static CourseApprovalResponse from(Course course) {
        return new CourseApprovalResponse(
                course.getId(), course.getApprovalStatus(), course.getApprovedAt());
    }
}
