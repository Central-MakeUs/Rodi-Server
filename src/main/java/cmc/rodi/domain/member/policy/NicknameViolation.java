package cmc.rodi.domain.member.policy;

/** 닉네임 정책 위반 사유(스펙 020). 판정 순서는 길이 → 문자 → 금칙어이고, 처음 걸린 하나만 돌려준다. */
public enum NicknameViolation {
    /** 2~10자(grapheme 기준)가 아니다. */
    INVALID_LENGTH,
    /** 허용 문자(완성형 한글·영문·숫자·_)가 아니거나 _가 처음·끝·연속에 있다. */
    INVALID_CHARACTER,
    /** 금칙어에 걸린다. */
    PROHIBITED_WORD
}
