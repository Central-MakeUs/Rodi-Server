package cmc.rodi.domain.member.policy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 닉네임 금칙어 판정(ADR 0014). 리소스 파일 세 개를 기동 시 메모리에 불변으로 로드한다.
 *
 * <ul>
 *   <li><b>정확 일치</b> — 비교 키 전체가 같으면 금칙어. 한 글자·일상어와 겹치는 단어(년·미친·ass)용.
 *   <li><b>포함</b> — 비교 키에 들어 있으면 금칙어. 단, 그 일치가 <b>허용 목록 단어 하나에 통째로 덮이면</b> 무시한다(시발점·초보지만).
 * </ul>
 *
 * <p>허용 단어를 지우고 검사하지 않고 위치로 판정하는 이유: "초보지랄"에서 "초보지"를 지우면 "랄"만 남아 "지랄"을 놓친다.
 */
@Component
public class ProhibitedWords {

    private final Set<String> exact;
    private final List<String> contains;
    private final List<String> allowed;

    public ProhibitedWords(
            @Value("${nickname.prohibited.contains-path:nickname/prohibited-contains.txt}")
                    String containsPath,
            @Value("${nickname.prohibited.exact-path:nickname/prohibited-exact.txt}")
                    String exactPath,
            @Value("${nickname.prohibited.allowed-path:nickname/allowed.txt}") String allowedPath) {
        this.contains = List.copyOf(new HashSet<>(load(containsPath)));
        this.exact = Set.copyOf(load(exactPath));
        this.allowed = List.copyOf(new HashSet<>(load(allowedPath)));
    }

    /**
     * 비교 키 — 소문자로 바꾸고 한글(가–힣)·영문만 남긴다. {@code _}·숫자·특수문자를 끼워 넣는 우회(씨_발·시1발)를 막는다. 저장값과는 별개로 비교에만
     * 쓴다.
     */
    public static String toCompareKey(String value) {
        StringBuilder key = new StringBuilder(value.length());
        for (char ch : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((ch >= '가' && ch <= '힣') || (ch >= 'a' && ch <= 'z')) {
                key.append(ch);
            }
        }
        return key.toString();
    }

    /** 닉네임이 금칙어에 걸리는지. */
    public boolean isProhibited(String nickname) {
        String key = toCompareKey(nickname);
        if (key.isEmpty()) {
            return false;
        }
        if (exact.contains(key)) {
            return true;
        }
        List<int[]> allowedSpans = findSpans(key, allowed);
        for (String word : contains) {
            for (int from = key.indexOf(word); from >= 0; from = key.indexOf(word, from + 1)) {
                if (!isCovered(from, from + word.length(), allowedSpans)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<int[]> findSpans(String key, List<String> words) {
        List<int[]> spans = new ArrayList<>();
        for (String word : words) {
            for (int from = key.indexOf(word); from >= 0; from = key.indexOf(word, from + 1)) {
                spans.add(new int[] {from, from + word.length()});
            }
        }
        return spans;
    }

    private static boolean isCovered(int start, int end, List<int[]> spans) {
        for (int[] span : spans) {
            if (span[0] <= start && end <= span[1]) {
                return true;
            }
        }
        return false;
    }

    /** 한 줄에 하나. {@code #} 주석·빈 줄은 무시하고, 비교 키로 바꿔 비는 항목(이모지 등)은 버린다. */
    private static List<String> load(String path) {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource(path).getInputStream(),
                                StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(ProhibitedWords::toCompareKey)
                    .filter(key -> !key.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("닉네임 금칙어 로드 실패: " + path, e);
        }
    }
}
