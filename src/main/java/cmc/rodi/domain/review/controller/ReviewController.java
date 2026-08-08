package cmc.rodi.domain.review.controller;

import cmc.rodi.domain.review.dto.MyReviewItem;
import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.dto.ReviewReportRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.dto.ReviewSummaryResponse;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 후기 API. 문서 스펙은 {@link ReviewControllerDocs}.
 *
 * <p>작성은 장소 하위(`/places/{placeId}/reviews`), 개별 조작(수정·삭제)은 후기 식별자만으로 충분해 `/reviews/{reviewId}`에 둔다.
 * 내가 쓴 후기는 회원 소유라 `/members/me/reviews`에 둔다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController implements ReviewControllerDocs {

    private static final int MAX_SIZE = 100;

    private final ReviewService reviewService;
    private final ReviewQueryService reviewQueryService;

    @Override
    @PostMapping("/places/{placeId}/reviews")
    public ApiResponse<ReviewCreateResponse> createReview(
            @PathVariable Long placeId,
            @CurrentMember Long memberId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(reviewService.create(placeId, memberId, request));
    }

    @Override
    @GetMapping("/members/me/reviews")
    public ApiResponse<CursorPage<MyReviewItem>> getMyReviews(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(reviewQueryService.getMyReviews(memberId, size, cursor));
    }

    @Override
    @GetMapping("/places/{placeId}/reviews")
    public ApiResponse<CursorPage<ReviewItem>> getReviews(
            @PathVariable Long placeId,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        return ApiResponse.success(
                reviewQueryService.getReviews(
                        placeId, memberId, new ReviewListRequest(level, size, cursor)));
    }

    @Override
    @GetMapping("/places/{placeId}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> getSummary(
            @PathVariable Long placeId,
            @RequestParam(required = false) String level,
            @CurrentMember Long memberId) {
        return ApiResponse.success(
                reviewQueryService.getSummary(placeId, memberId, ReviewListRequest.ofLevel(level)));
    }

    @Override
    @PutMapping("/reviews/{reviewId}")
    public ApiResponse<Void> updateReview(
            @PathVariable Long reviewId,
            @CurrentMember Long memberId,
            @Valid @RequestBody ReviewRequest request) {
        reviewService.update(reviewId, memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable Long reviewId, @CurrentMember Long memberId) {
        reviewService.delete(reviewId, memberId);
        return ApiResponse.success(null);
    }

    @Override
    @GetMapping("/reviews/report-form")
    public ApiResponse<FormResponse> getReportForm() {
        return ApiResponse.success(reviewService.getReportForm());
    }

    @Override
    @PostMapping("/reviews/{reviewId}/report")
    public ApiResponse<Void> report(
            @PathVariable Long reviewId,
            @CurrentMember Long memberId,
            @Valid @RequestBody ReviewReportRequest request) {
        reviewService.report(reviewId, memberId, request);
        return ApiResponse.success(null);
    }
}
