package cmc.rodi.domain.review.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 혼잡도(3단계). 라벨은 후기 작성 폼(스펙 024)에 내려가는 화면 문구다. */
@Getter
@RequiredArgsConstructor
public enum Congestion {
    QUIET("한산해요"),
    NORMAL("보통이에요"),
    CROWDED("복잡해요");

    private final String label;
}
