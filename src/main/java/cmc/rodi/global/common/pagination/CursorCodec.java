package cmc.rodi.global.common.pagination;

import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서(keyset) 페이지네이션용 불투명 토큰 인코더(ADR 0010). 정렬값과 id(타이브레이커)를 base64로 감싸 클라이언트에 노출한다. 정렬값의 해석(거리·시각
 * 등)은 호출부가 담당한다.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {}

    public record Cursor(String sortValue, long id) {}

    public static String encode(String sortValue, long id) {
        String raw = sortValue + SEPARATOR + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf(SEPARATOR);
            return new Cursor(raw.substring(0, sep), Long.parseLong(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
