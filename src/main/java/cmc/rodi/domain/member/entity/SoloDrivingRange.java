package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Q4 혼자 운전 범위(단일 선택). 화면 라벨은 온보딩 폼으로 내려준다(스펙 019). */
@Getter
@RequiredArgsConstructor
public enum SoloDrivingRange {
    NEAR_HOME("집 근처"),
    FAMILIAR_ROAD("익숙한 길"),
    UNFAMILIAR_ROAD("낯선 도로"),
    HIGHWAY_LONG("고속 장거리");

    private final String label;
}
