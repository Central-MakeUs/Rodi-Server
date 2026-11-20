package cmc.rodi.domain.review.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사용자 체감 난이도(5단계). 라벨은 후기 작성 폼(스펙 024)에 내려가는 화면 문구다. */
@Getter
@RequiredArgsConstructor
public enum Difficulty {
    VERY_EASY("매우 쉬움"),
    EASY("쉬움"),
    NORMAL("보통"),
    HARD("어려움"),
    VERY_HARD("매우 어려움");

    private final String label;
}
