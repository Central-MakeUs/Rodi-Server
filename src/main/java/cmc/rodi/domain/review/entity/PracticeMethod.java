package cmc.rodi.domain.review.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 연습 방법. 라벨은 후기 작성 폼(스펙 024)에 내려가는 화면 문구다. */
@Getter
@RequiredArgsConstructor
public enum PracticeMethod {
    SOLO("혼자 연습"),
    ACCOMPANIED("동승자와 연습");

    private final String label;
}
