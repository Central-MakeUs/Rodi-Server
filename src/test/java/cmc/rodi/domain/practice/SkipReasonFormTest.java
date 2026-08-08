package cmc.rodi.domain.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cmc.rodi.domain.practice.entity.SkipReason;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.global.common.form.FormOption;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 미방문 이유 폼. 저장이 없어 의존성 없이 만든 서비스로 검증한다(신고 사유 폼과 같은 구조). */
class SkipReasonFormTest {

    private final PracticeService practiceService = new PracticeService(null, null, null);

    @Test
    @DisplayName("화면 순서대로 선택지가 내려가고 기타만 직접 입력을 받는다")
    void 미방문_이유_폼() {
        FormResponse form = practiceService.getSkipReasonForm();

        assertThat(form.questionId()).isEqualTo("WHY_NOT_PRACTICED");
        assertThat(form.type()).isEqualTo(FormType.SINGLE_SELECT);
        assertThat(form.title()).isEqualTo("왜 연습을 다녀오지 않았나요?");
        assertThat(form.description()).isEqualTo("이유를 알려주시면 더 나은 코스를 추천해드릴게요!");
        assertThat(form.required()).isTrue();

        assertThat(form.options())
                .extracting(FormOption::code, FormOption::label, FormOption::order)
                .containsExactly(
                        tuple("CHECK_REALTIME_TRAFFIC", "실시간 교통정보를 보려고 했어요", 1),
                        tuple("TOO_FAR", "생각보다 멀었어요", 2),
                        tuple("ROUTE_SEEMED_DIFFICULT", "길이 어려워 보여요", 3),
                        tuple("SCHEDULE_DID_NOT_MATCH", "일정이 맞지 않았어요", 4),
                        tuple("OTHER", "기타", 5));

        // 기타만 직접 입력 — 나머지는 textInput* 자체가 없다(NON_NULL로 응답에서 빠진다)
        List<FormOption> options = form.options();
        assertThat(options.subList(0, 4))
                .allSatisfy(
                        option -> {
                            assertThat(option.requiresTextInput()).isFalse();
                            assertThat(option.textInputPlaceholder()).isNull();
                            assertThat(option.textInputMaxLength()).isNull();
                        });

        FormOption other = options.get(4);
        assertThat(other.requiresTextInput()).isTrue();
        assertThat(other.textInputPlaceholder()).isEqualTo("이유를 작성해주세요");
        assertThat(other.textInputMaxLength()).isEqualTo(SkipReason.TEXT_INPUT_MAX_LENGTH);
    }
}
