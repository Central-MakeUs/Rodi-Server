package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.PracticeCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 코스 등록 화면 정의(스펙 014). 카테고리 → 연습유형 트리와 입력 제약·안내 문구를 서버가 내려줘, 앱 배포 없이 구성을 바꿀 수 있게 한다.
 *
 * <p>공용 {@code global.common.form.FormResponse}를 쓰지 않는 이유: 그쪽은 단일 선택 문항 하나(신고 사유·미방문 사유)를 위한 구조라 2단
 * 트리와 입력 제약을 담을 수 없다.
 */
public record CourseRegistrationFormResponse(
        @Schema(description = "경유지 최대 개수") int maxWaypoints,
        @Schema(description = "등록 화면 소제목") Sections sections,
        @Schema(description = "연습유형 선택 정의") PracticeTypeForm practiceType,
        @Schema(description = "텍스트 입력 제약") Inputs inputs) {

    /** 앱 화면에 노출할 소제목. */
    public record Sections(
            @Schema(description = "기본 정보 섹션 제목") String basicInfo,
            @Schema(description = "연습유형 카테고리 선택 제목") String practiceCategory,
            @Schema(description = "연습유형 선택 제목") String practiceType,
            @Schema(description = "주의사항 입력 제목") String caution,
            @Schema(description = "한줄 소개 입력 제목") String description) {}

    /** 연습유형 선택 규칙 + 카테고리 트리. */
    public record PracticeTypeForm(
            @Schema(description = "최대 선택 개수(카테고리 합산)") int maxSelect,
            @Schema(description = "최대 개수 초과 시 안내 문구") String maxSelectExceededMessage,
            @Schema(description = "카테고리(order 오름차순)") List<CategoryItem> categories) {}

    /** 카테고리 하나. */
    public record CategoryItem(
            @Schema(description = "카테고리 코드") String code,
            @Schema(description = "화면 표시 문구") String label,
            @Schema(description = "노출 순서(1부터)") int order,
            @Schema(description = "하위 연습유형(order 오름차순)") List<PracticeTypeItem> practiceTypes) {}

    /** 연습유형 하나. {@code code}가 그대로 등록 요청의 {@code practiceTypes} 값이 된다. */
    public record PracticeTypeItem(
            @Schema(description = "연습유형 코드") String code,
            @Schema(description = "화면 표시 문구") String label,
            @Schema(description = "카테고리 안에서의 순서(1부터)") int order) {}

    /** 자유 입력 필드의 제약. */
    public record Inputs(
            @Schema(description = "주의사항") InputSpec caution,
            @Schema(description = "한줄 소개") InputSpec description) {}

    /** 입력 하나의 제약. 최소 길이가 없는 입력은 {@code minLength}를 생략한다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputSpec(
            @Schema(description = "필수 여부") boolean required,
            @Schema(description = "최소 길이(없으면 생략)") Integer minLength,
            @Schema(description = "최대 길이") int maxLength,
            @Schema(description = "입력 안내 문구") String placeholder) {}

    public static CourseRegistrationFormResponse of(
            int maxWaypoints, InputSpec caution, InputSpec description) {
        return new CourseRegistrationFormResponse(
                maxWaypoints,
                new Sections("기본정보", "연습유형 카테고리 고르기", "연습유형", "주의사항 작성", "한줄 소개"),
                new PracticeTypeForm(
                        PracticeCategory.MAX_SELECT,
                        PracticeCategory.MAX_SELECT_EXCEEDED_MESSAGE,
                        categories()),
                new Inputs(caution, description));
    }

    private static List<CategoryItem> categories() {
        return Arrays.stream(PracticeCategory.values())
                .sorted(Comparator.comparingInt(PracticeCategory::getOrder))
                .map(
                        category ->
                                new CategoryItem(
                                        category.name(),
                                        category.getLabel(),
                                        category.getOrder(),
                                        practiceTypes(category)))
                .toList();
    }

    /** 카테고리 안의 순서는 선언 순서를 그대로 쓴다(1부터). */
    private static List<PracticeTypeItem> practiceTypes(PracticeCategory category) {
        List<PracticeType> types = category.getPracticeTypes();
        return java.util.stream.IntStream.range(0, types.size())
                .mapToObj(
                        i ->
                                new PracticeTypeItem(
                                        types.get(i).name(), types.get(i).getLabel(), i + 1))
                .toList();
    }
}
