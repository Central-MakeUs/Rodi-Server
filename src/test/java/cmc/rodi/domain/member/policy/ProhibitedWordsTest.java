package cmc.rodi.domain.member.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 금칙어 판정(ADR 0014) — 실제 리소스 파일로 우회·오탐 사례를 검증한다. */
class ProhibitedWordsTest {

    private final ProhibitedWords words =
            new ProhibitedWords(
                    "nickname/prohibited-contains.txt",
                    "nickname/prohibited-exact.txt",
                    "nickname/allowed.txt");

    @ParameterizedTest
    @ValueSource(
            strings = {
                "씨발",
                "씨_발",
                "시1발",
                "개새끼운전",
                "병신같은",
                "운영자",
                "로디_공식",
                "ROdi",
                "Admin_kim",
                "Fuck_you",
                "초보지랄",
                "18놈",
                "미친",
                "ass",
                "sex",
                "Rapist"
            })
    @DisplayName("금칙어: 포함·정확 일치·우회(_·숫자·대소문자)를 거부한다")
    void 금칙어_거부(String nickname) {
        assertThat(words.isProhibited(nickname)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "청년",
                "소년_운전",
                "미친듯이",
                "시간부자",
                "젖소",
                "엿장수",
                "유모차",
                "class",
                "passenger",
                "therapist",
                "basement",
                "social",
                "시발점",
                "멜로디",
                "새끼손가락",
                "초보지만",
                "초보지킴이",
                "prodigy",
                "badminton",
                "최후장면",
                "등신대",
                "정액제",
                "차근차근_토끼"
            })
    @DisplayName("오탐 방지: 금칙어를 품은 정상 단어는 통과한다")
    void 정상_단어_통과(String nickname) {
        assertThat(words.isProhibited(nickname)).isFalse();
    }

    @Test
    @DisplayName("비교 키: 소문자로 바꾸고 한글·영문만 남긴다")
    void 비교_키() {
        assertThat(ProhibitedWords.toCompareKey("Ab_1가!나ㅋ😀")).isEqualTo("ab가나");
    }

    @Nested
    @DisplayName("파일 로드")
    class Loading {

        private final ProhibitedWords testWords =
                new ProhibitedWords(
                        "nickname-test/contains.txt",
                        "nickname-test/exact.txt",
                        "nickname-test/allowed.txt");

        @Test
        @DisplayName("주석·빈 줄은 무시하고, 항목도 비교 키로 정규화해 로드한다")
        void 주석_빈줄_무시_정규화() {
            assertThat(testWords.isProhibited("주석")).isFalse();
            assertThat(testWords.isProhibited("앞나쁜말뒤")).isTrue();
            assertThat(testWords.isProhibited("badword")).isTrue();
            assertThat(testWords.isProhibited("금지")).isTrue();
            assertThat(testWords.isProhibited("금지어")).isFalse();
        }

        @Test
        @DisplayName("허용 단어에 통째로 덮인 일치만 무시한다")
        void 허용_목록_범위() {
            assertThat(testWords.isProhibited("나쁜말괜찮")).isFalse();
            assertThat(testWords.isProhibited("나쁜말괜찮나쁜말")).isTrue();
        }
    }
}
