package cmc.rodi.domain.review.controller;

import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 후기 API의 Swagger 문서 스펙. 매핑·구현은 {@link ReviewController}. */
@Tag(name = "Review", description = "장소 후기")
public interface ReviewControllerDocs {

    @Operation(
            summary = "후기 작성",
            description =
                    "장소(코스·주차장)에 후기를 남긴다. 작성 당시 회원 레벨을 서버가 스냅샷으로 저장하며, 같은 장소에 여러 번 쓸 수 있다."
                            + " 레벨이 없는 회원(온보딩 미완료)은 409. JWT 필요.")
    ApiResponse<ReviewCreateResponse> createReview(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(hidden = true) Long memberId,
            ReviewRequest request);

    @Operation(
            summary = "후기 수정",
            description = "본인 후기를 전체 교체한다. 작성 당시 레벨과 현재 레벨이 다르면 409, 타인 후기면 403. JWT 필요.")
    ApiResponse<Void> updateReview(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId,
            ReviewRequest request);

    @Operation(summary = "후기 삭제", description = "본인 후기를 삭제한다(레벨 무관). 타인 후기면 403. JWT 필요.")
    ApiResponse<Void> deleteReview(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId);
}
