package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.service.CourseQueryService;
import cmc.rodi.domain.place.service.CourseService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 코스 API. 문서 스펙은 {@link CourseControllerDocs}.
 *
 * <p>코스는 등록·폼·내 목록·삭제라는 자체 오퍼레이션 묶음을 가지므로 장소 조회({@link PlaceController})와 분리한다. 엔티티가 {@code
 * domain.place}에 있어 패키지는 함께 둔다. 목록은 회원 소유라 {@code /members/me/courses}, 개별 조작은 대상 자체를 가리키는 {@code
 * /courses/{courseId}}에 둔다(연습 목록과 같은 규칙).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController implements CourseControllerDocs {

    private static final int MAX_SIZE = 100;

    private final CourseService courseService;
    private final CourseQueryService courseQueryService;

    @Override
    @PostMapping("/courses")
    public ApiResponse<CourseRegisterResponse> register(
            @Valid @RequestBody CourseRegisterRequest request, @CurrentMember Long memberId) {
        return ApiResponse.success(courseService.register(request, memberId));
    }

    @Override
    @GetMapping("/courses/registration-form")
    public ApiResponse<CourseRegistrationFormResponse> getRegistrationForm() {
        return ApiResponse.success(courseService.getRegistrationForm());
    }

    @Override
    @GetMapping("/members/me/courses")
    public ApiResponse<CursorPage<MyCourseItem>> getMyCourses(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(courseQueryService.getMyCourses(memberId, status, size, cursor));
    }

    @Override
    @DeleteMapping("/courses/{courseId}")
    public ApiResponse<Void> delete(@PathVariable Long courseId, @CurrentMember Long memberId) {
        courseService.delete(courseId, memberId);
        return ApiResponse.success(null);
    }
}
