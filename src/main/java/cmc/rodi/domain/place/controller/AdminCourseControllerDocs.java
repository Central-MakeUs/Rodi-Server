package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseApprovalRequest;
import cmc.rodi.domain.place.dto.CourseApprovalResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 관리자 코스 API의 Swagger 문서 스펙. 매핑·구현은 {@link AdminCourseController}. */
@Tag(name = "Admin Course", description = "관리자 코스 승인")
public interface AdminCourseControllerDocs {

    String APPROVAL_REQUEST_EXAMPLE =
            """
            { "approvalStatus": "APPROVED" }
            """;

    @Operation(
            summary = "코스 승인 상태 변경",
            description =
                    """
                    코스의 승인 상태를 `PENDING`·`APPROVED`·`REJECTED` 중 하나로 바꾼다.

                    ⚠️ **임시 정책**: 계정 권한(ROLE) 작업 전이라 지금은 로그인 사용자 전체가 호출할 수 있다.
                    운영 배포 전 관리자 전용으로 좁혀야 한다.

                    - 상태 전이는 자유롭다. 승인 취소·반려 되돌리기 등 운영 실수를 고칠 수 있어야 한다.
                    - 같은 상태로 보내면 멱등 200이며 `approvedAt`은 바뀌지 않는다.
                    - 삭제된 코스는 `COURSE_404_2`이고, 없는 코스·주차장 id는 `COURSE_404_1`이다.
                    """,
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CourseApprovalRequest.class),
                                            examples =
                                                    @ExampleObject(
                                                            value = APPROVAL_REQUEST_EXAMPLE))))
    ApiResponse<CourseApprovalResponse> changeApprovalStatus(
            @Parameter(description = "코스 id") Long courseId,
            @Schema(hidden = true) CourseApprovalRequest request,
            @Parameter(hidden = true) Long memberId);
}
