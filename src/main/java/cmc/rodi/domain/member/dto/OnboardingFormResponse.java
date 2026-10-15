package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.CarType;
import cmc.rodi.domain.member.entity.DrivingPeriod;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.entity.RecentFrequency;
import cmc.rodi.domain.member.entity.RoadExperience;
import cmc.rodi.domain.member.entity.SoloDrivingRange;
import cmc.rodi.domain.member.entity.SoloParkingLevel;
import cmc.rodi.global.common.form.FormType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * 온보딩 화면 정의(스펙 019). 단계 → 문항 → 선택지와 흐름 규칙을 서버가 내려줘, 앱 배포 없이 문구·구성을 바꿀 수 있게 한다.
 *
 * <p>공용 {@code global.common.form.FormResponse}를 쓰지 않는 이유: 그쪽은 단일 선택 문항 하나(신고 사유·미방문 사유)를 위한 구조라 여러
 * 단계·문항과 흐름 규칙을 담을 수 없다. 문항 {@code code}는 제출 요청({@link OnboardingRequest})의 필드명, 선택지 {@code code}는
 * enum 값 그대로다.
 */
public record OnboardingFormResponse(@Schema(description = "단계(order 오름차순)") List<Step> steps) {

    /** 선호 연습유형 최대 선택 수. 제출 요청의 {@code @Size(max = 3)}과 같은 값이다. */
    public static final int PRACTICE_TYPES_MAX_SELECT = 3;

    /** 화면 한 페이지. */
    public record Step(
            @Schema(description = "단계 순서(1부터)") int order,
            @Schema(description = "건너뛰기 가능 여부") boolean skippable,
            @Schema(description = "단계 제목") String title,
            @Schema(description = "단계 설명") String description,
            @Schema(description = "문항(order 오름차순)") List<Question> questions) {}

    /** 문항 하나. 값이 없는 선택 속성은 생략한다(NON_NULL). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(
            @Schema(description = "문항 코드 = 제출 요청의 필드명") String code,
            @Schema(description = "문항 순서(단계를 넘어 1부터)") int order,
            @Schema(description = "문항 유형") FormType type,
            @Schema(description = "필수 응답 여부") boolean required,
            @Schema(description = "문항 제목") String title,
            @Schema(description = "부연 설명(없으면 생략)") String description,
            @Schema(description = "입력 안내 문구(TEXT만)") String placeholder,
            @Schema(description = "최대 선택 개수(RANKED_SELECT만)") Integer maxSelect,
            @Schema(description = "최대 글자 수(TEXT만)") Integer maxLength,
            @Schema(description = "조건부 노출(없으면 항상 노출)") ShowWhen showWhen,
            @Schema(description = "선택지(order 오름차순, TEXT면 생략)") List<Option> options) {}

    /** 선택지 하나. {@code exclusive}·{@code skipsFollowUps}는 true일 때만 내려간다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Option(
            @Schema(description = "선택지 코드(제출 시 이 값을 보낸다)") String code,
            @Schema(description = "화면 표시 문구") String label,
            @Schema(description = "노출 순서(1부터)") int order,
            @Schema(description = "단독으로만 고를 수 있는지(true일 때만)") Boolean exclusive,
            @Schema(description = "고르면 후속 문항을 건너뛰는지(true일 때만)") Boolean skipsFollowUps) {}

    /** {@code question} 문항에서 {@code anyOf} 중 하나라도 골랐을 때만 보인다. */
    public record ShowWhen(
            @Schema(description = "조건 문항 코드") String question,
            @Schema(description = "이 중 하나라도 고르면 노출") List<String> anyOf) {}

