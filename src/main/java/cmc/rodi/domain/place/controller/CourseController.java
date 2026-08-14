package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.domain.place.service.CourseService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 코스 API. 문서 스펙은 {@link CourseControllerDocs}.
 *
 * <p>코스는 등록·폼·내 목록·삭제라는 자체 오퍼레이션 묶음을 가지므로 장소 조회({@link PlaceController})와 분리한다. 엔티티가 {@code
 * domain.place}에 있어 패키지는 함께 둔다.
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController implements CourseControllerDocs {

    private final CourseService courseService;

    @Override
    @PostMapping
    public ApiResponse<CourseRegisterResponse> register(
            @Valid @RequestBody CourseRegisterRequest request, @CurrentMember Long memberId) {
        return ApiResponse.success(courseService.register(request, memberId));
    }

    @Override
    @GetMapping("/registration-form")
    public ApiResponse<CourseRegistrationFormResponse> getRegistrationForm() {
        return ApiResponse.success(courseService.getRegistrationForm());
    }
}
