package cmc.rodi.global.auth.entity;

/** 소셜 로그인 공급자. 새 공급자는 여기에 추가하고 대응 SocialClient 구현을 더한다(ADR 0008). */
public enum SocialProvider {
    KAKAO,
    APPLE
}