    public static OnboardingFormResponse of() {
        ShowWhen soloOnly = new ShowWhen("roadExperiences", List.of(RoadExperience.SOLO.name()));
        return new OnboardingFormResponse(
                List.of(
                        new Step(
                                1,
                                false,
                                "운전 경험에 대해 알려주세요.",
                                "자세히 입력할수록 더 잘 맞는 연습 장소를 추천해요.",
                                List.of(
                                        select(
                                                "drivingPeriod",
                                                1,
                                                FormType.SINGLE_SELECT,
                                                true,
                                                "면허 취득 후 실제 운전한 기간을 알려주세요",
                                                null,
                                                options(
                                                        DrivingPeriod.values(),
                                                        DrivingPeriod::getLabel,
                                                        p -> false,
                                                        DrivingPeriod::skipsFollowUps)),
                                        select(
                                                "recentFrequency",
                                                2,
                                                FormType.SINGLE_SELECT,
                                                false,
                                                "가장 최근, 운전을 언제 하셨나요?",
                                                null,
                                                options(
                                                        RecentFrequency.values(),
                                                        RecentFrequency::getLabel)),
                                        select(
                                                "roadExperiences",
                                                3,
                                                FormType.MULTI_SELECT,
                                                false,
                                                "면허 취득후 도로주행을 해본 적이 있나요?",
                                                null,
                                                options(
                                                        RoadExperience.values(),
                                                        RoadExperience::getLabel,
                                                        RoadExperience::isExclusive,
                                                        e -> false)),
                                        select(
                                                "soloDrivingRange",
                                                4,
                                                FormType.SINGLE_SELECT,
                                                false,
                                                "혼자 운전, 어디까지 해봤나요?",
                                                soloOnly,
                                                options(
                                                        SoloDrivingRange.values(),
                                                        SoloDrivingRange::getLabel)),
                                        select(
                                                "soloParkingLevel",
                                                5,
                                                FormType.SINGLE_SELECT,
                                                false,
                                                "혼자 주차는 어느 정도 해봤나요?",
                                                soloOnly,
                                                options(
                                                        SoloParkingLevel.values(),
                                                        SoloParkingLevel::getLabel)))),
                        new Step(
                                2,
                                true,
                                "추가 정보를 입력하면 더 정확해요.",
                                "딱 맞는 코스 추천을 위한 선택항목이에요.",
                                List.of(
                                        new Question(
                                                "practiceTypes",
                                                6,
                                                FormType.RANKED_SELECT,
                                                false,
                                                "더 연습해보고 싶은 상황이 있나요?",
                                                "1순위부터 순서대로 선택해주세요.",
                                                null,
                                                PRACTICE_TYPES_MAX_SELECT,
                                                null,
                                                null,
                                                options(
                                                        PracticeType.values(),
                                                        PracticeType::getLabel)),
                                        select(
                                                "carType",
                                                7,
                                                FormType.SINGLE_SELECT,
                                                false,
                                                "주로 타는 차종은 무엇인가요?",
                                                null,
                                                options(CarType.values(), CarType::getLabel)),
                                        new Question(
                                                "drivingGoal",
                                                8,
                                                FormType.TEXT,
                                                false,
                                                "이루고 싶은 운전 목표를 입력해주세요.",
                                                null,
                                                "ex)강남 운전 자신있게 하기!",
                                                null,
                                                Member.DRIVING_GOAL_MAX_LENGTH,
                                                null,
                                                null)))));
    }

    /** 설명·입력 제약이 없는 선택 문항. */
    private static Question select(
            String code,
            int order,
            FormType type,
            boolean required,
            String title,
            ShowWhen showWhen,
            List<Option> options) {
        return new Question(
                code, order, type, required, title, null, null, null, null, showWhen, options);
    }

    private static <E extends Enum<E>> List<Option> options(E[] values, Function<E, String> label) {
        return options(values, label, e -> false, e -> false);
    }

    /** 선택지 순서는 enum 선언 순서를 그대로 쓴다(1부터). 흐름 표시는 true일 때만 채운다. */
    private static <E extends Enum<E>> List<Option> options(
            E[] values,
            Function<E, String> label,
            Predicate<E> exclusive,
            Predicate<E> skipsFollowUps) {
        return IntStream.range(0, values.length)
                .mapToObj(
                        i ->
                                new Option(
                                        values[i].name(),
                                        label.apply(values[i]),
                                        i + 1,
                                        exclusive.test(values[i]) ? Boolean.TRUE : null,
                                        skipsFollowUps.test(values[i]) ? Boolean.TRUE : null))
                .toList();
    }
}
