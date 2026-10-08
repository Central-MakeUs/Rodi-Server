package cmc.rodi.domain.member.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/** 닉네임 정책(스펙 019) — 정규화·길이·문자·금칙어 판정과 후보 풀 호환을 검증한다. */
class NicknamePolicyTest {

    private final NicknamePolicy policy =
            new NicknamePolicy(
                    new ProhibitedWords(
                            "nickname/prohibited-contains.txt",
                            "nickname/prohibited-exact.txt",
                            "nickname/allowed.txt"));

    @Test
    @DisplayName("정규화: 앞뒤 공백을 지우고 NFD 한글을 NFC로 합친다. null은 빈 문자열")
    void 정규화() {
        String nfd = Normalizer.normalize("초보", Normalizer.Form.NFD);

        assertThat(policy.normalize("  초보_탈출 ")).isEqualTo("초보_탈출");
        assertThat(policy.normalize(nfd)).isEqualTo("초보");
        assertThat(policy.normalize(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"초보", "가나다라마바사아자차", "ab", "Kim_123"})
    @DisplayName("통과: 2~10자의 한글·영문·숫자·_ 닉네임")
    void 통과(String nickname) {
        assertThat(policy.check(nickname)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "초", "가나다라마바사아자차카", "ㅋ", "😀😀😀😀😀😀😀😀😀😀😀"})
    @DisplayName("길이 위반: 2자 미만·10자 초과(grapheme 기준)")
    void 길이_위반(String nickname) {
        assertThat(policy.check(nickname)).contains(NicknameViolation.INVALID_LENGTH);
    }

    @Test
    @DisplayName("길이 위반: 코드유닛을 부풀린 입력은 경계 순회 전에 거절한다")
    void 부풀린_입력() {
        assertThat(policy.check("가".repeat(500))).contains(NicknameViolation.INVALID_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"초보 탈출", "_초보", "초보_", "초보__탈출", "ㅋㅋ초보", "ㅋㅋ", "초보😀", "초보!", "초보 탈출"})
    @DisplayName("문자 위반: 공백·자모·이모지·특수문자·_ 처음/끝/연속")
    void 문자_위반(String nickname) {
        assertThat(policy.check(nickname)).contains(NicknameViolation.INVALID_CHARACTER);
    }

    @Test
    @DisplayName("이모지는 한 글자로 센다 — 2자 이모지 닉네임은 길이가 아니라 문자 위반")
    void 이모지_한글자() {
        assertThat(policy.check("👨‍👩‍👧‍👦👍🏻")).contains(NicknameViolation.INVALID_CHARACTER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨_발", "운영자", "미친"})
    @DisplayName("금칙어 위반: 형식을 통과한 뒤 금칙어로 판정한다")
    void 금칙어_위반(String nickname) {
        assertThat(policy.check(nickname)).contains(NicknameViolation.PROHIBITED_WORD);
    }

    @Test
    @DisplayName("후보 풀 990개가 모두 정책(형식·금칙어)을 통과한다")
    void 후보_풀_전체_통과() throws IOException {
        List<String> pool = loadPool();

        assertThat(pool).hasSize(990).doesNotHaveDuplicates();
        assertThat(pool).allSatisfy(nickname -> assertThat(policy.check(nickname)).isEmpty());
    }

    private List<String> loadPool() throws IOException {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource("nickname/nicknames.txt").getInputStream(),
                                StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        }
    }
}
