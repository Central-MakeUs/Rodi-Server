package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 후보 풀이 닉네임 형식 규칙(스펙 019 — 2~10자, 한글·영문·숫자·_, _는 처음·끝·연속 불가)을 지키는지 검증한다. */
class NicknamePoolFormatTest {

    private static final Pattern FORMAT = Pattern.compile("^[가-힣A-Za-z0-9]+(_[가-힣A-Za-z0-9]+)*$");

    @Test
    @DisplayName("후보 풀 990개가 모두 공백 없이 _ 형식이고 2~10자다")
    void 후보_풀_형식() throws IOException {
        List<String> pool = loadPool();

        assertThat(pool).hasSize(990).doesNotHaveDuplicates();
        assertThat(pool)
                .allSatisfy(
                        nickname -> {
                            assertThat(nickname).doesNotContain(" ");
                            assertThat(nickname).matches(FORMAT);
                            assertThat(nickname.length()).isBetween(2, 10);
                        });
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
