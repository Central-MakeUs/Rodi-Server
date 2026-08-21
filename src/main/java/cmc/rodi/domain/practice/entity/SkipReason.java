package cmc.rodi.domain.practice.entity;

import cmc.rodi.global.common.form.FormOption;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 미방문 이유. 화면 문구·순서를 서버가 들고 있다가 폼으로 내려준다(앱 배포 없이 문구 변경 가능).
 *
 * <p>{@link #OTHER}만 직접 입력을 받으며, 그때 상세 사유가 필수다. 폼 응답 변환은 신고 사유 폼과 같은 공통 구조({@code
 * global.common.form})를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum SkipReason {
    CHECK_REALTIME_TRAFFIC("실시간 교통정보를 보려고 했어요", 1),
    TOO_FAR("생각보다 멀었어요", 2),
    ROUTE_SEEMED_DIFFICULT("길이 어려워 보여요", 3),
    SCHEDULE_DID_NOT_MATCH("일정이 맞지 않았어요", 4),
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
