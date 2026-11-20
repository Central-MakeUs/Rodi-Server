package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Q5 혼자 주차 수준(단일 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum SoloParkingLevel {
    NONE("없음"),
    WIDE_ONLY("넓은 곳만"),
    FAMILIAR_PLACE("익숙한 곳"),
    MOSTLY_POSSIBLE("대부분 가능");

    private final String label;
}
