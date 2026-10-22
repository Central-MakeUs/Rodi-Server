package cmc.rodi.domain.member.policy;

import cmc.rodi.global.common.validation.GraphemeSize;
import cmc.rodi.global.common.validation.GraphemeSizeValidator;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 닉네임 형식·금칙어 정책(스펙 020, ADR 0014). DB를 모르므로 중복 검사는 하지 않는다 — 서비스가 이 판정 뒤에 한다.
 *
 * <p>입력은 {@link #normalize}로 정규화한 값을 받는다. 판정은 길이 → 문자 → 금칙어 순이며 처음 걸린 사유 하나만 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 10;

    /** 닉네임을 바꾼 뒤 다시 바꿀 수 있기까지의 기간 — 바꾼 시각부터 정확히 30일(스펙 020, 이슈 #80). */
    public static final Duration CHANGE_COOLDOWN = Duration.ofDays(30);

    /** 완성형 한글·영문·숫자 덩어리를 _ 하나로 잇는다 — _의 처음·끝·연속과 공백·자모·이모지가 모두 여기서 걸린다. */
    private static final Pattern FORMAT = Pattern.compile("^[가-힣A-Za-z0-9]+(_[가-힣A-Za-z0-9]+)*$");

    private final ProhibitedWords prohibitedWords;

    /** 저장·판정용 정규화 — NFC(자모 분해로 온 한글을 완성형으로) 후 앞뒤 공백 제거. null은 빈 문자열(길이 위반)로 본다. */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
    }

    /** 정규화된 닉네임의 위반 사유. 통과면 빈 값. */
    public Optional<NicknameViolation> check(String nickname) {
        if (!hasValidLength(nickname)) {
            return Optional.of(NicknameViolation.INVALID_LENGTH);
        }
        if (!FORMAT.matcher(nickname).matches()) {
            return Optional.of(NicknameViolation.INVALID_CHARACTER);
        }
        if (prohibitedWords.isProhibited(nickname)) {
            return Optional.of(NicknameViolation.PROHIBITED_WORD);
        }
        return Optional.empty();
    }

    private static boolean hasValidLength(String nickname) {
        // 부풀린 입력은 경계 순회 전에 자른다(GraphemeSize와 같은 코드유닛 상한)
        if (nickname.length() > MAX_LENGTH * GraphemeSize.CODE_UNIT_MULTIPLIER) {
            return false;
        }
        int length = GraphemeSizeValidator.countGraphemes(nickname);
        return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }
}
