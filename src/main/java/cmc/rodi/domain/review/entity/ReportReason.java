package cmc.rodi.domain.review.entity;

import cmc.rodi.global.common.form.FormOption;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 후기 신고 사유. 화면 문구·순서를 서버가 들고 있다가 신고 사유 폼으로 그대로 내려준다(앱 배포 없이 문구 변경 가능).
 *
 * <p>{@link #OTHER}만 직접 입력을 받으며, 그때 상세 사유가 필수다.
 */
@Getter
@RequiredArgsConstructor
public enum ReportReason {
    SPAM("스팸/광고", 1),
    ABUSE("욕설, 음란성, 혐오 표현", 2),
    IRRELEVANT("코스와 무관한 내용", 3),
    FALSE_INFO("허위정보", 4),
    OTHER("기타", 5);

    /** 직접 입력 안내 문구·상한(OTHER 전용). */
    public static final String TEXT_INPUT_PLACEHOLDER = "이유를 작성해주세요";

    public static final int TEXT_INPUT_MAX_LENGTH = 100;

    private final String label;
    private final int order;

    public boolean requiresTextInput() {
        return this == OTHER;
    }

    public FormOption toOption() {
        return requiresTextInput()
                ? FormOption.withTextInput(
                        name(), label, order, TEXT_INPUT_PLACEHOLDER, TEXT_INPUT_MAX_LENGTH)
                : FormOption.of(name(), label, order);
    }
}
