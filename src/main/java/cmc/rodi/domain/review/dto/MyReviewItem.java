package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내가 쓴 후기 목록 아이템. 어느 장소에 썼는지가 핵심이라 장소를 싣고, 작성자 관련 값({@code nickname}·{@code isMine})은 전부 나 자신이라 뺀다.
 */
public record MyReviewItem(
        @Schema(description = "후기 id") Long reviewId,
        @Schema(description = "후기를 쓴 장소 id") Long placeId,
        @Schema(description = "장소명") String placeName,
        @Schema(description = "후기 내용(없으면 null)") String content,
        @Schema(description = "수정 가능 여부(작성 당시 레벨 = 현재 레벨)") @JsonProperty("isEditable")
                boolean editable,
        @Schema(description = "신고 누적으로 비공개된 후기인지") @JsonProperty("isHidden") boolean hidden,
        @Schema(description = "작성 당시 레벨에서 GPS 방문 인증을 받았는지") @JsonProperty("isVerifiedVisit")
                boolean verifiedVisit,
        @Schema(description = "작성 시각") LocalDateTime createdAt) {

    public static MyReviewItem of(Review review, Level currentLevel) {
        return new MyReviewItem(
                review.getId(),
                review.getPlace().getId(),
                review.getPlace().getName(),
                review.getContent(),
                review.isEditableAt(currentLevel),
                review.isHidden(),
                review.isVerifiedVisit(),
                review.getCreatedAt());
    }
}
