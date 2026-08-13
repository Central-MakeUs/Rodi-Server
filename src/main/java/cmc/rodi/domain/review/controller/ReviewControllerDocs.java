package cmc.rodi.domain.review.controller;

import cmc.rodi.domain.review.dto.MyReviewItem;
import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewDetailResponse;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewReportRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.dto.ReviewSummaryResponse;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 후기 API의 Swagger 문서 스펙. 매핑·구현은 {@link ReviewController}. */
@Tag(name = "Review", description = "장소 후기")
public interface ReviewControllerDocs {

    /**
     * 신고 사유 폼의 실제 응답. 공용 {@code FormResponse} 스키마를 미방문 사유 폼과 나눠 쓰는데 springdoc은 스키마 단위로 example을
     * 그리므로, 폼별 예시는 이렇게 엔드포인트에서 준다.
     */
    String REPORT_FORM_EXAMPLE =
            """
            {
              "isSuccess": true,
              "code": "COMMON_200",
              "message": "요청에 성공했습니다.",
              "data": {
                "questionId": "REVIEW_REPORT_REASON",
                "type": "SINGLE_SELECT",
                "title": "신고 사유",
                "required": true,
                "options": [
                  { "code": "SPAM", "label": "스팸/광고", "order": 1, "requiresTextInput": false },
                  { "code": "ABUSE", "label": "욕설, 음란성, 혐오 표현", "order": 2, "requiresTextInput": false },
                  { "code": "IRRELEVANT", "label": "코스와 무관한 내용", "order": 3, "requiresTextInput": false },
                  { "code": "FALSE_INFO", "label": "허위정보", "order": 4, "requiresTextInput": false },
                  { "code": "OTHER", "label": "기타", "order": 5, "requiresTextInput": true,
                    "textInputPlaceholder": "이유를 작성해주세요", "textInputMaxLength": 100 }
                ]
              }
            }
            """;

    @Operation(
            summary = "후기 작성",
            description =
                    "장소(코스·주차장)에 후기를 남긴다. 작성 당시 회원 레벨을 서버가 스냅샷으로 저장하며, 같은 장소에 여러 번 쓸 수 있다."
                            + " 필수는 isRecommended·difficulty·congestion·practiceMethod 넷이고"
                            + " content·caution은 선택이라 글 없이 평가만 남길 수 있다(공백만 보내면 없는 것으로 저장)."
                            + " 레벨이 없는 회원(온보딩 미완료)은 409. JWT 필요.")
    ApiResponse<ReviewCreateResponse> createReview(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(hidden = true) Long memberId,
            ReviewRequest request);

    @Operation(
            summary = "내가 쓴 후기 목록 조회",
            description =
                    "내가 쓴 후기를 최신순 커서 페이지네이션으로 반환한다. 장소 후기 목록과 달리 **레벨 필터가 없어**"
                            + " 레벨이 바뀌어도 내 후기가 전부 나온다. 신고 누적으로 비공개된 후기도 isHidden=true로 포함한다."
                            + " 어느 장소에 썼는지 알 수 있게 placeId·placeName을 함께 준다. JWT 필요.")
    ApiResponse<CursorPage<MyReviewItem>> getMyReviews(
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 목록 조회",
            description =
                    "장소 후기를 최신순 커서 페이지네이션으로 반환한다. level을 생략하면 조회자 본인 레벨, ALL이면 전체 레벨."
                            + " 내가 차단한 회원의 후기는 제외된다. 항목은 카드에 그릴 값만 담는다 —"
                            + " 추천 여부·난이도는 요약 API의 집계로 보고, 혼잡도는 저장만 하며 어느 API로도 내려주지 않는다."
                            + " caution은 관리자 화면 전용이라 응답에 없다. JWT 필요.")
    ApiResponse<CursorPage<ReviewItem>> getReviews(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(
                            description = "레벨 필터(생략=내 레벨, ALL=전체)",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "SEED",
                                                "ROOKIE",
                                                "OWNER",
                                                "EXPLORER",
                                                "NAVIGATOR",
                                                "ALL"
                                            },
                                            example = "ROOKIE"))
                    String level,
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 요약 조회",
            description =
                    "난이도 분포와 최다 난이도(topDifficulty)는 **선택한 레벨** 기준, 추천/비추천 수는 **전체 레벨 합산**이라"
                            + " 모수가 levelReviewCount·totalReviewCount로 나뉜다. 동률이면 더 어려운 난이도를 고르고,"
                            + " 후기가 없으면 topDifficulty 키 자체가 빠진다. 드롭다운용 레벨별 후기 수도 함께 준다."
                            + " 집계 단위는 후기 건수이며 차단은 반영하지 않는다. JWT 필요.")
    ApiResponse<ReviewSummaryResponse> getSummary(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(
                            description = "레벨 필터(생략=내 레벨, ALL=전체)",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "SEED",
                                                "ROOKIE",
                                                "OWNER",
                                                "EXPLORER",
                                                "NAVIGATOR",
                                                "ALL"
                                            },
                                            example = "ROOKIE"))
                    String level,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 상세 조회",
            description =
                    "후기 한 건의 전체 값을 준다. 수정 화면이 폼을 채우는 용도라 수정 요청(PUT)이 요구하는 필드를 모두 담는다."
                            + " 목록에 없는 caution까지 주므로 본인 후기만 조회할 수 있다(타인 403)."
                            + " 신고 누적으로 비공개된 내 후기도 조회되며 isHidden=true로 온다."
                            + " 내용이 없는 후기는 content가 null이다. 없는 후기는 404. JWT 필요.")
    ApiResponse<ReviewDetailResponse> getReview(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "후기 수정",
            description =
                    "본인 후기를 전체 교체한다. 작성과 같은 바디를 쓰며, 전체 교체라 content·caution을 비워 보내면"
                            + " 기존 값이 지워진다. 작성 당시 레벨과 현재 레벨이 다르면 409, 타인 후기면 403. JWT 필요.")
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            // 없으면 예시만 남고 응답 스키마가 통째로 사라진다(필드 설명이 안 보인다)
            useReturnTypeSchema = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResponseFormResponse"),
                            examples =
                                    @ExampleObject(name = "신고 사유 폼", value = REPORT_FORM_EXAMPLE)))
    ApiResponse<FormResponse> getReportForm();

    @Operation(
            summary = "후기 신고",
            description =
                    "후기를 신고한다(중복 신고는 멱등). 본인 후기 신고는 400."
                            + " 서로 다른 5명에게 신고되면 그 5번째 신고로 후기가 자동 비공개되어 목록·요약에서 빠지고,"
                            + " 작성자 본인 목록에만 isHidden=true로 남는다. JWT 필요.")
    ApiResponse<Void> report(
            @Parameter(description = "후기 id") Long reviewId,
            @Parameter(hidden = true) Long memberId,
            ReviewReportRequest request);
}
