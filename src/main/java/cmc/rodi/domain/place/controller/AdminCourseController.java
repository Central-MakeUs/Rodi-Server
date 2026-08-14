package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseApprovalRequest;
import cmc.rodi.domain.place.dto.CourseApprovalResponse;
import cmc.rodi.domain.place.service.CourseService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 코스 API. 문서 스펙은 {@link AdminCourseControllerDocs}.
 *
 * <p>TODO 권한 체계가 들어오면 컨트롤러 단위로 관리자 권한을 건다. 지금은 3차 업데이트 임시 정책에 따라 로그인 사용자 전체가 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController implements AdminCourseControllerDocs {

    private final CourseService courseService;

    @Override
    @PatchMapping("/{courseId}/approval")
    public ApiResponse<CourseApprovalResponse> changeApprovalStatus(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseApprovalRequest request,
            @CurrentMember Long memberId) {
        // memberId는 현재 임시 정책에서 "로그인한 사용자"임을 보장하는 용도다. 권한 작업 후 관리자 검사로 대체한다.
        return ApiResponse.success(courseService.changeApprovalStatus(courseId, request));
    }
}
