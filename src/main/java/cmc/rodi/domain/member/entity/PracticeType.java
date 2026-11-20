package cmc.rodi.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 연습 유형(13종). 온보딩 선호(순위)·홈 정렬 필터·코스 태그에 공용으로 쓴다.
 *
 * <p>한글 라벨을 서버가 들고 있다가 코스 등록 폼(스펙 014)·온보딩 폼(스펙 019)으로 내려준다 — 앱 배포 없이 문구를 바꾸기 위함이다. 두 폼이 같은 라벨을 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum PracticeType {
    U_TURN("유턴"),
    LEFT_RIGHT_TURN("좌우 회전"),
    PARKING("주차"),
    LANE_CHANGE("차선변경"),
    INTERSECTION("교차로"),
    ROUNDABOUT("회전 교차로"),
    UNPROTECTED_LEFT_TURN("비보호 좌회전"),
    HIGHWAY_ENTRY("고속진입"),
    CORNERING("코너링"),
    NARROW_ROAD("좁은 도로 주행"),
    MULTILANE("다차로 주행"),
    MERGING("합류"),
    STRAIGHT("직선주행");

    private final String label;
}
