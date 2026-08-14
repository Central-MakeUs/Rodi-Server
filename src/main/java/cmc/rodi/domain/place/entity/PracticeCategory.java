package cmc.rodi.domain.place.entity;

import cmc.rodi.domain.member.entity.PracticeType;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 코스 등록 폼의 연습유형 카테고리(스펙 014).
 *
 * <p><b>DB에 저장하지 않는다</b> — 화면 탐색용 묶음일 뿐이고, 코스에 저장·매칭되는 값은 {@link PracticeType}뿐이다. 카테고리는 선택된 유형
 * 집합에서 역산할 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum PracticeCategory {
    BASIC_DRIVING(
            "기초 주행",
            1,
            List.of(PracticeType.STRAIGHT, PracticeType.LEFT_RIGHT_TURN, PracticeType.LANE_CHANGE)),
    CITY_BASIC("도심 기본", 2, List.of(PracticeType.INTERSECTION, PracticeType.U_TURN)),
    PARKING_SPACE("주차", 3, List.of(PracticeType.PARKING)),
    TRAFFIC_FLOW(
            "도로 흐름",
            4,
            List.of(PracticeType.MULTILANE, PracticeType.MERGING, PracticeType.HIGHWAY_ENTRY)),
    COMPLEX(
            "복합 상황",
            5,
            List.of(
                    PracticeType.ROUNDABOUT,
                    PracticeType.UNPROTECTED_LEFT_TURN,
                    PracticeType.NARROW_ROAD,
                    PracticeType.CORNERING));

    /** 코스 하나에 붙일 수 있는 연습유형 수. 카테고리를 넘나들며 합산한다. */
    public static final int MAX_SELECT = 3;

    /** 최대 개수를 넘겨 선택했을 때 앱이 띄울 문구(CM-08). */
    public static final String MAX_SELECT_EXCEEDED_MESSAGE = "연습유형은 최대 3개까지 선택할 수 있어요.";

    private final String label;
    private final int order;
    private final List<PracticeType> practiceTypes;
}
