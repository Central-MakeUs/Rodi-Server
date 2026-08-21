package cmc.rodi.domain.member.service;

import static cmc.rodi.domain.member.entity.PracticeType.CORNERING;
import static cmc.rodi.domain.member.entity.PracticeType.HIGHWAY_ENTRY;
import static cmc.rodi.domain.member.entity.PracticeType.INTERSECTION;
import static cmc.rodi.domain.member.entity.PracticeType.LANE_CHANGE;
import static cmc.rodi.domain.member.entity.PracticeType.LEFT_RIGHT_TURN;
import static cmc.rodi.domain.member.entity.PracticeType.MERGING;
import static cmc.rodi.domain.member.entity.PracticeType.MULTILANE;
import static cmc.rodi.domain.member.entity.PracticeType.NARROW_ROAD;
import static cmc.rodi.domain.member.entity.PracticeType.PARKING;
import static cmc.rodi.domain.member.entity.PracticeType.ROUNDABOUT;
import static cmc.rodi.domain.member.entity.PracticeType.STRAIGHT;
import static cmc.rodi.domain.member.entity.PracticeType.UNPROTECTED_LEFT_TURN;
import static cmc.rodi.domain.member.entity.PracticeType.U_TURN;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.PracticeType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 레벨별 추천 태그 고정 매핑(마이페이지 표시용). SEED~EXPLORER는 {@link PracticeType} 코드, NAVIGATOR는 연습유형이 아닌 활동 태그다(스펙
 * 006). 응답은 표시용 문자열 배열이라 enum이 아닌 코드 문자열로 통일한다. 레벨이 없으면(온보딩 전) 빈 목록.
 */
public final class RecommendationTags {

    private static final Map<Level, List<String>> BY_LEVEL =
            Map.of(
                    Level.SEED, codes(STRAIGHT, LEFT_RIGHT_TURN, LANE_CHANGE),
                    Level.ROOKIE, codes(U_TURN, INTERSECTION, PARKING),
                    Level.OWNER, codes(HIGHWAY_ENTRY, MERGING, MULTILANE),
                    Level.EXPLORER,
                            codes(UNPROTECTED_LEFT_TURN, ROUNDABOUT, NARROW_ROAD, CORNERING),
                    Level.NAVIGATOR, List.of("REGISTER_COURSE", "WRITE_REVIEW", "SHARE_COURSE"));

    private RecommendationTags() {}

    /** 레벨의 추천 태그. 레벨이 null이면 빈 목록. */
    public static List<String> of(Level level) {
        return level == null ? List.of() : BY_LEVEL.getOrDefault(level, List.of());
    }

    private static List<String> codes(PracticeType... types) {
        return Arrays.stream(types).map(PracticeType::name).toList();
    }
}
