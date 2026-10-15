package cmc.rodi.global.common.form;

/** 폼 문항 유형. 신고·미방문 사유 폼은 단일 선택만, 온보딩 폼(스펙 019)은 네 유형을 모두 쓴다. */
public enum FormType {
    /** 하나만 고른다. */
    SINGLE_SELECT,
    /** 여러 개를 고른다. 배타 선택지({@code exclusive})는 단독으로만. */
    MULTI_SELECT,
    /** 여러 개를 순서대로 고른다. 고른 순서가 우선순위다. */
    RANKED_SELECT,
    /** 자유 입력. */
    TEXT
}
