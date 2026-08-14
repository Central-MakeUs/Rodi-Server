package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;

/** 코스 등록 결과. 승인 전까지 전체 목록·검색에 나오지 않으므로 상태를 함께 내려준다. */
public record CourseRegisterResponse(
        @Schema(description = "등록된 코스 id") Long courseId,
        @Schema(description = "승인 상태(등록 직후 PENDING)") ApprovalStatus approvalStatus) {

    public static CourseRegisterResponse from(Course course) {
        return new CourseRegisterResponse(course.getId(), course.getApprovalStatus());
    }
}
