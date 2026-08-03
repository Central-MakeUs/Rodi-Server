package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.place.dto.PlaceSuggestion;
import cmc.rodi.domain.place.dto.RelatedSearchRequest;
import cmc.rodi.domain.place.dto.RelatedSearchResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연관 검색어(스펙 009) — 지역·장소명 자동완성을 실제 DB·번들 CSV로 검증. 장소명은 고유 토큰("트레스트마커")으로 다른 테스트 시드와 겹치지 않게 격리한다.
 * {@code @Transactional}로 각 테스트를 롤백해 seed 누적 방지.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(cmc.rodi.support.TestcontainersConfiguration.class)
class RelatedSearchIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String TOKEN = "트레스트마커";

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private RelatedSearchRequest request(String keyword, int size, String cursor) {
        return new RelatedSearchRequest(keyword, size, cursor);
    }

    private void seedPlaces() {
        // 관련도(matchPos) 오름차순 기대: 앞쪽 매칭 코스 → 주차장 → 뒤쪽 매칭 코스
        courseRepository.save(
                Course.builder()
                        .name(TOKEN + " 코스A") // matchPos=1(앞쪽)
                        .address("서울특별시 강남구")
                        .location(point(37.50, 127.03))
                        .build());
        parkingRepository.save(
                Parking.builder()
                        .name(TOKEN + " 주차장") // matchPos=1(앞쪽), 코스와 동률 → id로 tie-break
                        .address("서울특별시 서초구")
                        .location(point(37.49, 127.02))
                        .capacity(50)
                        .build());
        courseRepository.save(
                Course.builder()
                        .name("코스B " + TOKEN) // matchPos=뒤쪽(더 큼)
                        .address("서울특별시 송파구")
                        .location(point(37.51, 127.10))
                        .build());
        // 매칭 안 되는 place(필터링 확인용)
        courseRepository.save(
                Course.builder()
                        .name("상관없는코스")
                        .address("서울특별시 노원구")
                        .location(point(37.65, 127.06))
                        .build());
    }

    @Test
    @DisplayName("장소명 관련도: 앞쪽 매칭이 먼저, 코스+주차장 모두 포함, 2페이지 연속성·totalCount")
    void 장소명_관련도_정렬() {
        seedPlaces();

        CursorPage<PlaceSuggestion> page1 =
                placeQueryService.relatedSearch(request(TOKEN, 2, null)).places();

        assertThat(page1.totalCount()).isEqualTo(3L); // "상관없는코스" 제외
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.items())
                .extracting(PlaceSuggestion::name)
                .containsExactly(TOKEN + " 코스A", TOKEN + " 주차장"); // 앞쪽 매칭 2개(id 순 tie-break)

        PlaceSuggestion course = page1.items().get(0);
        assertThat(course.region()).isEqualTo("서울특별시 강남구");

        CursorPage<PlaceSuggestion> page2 =
                placeQueryService.relatedSearch(request(TOKEN, 2, page1.nextCursor())).places();
        assertThat(page2.items()).extracting(PlaceSuggestion::name).containsExactly("코스B " + TOKEN);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull(); // 다음 페이지엔 totalCount 생략
    }

    @Test
    @DisplayName("지역(regions): 첫 페이지에서만 채워지고, 다음 페이지는 빈 목록")
    void 지역_첫페이지만() {
        seedPlaces();

        RelatedSearchResponse page1 = placeQueryService.relatedSearch(request("강남", 20, null));
        assertThat(page1.regions()).contains("서울특별시 강남구");

        // 두 번째 페이지 호출(다른 키워드로 커서 시뮬레이션 불가하니, TOKEN으로 페이지네이션 유발)
        CursorPage<PlaceSuggestion> firstPlacePage =
                placeQueryService.relatedSearch(request(TOKEN, 2, null)).places();
        RelatedSearchResponse page2 =
                placeQueryService.relatedSearch(request(TOKEN, 2, firstPlacePage.nextCursor()));
        assertThat(page2.regions()).isEmpty();
    }

    @Test
    @DisplayName("결과 없으면 지역·장소 모두 빈 목록(200)")
    void 결과_없음() {
        RelatedSearchResponse response =
                placeQueryService.relatedSearch(request("존재하지않는키워드999", 20, null));
        assertThat(response.regions()).isEmpty();
        assertThat(response.places().items()).isEmpty();
        assertThat(response.places().totalCount()).isZero();
    }

    @Test
    @DisplayName("미인증: 토큰 없이 GET /places/related-search 401 (로그인 전용)")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/places/related-search").param("keyword", "강남"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("size 경계: 1·100은 허용, 0·101은 400(BusinessException)")
    void size_경계값() {
        assertThat(request("강남", 1, null)).isNotNull(); // 최소 허용
        assertThat(request("강남", 100, null)).isNotNull(); // 최대 허용
        assertThatThrownBy(() -> request("강남", 0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> request("강남", 101, null)).isInstanceOf(BusinessException.class);
    }
}
