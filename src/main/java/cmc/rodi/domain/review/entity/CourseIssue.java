package cmc.rodi.domain.review.entity;

import cmc.rodi.global.common.form.FormOption;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * '별로에요' 후기의 코스 문제 항목(복수 선택, 스펙 024). 화면 문구·순서를 서버가 들고 있다가 코스 문제 항목 폼으로 그대로 내려준다(앱 배포 없이 문구 변경 가능).
 *
 * <p>{@link #OTHER}만 직접 입력을 받으며, 그때 상세 내용이 필수다. 값을 추가하면 DB CHECK({@code
 * ck_review_course_issue_issue})도 함께 바꾼다.
 */
@Getter
@RequiredArgsConstructor
public enum CourseIssue {
    VEHICLE_RESTRICTED("차량 진입이 제한된 장소예요", 1),
    ROAD_INFO_MISMATCH("도로 정보가 실제와 달라요", 2),
    CONSTRUCTION("공사·통제 등으로 현재 주행할 수 없어요", 3),
    NOT_FOR_PRACTICE("연습 코스로 적합하지 않아요", 4),
    OTHER("기타", 5);

    /** 직접 입력 안내 문구·상한(OTHER 전용). 상한은 grapheme 기준(스펙 018). */
    public static final String TEXT_INPUT_PLACEHOLDER = "이유를 작성해주세요.";

    public static final int TEXT_INPUT_MAX_LENGTH = 30;

    private final String label;
    private final int order;

    /** 직접 입력을 받는 항목인지. 기타({@link #OTHER})만 해당한다. */
    public boolean requiresTextInput() {
        return this == OTHER;
    }

    /** 폼 선택지로 바꾼다. 직접 입력 항목은 안내 문구·최대 길이를 함께 싣는다. */
    public FormOption toOption() {
        return requiresTextInput()
                ? FormOption.withTextInput(
                        name(), label, order, TEXT_INPUT_PLACEHOLDER, TEXT_INPUT_MAX_LENGTH)
                : FormOption.of(name(), label, order);
    }
}
