package cmc.rodi.global.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.text.BreakIterator;
import java.util.Locale;

/**
 * {@link GraphemeSize} 판정기. 글자 수는 {@link BreakIterator}의 문자 경계로 세고, 저장 크기는 코드유닛 상한으로 따로 막는다.
 *
 * <p>{@code BreakIterator}는 <b>스레드 안전하지 않아</b> 매 호출마다 새로 만든다. 로케일에 따라 경계 규칙이 달라지지 않아야 하므로 {@link
 * Locale#ROOT}로 고정한다.
 */
public class GraphemeSizeValidator implements ConstraintValidator<GraphemeSize, String> {

    private int max;
    private int codeUnitCeiling;

    @Override
    public void initialize(GraphemeSize annotation) {
        this.max = annotation.max();
        this.codeUnitCeiling = annotation.max() * GraphemeSize.CODE_UNIT_MULTIPLIER;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 필수 여부는 @NotNull·@NotBlank가 정한다
        }
        // 코드유닛부터 본다 — 부풀린 입력을 경계 순회 전에 잘라내고, 정상 입력은 여기서 걸릴 수 없다
        if (value.length() > codeUnitCeiling) {
            return false;
        }
        return countGraphemes(value) <= max;
    }

    /** 사용자가 한 글자로 인식하는 단위(grapheme cluster)의 개수. */
    private static int countGraphemes(String value) {
        BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.ROOT);
        boundaries.setText(value);
        int count = 0;
        while (boundaries.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
