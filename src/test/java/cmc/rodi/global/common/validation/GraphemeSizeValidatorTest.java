package cmc.rodi.global.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Payload;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** grapheme cluster 카운트 규칙(스펙 018) — 사용자가 한 글자로 보는 것은 UTF-16 길이와 무관하게 1로 센다. */
class GraphemeSizeValidatorTest {

    private static GraphemeSizeValidator validator(int max) {
        GraphemeSizeValidator validator = new GraphemeSizeValidator();
        validator.initialize(graphemeSize(max));
        return validator;
    }

    /** 애노테이션 인스턴스를 직접 만들어 넘긴다 — 컨테이너 없이 판정기만 검증한다. */
    private static GraphemeSize graphemeSize(int max) {
        return new GraphemeSize() {
            @Override
            public int max() {
                return max;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return GraphemeSize.class;
            }
        };
    }

    @ParameterizedTest(name = "{1}(UTF-16 {2}) → 1자")
    @DisplayName("사용자가 한 글자로 보는 것은 UTF-16 길이와 무관하게 1자다")
    @CsvSource({
        "가, 한글, 1",
        "😀, 기본 이모지, 2",
        "❤️, 변이선택자, 2",
        "👍🏻, 피부톤, 4",
        "👨‍👩‍👧‍👦, ZWJ 가족, 11",
        "🇰🇷, 국기, 4",
        "👩‍💻, ZWJ 직업, 5",
    })
    void 한_글자로_센다(String input, String description, int utf16Length) {
        assertThat(input.length()).as("UTF-16 길이 전제").isEqualTo(utf16Length);
        // max=1을 통과한다 = 1자로 셌다는 뜻
        assertThat(validator(1).isValid(input, null)).as(description).isTrue();
    }

    @Test
    @DisplayName("이모지 30개는 30자로 세어 통과하고, 31개는 걸린다")
    void 이모지_경계값() {
        assertThat(validator(30).isValid("😀".repeat(30), null)).isTrue();
        assertThat(validator(30).isValid("😀".repeat(31), null)).isFalse();
        assertThat(validator(30).isValid("👨‍👩‍👧‍👦".repeat(30), null)).isTrue();
        assertThat(validator(30).isValid("👨‍👩‍👧‍👦".repeat(31), null)).isFalse();
    }

    @Test
    @DisplayName("한글·영문 경계값은 기존과 같다")
    void 일반_문자_경계값() {
        assertThat(validator(30).isValid("가".repeat(30), null)).isTrue();
        assertThat(validator(30).isValid("가".repeat(31), null)).isFalse();
        assertThat(validator(150).isValid("a".repeat(150), null)).isTrue();
        assertThat(validator(150).isValid("a".repeat(151), null)).isFalse();
    }

    @Test
    @DisplayName("null·빈 문자열은 통과한다(필수 여부는 @NotNull·@NotBlank가 정한다)")
    void 빈값은_통과() {
        assertThat(validator(30).isValid(null, null)).isTrue();
        assertThat(validator(30).isValid("", null)).isTrue();
        assertThat(validator(30).isValid("   ", null)).isTrue();
    }

    @Test
    @DisplayName("한 글자여도 코드유닛 상한을 넘기면 거절한다(저장 크기 방어)")
    void 코드유닛_상한() {
        int max = 30;
        int ceiling = max * GraphemeSize.CODE_UNIT_MULTIPLIER; // 600

        // 결합문자를 이어 붙이면 grapheme은 1인데 코드유닛만 부푼다
        String inflated = "가" + "́".repeat(ceiling);
        assertThat(validator(max).isValid(inflated, null)).isFalse();

        String withinCeiling = "가" + "́".repeat(ceiling - 1);
        assertThat(withinCeiling.length()).isEqualTo(ceiling);
        assertThat(validator(max).isValid(withinCeiling, null)).isTrue();
    }
}
