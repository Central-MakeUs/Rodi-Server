package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.CourseIssue;
import cmc.rodi.global.common.validation.GraphemeSize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 코스 신고 요청(스펙 025). 문제 항목은 신고 폼(GET /courses/report-form)의 선택지 코드를 그대로 보낸다. {@code OTHER}(기타)를 고르면
 * 직접 입력이 필수이고, 없으면 무시된다.
 */
public record CourseReportRequest(
        @Schema(
                        description = "코스 문제 항목 코드(1개 이상, 복수 선택)",
                        example = "[\"ROAD_INFO_MISMATCH\", \"OTHER\"]")
                @NotEmpty
                List<@NotNull CourseIssue> courseIssues,
        @Schema(
                        description =
                                "기타 직접 입력(courseIssues에 OTHER가 있으면 필수, 30자 이하 — 사용자가 보는 글자 수 기준)",
                        example = "주말엔 입구가 잠겨 있어요")
                @GraphemeSize(max = CourseIssue.TEXT_INPUT_MAX_LENGTH)
                String detail) {

    /** 입력 정리: 항목 중복을 합치고, 직접 입력은 기타일 때만 앞뒤 공백을 잘라 남긴다(검증보다 먼저 실행된다). */
    public CourseReportRequest {
        courseIssues = courseIssues == null ? null : courseIssues.stream().distinct().toList();
        // 기타가 아니면 길이 검증에도 걸리지 않게 미리 버린다.
        boolean acceptsDetail = courseIssues != null && courseIssues.contains(CourseIssue.OTHER);
        detail = (!acceptsDetail || detail == null || detail.isBlank()) ? null : detail.trim();
    }

    /** 기타는 직접 입력이 반드시 있어야 한다(생성자에서 공백뿐인 값은 이미 null이 됐다). */
    @JsonIgnore
    @Schema(hidden = true) // 검증 메서드일 뿐이라 요청 스키마에 노출하지 않는다
    @AssertTrue(message = "기타 문제는 직접 입력이 필요합니다")
    public boolean isDetailConsistent() {
        return courseIssues == null || !courseIssues.contains(CourseIssue.OTHER) || detail != null;
    }
}
