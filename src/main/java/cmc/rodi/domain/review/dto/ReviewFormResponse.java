package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.CourseIssue;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.global.common.form.FormOption;
import cmc.rodi.global.common.form.FormType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * 후기 작성 화면 정의(스펙 024). 단계 → 문항 → 선택지와 노출 조건을 서버가 내려줘, 앱 배포 없이 문구·구성을 바꿀 수 있게 한다.
 *
 * <p>구조는 온보딩 폼({@code OnboardingFormResponse})과 같다. 공용 {@code FormResponse}는 문항 하나만 담는 구조라 단계·조건부
 * 노출을 담을 수 없어 전용 응답을 두고, 선택지만 공용 {@link FormOption}을 쓴다(기타 직접 입력 표현이 이미 있다). 문항 {@code code}는 작성
 * 요청({@link ReviewRequest})의 필드명, 선택지 {@code code}는 제출 값이다.
 *
 * <p>기본 선택값은 없다 — 처음엔 추천 여부만 보이고, 고르는 순간 {@code showWhen}이 맞는 쪽 문항이 나타난다. 기타 직접 입력의 제출 키 ({@code
 * courseIssueDetail})는 신고 사유 폼과 같이 폼에 담지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewFormResponse(
        @Schema(description = "화면 제목") String title,
        @Schema(description = "코스명(1단계 제목의 강조 표시용)") String placeName,
        @Schema(description = "단계(order 오름차순)") List<Step> steps) {

    /** 추천 여부 문항의 선택지 코드. 요청 필드가 boolean이라 문자열로 둔다. */
    public static final String RECOMMENDED = "true";

    public static final String NOT_RECOMMENDED = "false";

    /** 화면 한 페이지. 시안에 제목이 없는 단계는 {@code title}을 생략한다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(
            @Schema(description = "단계 순서(1부터)") int order,
            @Schema(description = "단계 제목(없으면 생략)") String title,
            @Schema(description = "문항(order 오름차순)") List<Question> questions) {}

    /** 문항 하나. 값이 없는 선택 속성은 생략한다(NON_NULL). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(
            @Schema(description = "문항 코드 = 작성 요청의 필드명") String code,
            @Schema(description = "문항 순서(단계를 넘어 1부터)") int order,
            @Schema(description = "문항 유형") FormType type,
            @Schema(description = "필수 응답 여부(문항이 보일 때만 적용)") boolean required,
            @Schema(description = "문항 제목") String title,
            @Schema(description = "입력 안내 문구(TEXT만)") String placeholder,
            @Schema(description = "최대 글자 수(TEXT만, 제한 없으면 생략)") Integer maxLength,
            @Schema(description = "조건부 노출(없으면 항상 노출)") ShowWhen showWhen,
            @Schema(description = "선택지(order 오름차순, TEXT면 생략)") List<FormOption> options) {}

    /** {@code question} 문항에서 {@code anyOf} 중 하나라도 골랐을 때만 보인다. */
    public record ShowWhen(
            @Schema(description = "조건 문항 코드") String question,
            @Schema(description = "이 중 하나라도 고르면 노출") List<String> anyOf) {}

    public static ReviewFormResponse of(String placeName) {
        ShowWhen recommendedOnly = new ShowWhen("isRecommended", List.of(RECOMMENDED));
        ShowWhen notRecommendedOnly = new ShowWhen("isRecommended", List.of(NOT_RECOMMENDED));
        return new ReviewFormResponse(
                "후기 남기기",
                placeName,
                List.of(
                        new Step(
                                1,
                                placeName + "의 연습은 어떠셨나요?",
                                List.of(
                                        select(
                                                "isRecommended",
                                                1,
                                                FormType.SINGLE_SELECT,
                                                "이 코스를 다른 초보 운전자에게 추천하시겠어요?",
                                                null,
                                                List.of(
                                                        FormOption.of(NOT_RECOMMENDED, "별로에요", 1),
                                                        FormOption.of(RECOMMENDED, "추천해요", 2))),
                                        select(
                                                "difficulty",
                                                2,
                                                FormType.SINGLE_SELECT,
                                                "난이도",
                                                recommendedOnly,
                                                options(Difficulty.values(), Difficulty::getLabel)),
                                        select(
                                                "congestion",
                                                3,
                                                FormType.SINGLE_SELECT,
                                                "혼잡도",
                                                recommendedOnly,
                                                options(Congestion.values(), Congestion::getLabel)),
                                        new Question(
                                                "caution",
                                                4,
                                                FormType.TEXT,
                                                false,
                                                "주의사항",
                                                "예) 갑자기 나오는 자전거 주의!",
                                                null, // 길이 제한 없음
                                                recommendedOnly,
                                                null),
                                        select(
                                                "courseIssues",
                                                5,
                                                FormType.MULTI_SELECT,
                                                "해당 코스에 어떤 문제가 있나요?",
                                                notRecommendedOnly,
                                                Arrays.stream(CourseIssue.values())
                                                        .sorted(
                                                                Comparator.comparingInt(
                                                                        CourseIssue::getOrder))
                                                        .map(CourseIssue::toOption)
                                                        .toList()))),
                        new Step(
                                2,
                                null, // 시안에 단계 제목 없음
                                List.of(
                                        select(
                                                "practiceMethod",
                                                6,
                                                FormType.SINGLE_SELECT,
                                                "연습 방법",
                                                null,
                                                options(
                                                        PracticeMethod.values(),
                                                        PracticeMethod::getLabel)),
                                        new Question(
                                                "content",
                                                7,
                                                FormType.TEXT,
                                                false,
                                                "후기 작성",
                                                "자유롭게 후기를 작성해주세요.",
                                                Review.MAX_CONTENT_LENGTH,
                                                null,
                                                null)))));
    }

    /** 필수 선택 문항. */
    private static Question select(
            String code,
            int order,
            FormType type,
            String title,
            ShowWhen showWhen,
            List<FormOption> options) {
        return new Question(code, order, type, true, title, null, null, showWhen, options);
    }

    /** 선택지 순서는 enum 선언 순서를 그대로 쓴다(1부터). */
    private static <E extends Enum<E>> List<FormOption> options(
            E[] values, Function<E, String> label) {
        return IntStream.range(0, values.length)
                .mapToObj(i -> FormOption.of(values[i].name(), label.apply(values[i]), i + 1))
                .toList();
    }
}
