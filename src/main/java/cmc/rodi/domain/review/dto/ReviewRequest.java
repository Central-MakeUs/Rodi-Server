package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.CourseIssue;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.global.common.validation.GraphemeSize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 후기 작성·수정 공용 요청. 수정은 전체 교체(PUT)라 작성과 같은 바디를 쓴다. 작성 당시 회원 레벨은 서버가 채우므로 요청에 없다.
 *
 * <p>1단계 평가는 <b>추천 여부에 따라 갈린다</b>(스펙 024). 추천해요는 난이도·혼잡도가 필수(주의사항 선택), 별로에요는 코스 문제 항목이 1개 이상 필수다.
 * 반대쪽 값이 와도 에러 없이 버린다. 연습 방법은 둘 다 필수이고 {@code content}는 선택이다. 수정이 전체 교체라 선택 값을 비우면 기존 값이 지워진다.
 */
public record ReviewRequest(
        @Schema(description = "추천 여부", example = "true") @NotNull @JsonProperty("isRecommended")
                Boolean recommended,
        @Schema(description = "체감 난이도(추천해요일 때 필수, 별로에요면 무시)") Difficulty difficulty,
        @Schema(description = "혼잡도(추천해요일 때 필수, 별로에요면 무시)") Congestion congestion,
        @Schema(description = "연습 방법") @NotNull PracticeMethod practiceMethod,
        @Schema(
                        description =
                                "후기 내용(선택, 150자 이하 — 이모지·자모 포함 사용자가 보는 글자 수 기준)."
                                        + " 공백만 보내면 없는 것으로 저장된다",
                        example = "차선이 넓고 신호가 단순해서 처음 도로 나갈 때 딱이었어요.")
                @GraphemeSize(max = Review.MAX_CONTENT_LENGTH)
                String content,
        @Schema(description = "주의사항(추천해요일 때만 저장, 선택, 길이 제한 없음)", example = "주말 오후엔 자전거 통행이 많습니다.")
                String caution,
        @Schema(
                        description =
                                "코스 문제 항목 코드(별로에요일 때 1개 이상 필수, 복수 선택, 추천해요면 무시)."
                                        + " 후기 작성 폼(GET /places/{placeId}/review-form) courseIssues 문항의 선택지 code 값")
                List<@NotNull CourseIssue> courseIssues,
        @Schema(
                        description =
                                "코스 문제 기타 직접 입력(courseIssues에 OTHER가 있으면 필수, 30자 이하 — 사용자가 보는 글자 수 기준)",
                        example = "입구 차단봉이 내려가 있어요")
                @GraphemeSize(max = CourseIssue.TEXT_INPUT_MAX_LENGTH)
                String courseIssueDetail) {

    public ReviewRequest {
        // 같은 코드가 여러 번 와도 하나로 본다.
        courseIssues = courseIssues == null ? null : courseIssues.stream().distinct().toList();
        // 직접 입력은 별로에요 + OTHER에서만 의미가 있다. 그 외에는 길이 검증에도 걸리지 않게 미리 버린다.
        boolean acceptsDetail =
                Boolean.FALSE.equals(recommended)
                        && courseIssues != null
                        && courseIssues.contains(CourseIssue.OTHER);
        courseIssueDetail =
                (!acceptsDetail || courseIssueDetail == null || courseIssueDetail.isBlank())
                        ? null
                        : courseIssueDetail.trim();
    }

    /** 추천해요는 난이도·혼잡도가 모두 있어야 한다. recommended가 null이면 @NotNull이 먼저 잡는다. */
    @JsonIgnore
    @Schema(hidden = true) // 검증 메서드일 뿐이라 요청 스키마에 노출하지 않는다
    @AssertTrue(message = "추천 후기는 난이도와 혼잡도가 필요합니다")
    public boolean isRatingsPresent() {
        return !Boolean.TRUE.equals(recommended) || (difficulty != null && congestion != null);
    }

    /** 별로에요는 코스 문제 항목을 하나 이상 골라야 한다. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "코스 문제 항목을 하나 이상 선택해주세요")
    public boolean isCourseIssuesPresent() {
        return !Boolean.FALSE.equals(recommended)
                || (courseIssues != null && !courseIssues.isEmpty());
    }

    /** 별로에요 + 기타는 직접 입력이 반드시 있어야 한다(생성자에서 공백뿐인 값은 이미 null이 됐다). */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "기타 문제는 직접 입력이 필요합니다")
    public boolean isCourseIssueDetailConsistent() {
        return !Boolean.FALSE.equals(recommended)
                || courseIssues == null
                || !courseIssues.contains(CourseIssue.OTHER)
                || courseIssueDetail != null;
    }
}
