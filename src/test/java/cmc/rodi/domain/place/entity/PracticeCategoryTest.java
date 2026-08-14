package cmc.rodi.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.PracticeType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코스 등록 폼의 카테고리 구성(스펙 014) — 매핑·순서·"전체" 버튼 규칙. */
class PracticeCategoryTest {

    @Test
    @DisplayName("카테고리는 5개이고 order가 1부터 빠짐없이 이어진다")
    void 순서() {
        List<Integer> orders =
                Arrays.stream(PracticeCategory.values()).map(PracticeCategory::getOrder).toList();

        assertThat(orders).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("항목이 4개인 복합 상황만 전체 버튼이 없다 — 전체 선택이 최대 3개를 넘기 때문")
    void 전체_버튼() {
        assertThat(PracticeCategory.COMPLEX.getPracticeTypes()).hasSize(4);
        assertThat(PracticeCategory.COMPLEX.isSelectAllEnabled()).isFalse();

        assertThat(
                        Arrays.stream(PracticeCategory.values())
                                .filter(c -> !c.isSelectAllEnabled())
                                .toList())
                .containsExactly(PracticeCategory.COMPLEX);
    }

    @Test
    @DisplayName("주차는 PARKING 하나이고, 도심 기본에도 PARKING이 겹쳐 들어간다")
    void 주차_중복_노출() {
        assertThat(PracticeCategory.PARKING_SPACE.getPracticeTypes())
                .containsExactly(PracticeType.PARKING);
        assertThat(PracticeCategory.CITY_BASIC.getPracticeTypes()).contains(PracticeType.PARKING);
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
