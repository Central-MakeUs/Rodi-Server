package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레벨 → 추천 태그 고정 매핑(스펙 006) 검증. */
class RecommendationTagsTest {

    @Test
    @DisplayName("SEED~EXPLORER는 PracticeType 코드, 순서 유지")
    void 연습유형_레벨() {
        assertThat(RecommendationTags.of(Level.SEED))
                .containsExactly("STRAIGHT", "LEFT_RIGHT_TURN", "LANE_CHANGE");
        assertThat(RecommendationTags.of(Level.ROOKIE))
                .containsExactly("U_TURN", "INTERSECTION", "PARKING");
        assertThat(RecommendationTags.of(Level.OWNER))
                .containsExactly("HIGHWAY_ENTRY", "MERGING", "MULTILANE");
        assertThat(RecommendationTags.of(Level.EXPLORER))
                .containsExactly("UNPROTECTED_LEFT_TURN", "ROUNDABOUT", "NARROW_ROAD", "CORNERING");
    }

    @Test
    @DisplayName("NAVIGATOR는 활동 태그(연습유형 아님)")
    void 네비게이터_액션() {
        assertThat(RecommendationTags.of(Level.NAVIGATOR))
                .containsExactly("REGISTER_COURSE", "WRITE_REVIEW", "SHARE_COURSE");
    }

    @Test
    @DisplayName("레벨이 없으면(온보딩 전) 빈 목록")
    void 레벨_없음() {
        assertThat(RecommendationTags.of(null)).isEmpty();
    }
}
