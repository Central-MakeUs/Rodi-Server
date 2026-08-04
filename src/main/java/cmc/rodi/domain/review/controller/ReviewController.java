package cmc.rodi.domain.review.controller;

import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 후기 API. 문서 스펙은 {@link ReviewControllerDocs}.
 *
 * <p>작성은 장소 하위(`/places/{placeId}/reviews`), 개별 조작(수정·삭제)은 후기 식별자만으로 충분해 `/reviews/{reviewId}`에 둔다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController implements ReviewControllerDocs {

    private final ReviewService reviewService;

    @Override
    @PostMapping("/places/{placeId}/reviews")
    public ApiResponse<ReviewCreateResponse> createReview(
            @PathVariable Long placeId,
            @CurrentMember Long memberId,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(reviewService.create(placeId, memberId, request));
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
}
