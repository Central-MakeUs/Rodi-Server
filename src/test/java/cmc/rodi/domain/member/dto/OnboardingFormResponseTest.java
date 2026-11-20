package cmc.rodi.domain.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.dto.OnboardingFormResponse.Option;
import cmc.rodi.domain.member.dto.OnboardingFormResponse.Question;
import cmc.rodi.domain.member.dto.OnboardingFormResponse.Step;
import cmc.rodi.domain.member.entity.CarType;
import cmc.rodi.domain.member.entity.DrivingPeriod;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.entity.RecentFrequency;
import cmc.rodi.domain.member.entity.RoadExperience;
import cmc.rodi.domain.member.entity.SoloDrivingRange;
import cmc.rodi.domain.member.entity.SoloParkingLevel;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 온보딩 폼 정의(스펙 019) — 화면 구성과 제출 요청·enum이 어긋나지 않는지 검증한다. */
class OnboardingFormResponseTest {

    private final OnboardingFormResponse form = OnboardingFormResponse.of();

    private List<Question> questions() {
        return form.steps().stream().flatMap(step -> step.questions().stream()).toList();
    }

    private Question question(String code) {
        return questions().stream().filter(q -> q.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("2단계: 1단계는 Q1~Q4-2(건너뛰기 불가), 2단계는 추가 정보(건너뛰기 가능)")
    void 단계_구성() {
        assertThat(form.steps()).extracting(Step::order).containsExactly(1, 2);
        assertThat(form.steps()).extracting(Step::skippable).containsExactly(false, true);
        assertThat(form.steps().get(0).questions())
                .extracting(Question::code)
                .containsExactly(
                        "drivingPeriod",
                        "recentFrequency",
                        "roadExperiences",
                        "soloDrivingRange",
                        "soloParkingLevel");
        assertThat(form.steps().get(1).questions())
                .extracting(Question::code)
                .containsExactly("practiceTypes", "carType", "drivingGoal");
    }

    @Test
    @DisplayName("문항 order는 단계를 넘어 1~8로 이어진다")
    void 문항_순서() {
        assertThat(questions()).extracting(Question::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    @DisplayName("문항 code는 모두 제출 요청의 필드명이고, level만 문항이 아니다")
    void 문항_코드_제출_필드() {
        List<String> requestFields =
                Arrays.stream(OnboardingRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();

        assertThat(requestFields).containsAll(questions().stream().map(Question::code).toList());
        assertThat(requestFields)
                .filteredOn(field -> questions().stream().noneMatch(q -> q.code().equals(field)))
                .containsExactly("level");
    }

    @Test
    @DisplayName("선택지는 enum 전체를 선언 순서로, 라벨은 enum label")
    void 선택지_enum_일치() {
        assertOptions("drivingPeriod", DrivingPeriod.values(), DrivingPeriod::getLabel);
        assertOptions("recentFrequency", RecentFrequency.values(), RecentFrequency::getLabel);
        assertOptions("roadExperiences", RoadExperience.values(), RoadExperience::getLabel);
        assertOptions("soloDrivingRange", SoloDrivingRange.values(), SoloDrivingRange::getLabel);
        assertOptions("soloParkingLevel", SoloParkingLevel.values(), SoloParkingLevel::getLabel);
        assertOptions("practiceTypes", PracticeType.values(), PracticeType::getLabel);
        assertOptions("carType", CarType.values(), CarType::getLabel);
        assertThat(question("drivingGoal").options()).isNull();
    }

    private <E extends Enum<E>> void assertOptions(
            String code, E[] values, Function<E, String> label) {
        List<Option> options = question(code).options();
        assertThat(options)
                .extracting(Option::code)
                .containsExactlyElementsOf(Arrays.stream(values).map(Enum::name).toList());
        assertThat(options)
                .extracting(Option::label)
                .containsExactlyElementsOf(Arrays.stream(values).map(label).toList());
        assertThat(options)
                .extracting(Option::order)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, values.length).boxed().toList());
    }

    @Test
    @DisplayName("화면 문구: Q1 3~9년, 연습유형은 온보딩 화면 표기")
    void 화면_문구() {
        Map<String, String> period = labels("drivingPeriod");
        assertThat(period).containsEntry("YEARS_3_9", "3~9년");

        Map<String, String> types = labels("practiceTypes");
        assertThat(types)
                .containsEntry("LEFT_RIGHT_TURN", "좌우 회전")
                .containsEntry("ROUNDABOUT", "회전 교차로")
                .containsEntry("UNPROTECTED_LEFT_TURN", "비보호 좌회전")
                .containsEntry("NARROW_ROAD", "좁은 도로 주행")
                .containsEntry("MULTILANE", "다차로 주행");
    }

    private Map<String, String> labels(String code) {
        return question(code).options().stream()
                .collect(Collectors.toMap(Option::code, Option::label));
    }

    @Test
    @DisplayName("흐름 규칙: exclusive는 Q3 NONE만, skipsFollowUps는 Q1 3~9년·10년 이상만, 나머지는 null")
    void 흐름_표시() {
        List<Option> all =
                questions().stream()
                        .filter(q -> q.options() != null)
                        .flatMap(q -> q.options().stream())
                        .toList();

        assertThat(question("roadExperiences").options())
                .filteredOn(o -> Boolean.TRUE.equals(o.exclusive()))
                .extracting(Option::code)
                .containsExactly("NONE");
        assertThat(all).filteredOn(o -> o.exclusive() != null).hasSize(1);

        assertThat(question("drivingPeriod").options())
                .filteredOn(o -> Boolean.TRUE.equals(o.skipsFollowUps()))
                .extracting(Option::code)
                .containsExactly("YEARS_3_9", "OVER_10_YEARS");
        assertThat(all).filteredOn(o -> o.skipsFollowUps() != null).hasSize(2);
    }

    @Test
    @DisplayName("showWhen은 Q4-1·Q4-2에만, 조건은 Q3에서 SOLO 선택")
    void 조건부_노출() {
        assertThat(questions())
                .filteredOn(q -> q.showWhen() != null)
                .extracting(Question::code)
                .containsExactly("soloDrivingRange", "soloParkingLevel");
        assertThat(question("soloDrivingRange").showWhen().question()).isEqualTo("roadExperiences");
        assertThat(question("soloDrivingRange").showWhen().anyOf()).containsExactly("SOLO");
    }

    @Test
    @DisplayName("필수는 Q1뿐, 입력 제약은 제출 검증과 같은 값")
    void 필수_제약() {
        assertThat(questions())
                .filteredOn(Question::required)
                .extracting(Question::code)
                .containsExactly("drivingPeriod");
        assertThat(question("practiceTypes").maxSelect()).isEqualTo(3);
        assertThat(question("drivingGoal").maxLength()).isEqualTo(30);
        assertThat(question("drivingGoal").placeholder()).isEqualTo("ex)강남 운전 자신있게 하기!");
    }
}
