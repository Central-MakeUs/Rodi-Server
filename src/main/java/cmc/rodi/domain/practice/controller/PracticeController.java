package cmc.rodi.domain.practice.controller;

import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeSkipReasonRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.domain.practice.service.PracticeQueryService;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연습 코스 API. 문서 스펙은 {@link PracticeControllerDocs}.
 *
 * <p>담기는 장소 하위(`/places/{placeId}/practices`), 목록은 회원 소유라 `/members/me/practices`에 둔다. 개별 조작(방문
 * 기록·미방문 사유·제거)은 항목 자체를 가리키는 `/practices/{practiceId}`에 둔다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PracticeController implements PracticeControllerDocs {

    private static final int MAX_SIZE = 100;

    private final PracticeService practiceService;
    private final PracticeQueryService practiceQueryService;

    @Override
    @PostMapping("/places/{placeId}/practices")
    public ApiResponse<PracticeRegisterResponse> register(
            @PathVariable Long placeId, @CurrentMember Long memberId) {
        return ApiResponse.success(practiceService.register(placeId, memberId));
    }

    @Override
    @GetMapping("/members/me/practices")
    public ApiResponse<CursorPage<PracticeItem>> getMyPractices(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(practiceQueryService.getMyPractices(memberId, size, cursor));
    }

    @Override
    @PatchMapping("/practices/{practiceId}")
    public ApiResponse<PracticeVisitResponse> recordVisit(
            @PathVariable Long practiceId,
            @CurrentMember Long memberId,
            @Valid @RequestBody PracticeVisitRequest request) {
        return ApiResponse.success(practiceService.recordVisit(practiceId, memberId, request));
    }

    @Override
    @GetMapping("/practices/skip-reason-form")
    public ApiResponse<FormResponse> getSkipReasonForm() {
        return ApiResponse.success(practiceService.getSkipReasonForm());
    }

    @Override
    @PostMapping("/practices/{practiceId}/skip-reason")
    public ApiResponse<Void> submitSkipReason(
            @PathVariable Long practiceId,
            @CurrentMember Long memberId,
            @Valid @RequestBody PracticeSkipReasonRequest request) {
        practiceService.submitSkipReason(practiceId, memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @DeleteMapping("/practices/{practiceId}")
    public ApiResponse<Void> delete(@PathVariable Long practiceId, @CurrentMember Long memberId) {
        practiceService.delete(practiceId, memberId);
        return ApiResponse.success(null);
    }
}
