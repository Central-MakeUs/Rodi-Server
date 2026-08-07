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
 * 후기 목록 아이템. {@code memberLevel}은 작성 당시 레벨(작성자의 현재 레벨이 아니다), {@code nickname}이 null이면 탈퇴·익명화된 회원의
 * 후기다.
 */
public record ReviewItem(
        @Schema(description = "후기 id") Long reviewId,
        @Schema(description = "작성자 회원 id(신고·차단 대상 지정용)") Long memberId,
        @Schema(description = "작성자 닉네임(탈퇴·익명화 시 null)") String nickname,
        @Schema(description = "작성 당시 작성자 레벨") Level memberLevel,
        @Schema(description = "추천 여부") @JsonProperty("isRecommended") boolean recommended,
        @Schema(description = "체감 난이도") Difficulty difficulty,
        @Schema(description = "혼잡도") Congestion congestion,
        @Schema(description = "연습 방법") PracticeMethod practiceMethod,
        @Schema(description = "후기 내용") String content,
        @Schema(description = "주의사항(없으면 null)") String caution,
        @Schema(description = "내가 쓴 후기인지") @JsonProperty("isMine") boolean mine,
        @Schema(description = "수정 가능 여부(내 후기이고 작성 당시 레벨 = 현재 레벨)") @JsonProperty("isEditable")
                boolean editable,
        @Schema(description = "신고 누적으로 비공개된 후기인지(내 후기에서만 true로 내려간다)") @JsonProperty("isHidden")
                boolean hidden,
        @Schema(description = "작성 시각") LocalDateTime createdAt) {

    public static ReviewItem of(Review review, boolean mine, boolean editable) {
        return new ReviewItem(
                review.getId(),
                review.getMember().getId(),
                review.getMember().getNickname(),
                review.getMemberLevel(),
                review.isRecommended(),
                review.getDifficulty(),
                review.getCongestion(),
                review.getPracticeMethod(),
                review.getContent(),
                review.getCaution(),
                mine,
                editable,
                review.isHidden(),
                review.getCreatedAt());
    }
}
