package cmc.rodi.global.auth.entity;

import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.exception.BusinessException;
import java.util.Locale;

/** 소셜 로그인 공급자. 새 공급자는 여기에 추가하고 대응 SocialClient 구현을 더한다(ADR 0008). */
public enum SocialProvider {
    KAKAO,
    APPLE;

    /** 경로변수 등 문자열(대소문자 무관)을 enum으로 변환. 미지원 값이면 {@link AuthErrorCode#UNSUPPORTED_PROVIDER}. */
    public static SocialProvider from(String value) {
        if (value == null) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        try {
            return SocialProvider.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
    }
}
