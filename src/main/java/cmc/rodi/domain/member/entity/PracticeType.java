package cmc.rodi.domain.member.entity;

/** 연습 유형(13종). 온보딩 선호(순위) 저장에 사용, 이후 코스와 공용 예정. */
public enum PracticeType {
    U_TURN, // 유턴
    LEFT_RIGHT_TURN, // 좌우회전
    PARKING, // 주차
    LANE_CHANGE, // 차선변경
    INTERSECTION, // 교차로
    ROUNDABOUT, // 회전교차로
    UNPROTECTED_LEFT_TURN, // 비보호좌회전
    HIGHWAY_ENTRY, // 고속진입
    CORNERING, // 코너링
    NARROW_ROAD, // 좁은 도로
    MULTILANE, // 다차로주행
    MERGING, // 합류
    STRAIGHT // 직선주행
}
