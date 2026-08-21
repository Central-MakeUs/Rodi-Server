package cmc.rodi.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.PracticeType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코스 등록 폼의 카테고리 구성(스펙 014) — 매핑·순서. */
class PracticeCategoryTest {

    @Test
    @DisplayName("카테고리는 5개이고 order가 1부터 빠짐없이 이어진다")
    void 순서() {
        List<Integer> orders =
                Arrays.stream(PracticeCategory.values()).map(PracticeCategory::getOrder).toList();

        assertThat(orders).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("카테고리별 연습유형은 기획 표 순서 그대로 내려간다")
    void 카테고리_매핑() {
        assertThat(PracticeCategory.BASIC_DRIVING.getPracticeTypes())
                .containsExactly(
                        PracticeType.STRAIGHT,
                        PracticeType.LEFT_RIGHT_TURN,
                        PracticeType.LANE_CHANGE);
        assertThat(PracticeCategory.CITY_BASIC.getPracticeTypes())
                .containsExactly(PracticeType.INTERSECTION, PracticeType.U_TURN);
        assertThat(PracticeCategory.PARKING_SPACE.getPracticeTypes())
                .containsExactly(PracticeType.PARKING);
        assertThat(PracticeCategory.TRAFFIC_FLOW.getPracticeTypes())
                .containsExactly(
                        PracticeType.MULTILANE, PracticeType.MERGING, PracticeType.HIGHWAY_ENTRY);
        assertThat(PracticeCategory.COMPLEX.getPracticeTypes())
                .containsExactly(
                        PracticeType.ROUNDABOUT,
                        PracticeType.UNPROTECTED_LEFT_TURN,
                        PracticeType.NARROW_ROAD,
                        PracticeType.CORNERING);
    }

    @Test
    @DisplayName("13종 연습유형이 모두 어느 카테고리엔가 들어간다")
    void 전체_유형_포함() {
        List<PracticeType> covered =
                Arrays.stream(PracticeCategory.values())
                        .flatMap(c -> c.getPracticeTypes().stream())
                        .distinct()
                        .toList();

        assertThat(covered).containsExactlyInAnyOrder(PracticeType.values());
    }

    @Test
    @DisplayName("모든 연습유형에 한글 라벨이 있다")
    void 라벨() {
        assertThat(Arrays.stream(PracticeType.values()).map(PracticeType::getLabel))
                .allSatisfy(label -> assertThat(label).isNotBlank());
        assertThat(PracticeType.STRAIGHT.getLabel()).isEqualTo("직선주행");
    }
}
