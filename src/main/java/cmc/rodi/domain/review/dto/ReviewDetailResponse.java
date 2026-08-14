package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 후기 한 건의 전체 값. <b>수정 화면이 폼을 채우는 데 쓴다</b> — 그래서 목록 아이템과 달리 수정 요청({@code ReviewRequest})이 요구하는 필드를
 * 하나도 빠뜨리지 않는다.
 *
 * <p>{@code caution}은 목록에 내리지 않는 값이라 이 응답은 <b>본인 후기에만</b> 열린다.
 *
 * <p>값이 없는 {@code content}·{@code caution}도 키를 남긴 채 null로 내려간다 — 폼이 항상 같은 필드를 읽게 한다.
 */
public record ReviewDetailResponse(
        @Schema(description = "후기 id") Long reviewId,
        @Schema(description = "후기를 쓴 장소 id") Long placeId,
        @Schema(description = "장소명(수정 화면 헤더용)") String placeName,
        @Schema(description = "추천 여부") @JsonProperty("isRecommended") boolean recommended,
        @Schema(description = "체감 난이도") Difficulty difficulty,
        @Schema(description = "혼잡도") Congestion congestion,
        @Schema(description = "연습 방법") PracticeMethod practiceMethod,
        @Schema(description = "후기 내용(없으면 null)") String content,
        @Schema(description = "주의사항(없으면 null)") String caution,
        @Schema(description = "수정 가능 여부(작성 당시 레벨 = 현재 레벨)") @JsonProperty("isEditable")
                boolean editable,
        @Schema(description = "신고 누적으로 비공개된 후기인지") @JsonProperty("isHidden") boolean hidden,
        @Schema(description = "작성 당시 레벨에서 GPS 방문 인증을 받았는지") @JsonProperty("isVerifiedVisit")
                boolean verifiedVisit,
        @Schema(description = "작성 시각") LocalDateTime createdAt) {

    public static ReviewDetailResponse of(Review review, Level currentLevel) {
        return new ReviewDetailResponse(
                review.getId(),
                review.getPlace().getId(),
                review.getPlace().getName(),
                review.isRecommended(),
                review.getDifficulty(),
                review.getCongestion(),
                review.getPracticeMethod(),
                review.getContent(),
                review.getCaution(),
                review.isEditableAt(currentLevel),
                review.isHidden(),
                review.isVerifiedVisit(),
                review.getCreatedAt());
    }
}
