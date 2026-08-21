package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 후기 목록 아이템. 화면이 카드에 그리는 값만 담는다 — 추천·난이도·혼잡도는 요약(분포)에서 보고, 작성 당시 레벨은 {@code isEditable} 판정에만 쓰여
 * 내려보내지 않는다. {@code caution}은 관리자 화면 전용이라 제외한다.
 *
 * <p>{@code nickname}이 null이면 탈퇴·익명화된 회원의 후기다.
 */
public record ReviewItem(
        @Schema(description = "후기 id") Long reviewId,
        @Schema(description = "작성자 회원 id(신고·차단 대상 지정용)") Long memberId,
        @Schema(description = "작성자 닉네임(탈퇴·익명화 시 null)") String nickname,
        @Schema(description = "연습 방법") PracticeMethod practiceMethod,
        @Schema(description = "후기 내용(없으면 null)") String content,
        @Schema(description = "내가 쓴 후기인지") @JsonProperty("isMine") boolean mine,
        @Schema(description = "수정 가능 여부(내 후기이고 작성 당시 레벨 = 현재 레벨)") @JsonProperty("isEditable")
                boolean editable,
        @Schema(description = "신고 누적으로 비공개된 후기인지(내 후기에서만 true로 내려간다)") @JsonProperty("isHidden")
                boolean hidden,
        @Schema(description = "작성 당시 레벨에서 GPS 방문 인증을 받았는지(\"인증된 후기\" 배지)")
                @JsonProperty("isVerifiedVisit")
                boolean verifiedVisit,
        @Schema(description = "작성 시각") LocalDateTime createdAt) {

    public static ReviewItem of(Review review, boolean mine, boolean editable) {
        return new ReviewItem(
                review.getId(),
                review.getMember().getId(),
                review.getMember().getNickname(),
                review.getPracticeMethod(),
                review.getContent(),
                mine,
                editable,
                review.isHidden(),
                review.isVerifiedVisit(),
                review.getCreatedAt());
    }
}
