package cmc.rodi.domain.review.controller;

import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewReportRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.dto.ReviewSummaryResponse;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.pagination.CursorPage;
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
            summary = "후기 목록 조회",
            description =
                    "장소 후기를 최신순 커서 페이지네이션으로 반환한다. level을 생략하면 조회자 본인 레벨, ALL이면 전체 레벨."
                            + " 내가 차단한 회원의 후기는 제외된다. JWT 필요.")
    ApiResponse<CursorPage<ReviewItem>> getReviews(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(description = "레벨 필터(생략=내 레벨, ALL=전체)", example = "ROOKIE") String level,
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 요약 조회",
            description =
                    "선택한 레벨 기준 난이도·혼잡도 분포와 추천 수, 레벨별 후기 수를 반환한다. 집계 단위는 후기 건수이며 차단은 반영하지 않는다."
                            + " JWT 필요.")
    ApiResponse<ReviewSummaryResponse> getSummary(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(description = "레벨 필터(생략=내 레벨, ALL=전체)", example = "ROOKIE") String level,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 수정",
            description = "본인 후기를 전체 교체한다. 작성 당시 레벨과 현재 레벨이 다르면 409, 타인 후기면 403. JWT 필요.")
    ApiResponse<Void> updateReview(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId,
            ReviewRequest request);

    @Operation(
            summary = "후기 삭제",
            description = "본인 후기를 삭제한다(레벨 무관). 신고도 함께 제거된다. 타인 후기면 403. JWT 필요.")
    ApiResponse<Void> deleteReview(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "신고 사유 폼 조회",
            description =
                    "후기 신고 화면에 그릴 사유 선택지를 내려준다(order 오름차순). 문구·순서를 서버가 정의하므로 앱 배포 없이 바꿀 수 있다."
                            + " OTHER(기타)는 requiresTextInput=true이며 placeholder·최대 길이가 함께 온다. JWT 필요.")
    ApiResponse<FormResponse> getReportForm();

    @Operation(
            summary = "후기 신고",
            description = "후기를 신고한다(중복 신고는 멱등). 본인 후기 신고는 400. 접수만 하고 후기 노출은 바뀌지 않는다. JWT 필요.")
    ApiResponse<Void> report(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId,
            ReviewReportRequest request);
}
