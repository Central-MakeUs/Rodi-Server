package cmc.rodi.global.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사용자가 화면에서 세는 글자 수(grapheme cluster) 기준 길이 제한(스펙 018).
 *
 * <p>{@code @Size}는 자바 {@code String.length()} — 즉 <b>UTF-16 코드유닛</b>을 센다. BMP 밖 이모지는 서로게이트 페어라 한
 * 글자가 2로, 피부톤은 4로, ZWJ 가족(👨‍👩‍👧‍👦)은 11로 계산돼 앱이 세는 수와 어긋난다. iOS {@code String.count}와 안드로이드
 * {@code BreakIterator}는 이들을 모두 1로 세므로, 서버도 같은 단위로 맞춘다.
 *
 * <p>{@code null}은 통과시킨다 — 선택 입력 필드에 쓰이므로 필수 여부는 {@code @NotNull}·{@code @NotBlank}가 따로 정한다.
 */
@Documented
@Constraint(validatedBy = GraphemeSizeValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphemeSize {

    /**
     * 저장 크기 방어용 코드유닛 상한 배수 — 실제 상한은 {@code max × 이 값}(UTF-16 코드유닛)이다.
     *
     * <p>grapheme cluster는 결합문자·ZWJ를 무한히 이을 수 있어 <b>이론상 길이 제한이 없다</b>. 정상 입력 중 가장 긴 축인 ZWJ 가족이 11
     * 코드유닛이므로 20배면 충분히 여유롭고, 한 글자를 수천 코드유닛으로 부풀린 비정상 입력만 걸러진다.
     */
    int CODE_UNIT_MULTIPLIER = 20;

    /** 허용할 최대 글자 수(grapheme cluster). */
    int max();

    String message() default "{max}자 이하로 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
