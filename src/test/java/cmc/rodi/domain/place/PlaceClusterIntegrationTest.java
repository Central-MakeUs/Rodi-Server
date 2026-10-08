package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberSortService;
import cmc.rodi.domain.place.dto.MarkerClusterRequest;
import cmc.rodi.domain.place.dto.MarkerClusterResponse;
import cmc.rodi.domain.place.dto.MarkerClusterResponse.Cluster;
import cmc.rodi.domain.place.dto.MarkerClusterResponse.Level;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * 지도 마커 클러스터 검색(스펙 021) — 검색 범위·노출 조건·회원 필터·검증을 실제 DB로 검증한다. 다른 테스트와 겹치지 않게 강릉 좌표로 격리하고,
 * {@code @Transactional}로 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class PlaceClusterIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // 강릉 시내 검색 범위
    private static final double SW_LAT = 37.70, SW_LNG = 128.80, NE_LAT = 37.85, NE_LNG = 129.00;

    @Autowired PlaceQueryService placeQueryService;
    @Autowired PlaceRepository placeRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberSortService memberSortService;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private static MarkerClusterRequest searchArea() {
        return new MarkerClusterRequest(SW_LAT, SW_LNG, NE_LAT, NE_LNG);
    }

    /** 범위 안: 교차로 코스 2개·직선 코스 1개·주차장 1개(노출). 범위 안이지만 미승인·삭제 코스, 범위 밖 코스(미노출). */
    private void seed() {
        courseRepository.save(course("강릉-교차로-1", 37.7510, 128.8760, PracticeType.INTERSECTION));
        courseRepository.save(course("강릉-교차로-2", 37.7520, 128.8770, PracticeType.INTERSECTION));
        courseRepository.save(course("강릉-직선", 37.7900, 128.9100, PracticeType.STRAIGHT));
        parkingRepository.save(
                Parking.builder()
                        .name("강릉-주차장")
                        .address("강원특별자치도 강릉시")
                        .location(point(37.7530, 128.8780))
                        .capacity(30)
                        .build());

        Member author = memberRepository.save(Member.createBySocial("cluster-author@test.com"));
        courseRepository.save(
                Course.register(
                        "강릉-미승인", null, "강원특별자치도 강릉시", point(37.7540, 128.8790), 1000, author));
        Course deleted = course("강릉-삭제", 37.7550, 128.8800, PracticeType.INTERSECTION);
        deleted.delete(LocalDateTime.now());
        courseRepository.save(deleted);

        courseRepository.save(course("속초-범위밖", 38.2070, 128.5918, PracticeType.INTERSECTION));
    }

    private static Course course(String name, double lat, double lng, PracticeType tag) {
        Course course =
                Course.builder()
                        .name(name)
                        .address("강원특별자치도 강릉시")
                        .location(point(lat, lng))
                        .build();
        course.addTag(tag);
        return course;
    }

    private static List<String> names(MarkerClusterResponse response) {
        return response.places().stream().map(PlaceCoordinateResponse::name).toList();
    }

    @Test
    @DisplayName("검색 범위 안의 노출 장소만 — 미승인·삭제 코스와 범위 밖 장소는 없다")
    void 범위_노출_조건() {
        seed();

        MarkerClusterResponse response = placeQueryService.getClusters(searchArea(), null);

        assertThat(names(response))
                .containsExactlyInAnyOrder("강릉-교차로-1", "강릉-교차로-2", "강릉-직선", "강릉-주차장");
    }

    @Test
    @DisplayName("기본 11단계이고, 모든 단계에서 클러스터 count 합 + 개별 장소 수 = 장소 수")
    void 단계_개수_불변() {
        seed();

        MarkerClusterResponse response = placeQueryService.getClusters(searchArea(), null);

        assertThat(response.levels()).hasSize(11);
        for (Level level : response.levels()) {
            int clustered = level.clusters().stream().mapToInt(Cluster::count).sum();
            assertThat(clustered + level.placeIds().size()).isEqualTo(response.places().size());
        }
        // 묶지 않는 단계는 장소 목록 전체
        assertThat(response.levels().get(10).placeIds())
                .containsExactlyElementsOf(
                        response.places().stream().map(PlaceCoordinateResponse::id).toList());
    }

    @Test
    @DisplayName("장소 수는 같은 범위 목록의 전체 개수(countInViewport)와 같다")
    void 목록_개수_일치() {
        seed();

        MarkerClusterResponse response = placeQueryService.getClusters(searchArea(), null);

        assertThat((long) response.places().size())
                .isEqualTo(
                        placeRepository.countInViewport(
                                SW_LAT, SW_LNG, NE_LAT, NE_LNG, false, List.of("__UNUSED__"), 0));
    }

    @Test
    @DisplayName("회원 필터: 필터에 맞는 장소만 — 코스는 태그 겹침, 주차장은 PARKING 포함 시")
    void 회원_필터() {
        seed();
        Member member = memberRepository.save(Member.createBySocial("cluster-filter@test.com"));

        memberSortService.updateSortTags(member.getId(), List.of(PracticeType.INTERSECTION));
        assertThat(names(placeQueryService.getClusters(searchArea(), member.getId())))
                .containsExactlyInAnyOrder("강릉-교차로-1", "강릉-교차로-2");

        memberSortService.updateSortTags(
                member.getId(), List.of(PracticeType.STRAIGHT, PracticeType.PARKING));
        assertThat(names(placeQueryService.getClusters(searchArea(), member.getId())))
                .containsExactlyInAnyOrder("강릉-직선", "강릉-주차장");
    }

    @Test
    @DisplayName("빈 필터는 비로그인과 같다(전부)")
    void 빈_필터() {
        seed();
        Member member = memberRepository.save(Member.createBySocial("cluster-empty@test.com"));

        assertThat(placeQueryService.getClusters(searchArea(), member.getId()))
                .isEqualTo(placeQueryService.getClusters(searchArea(), null));
    }

    @Test
    @DisplayName("결정적: 같은 요청은 같은 응답")
    void 결정적() {
        seed();

        assertThat(placeQueryService.getClusters(searchArea(), null))
                .isEqualTo(placeQueryService.getClusters(searchArea(), null));
    }

    @Test
    @DisplayName("API: 토큰 없이 200, places와 levels를 준다")
    void API_공개() throws Exception {
        seed();

        mockMvc.perform(
                        get("/api/v1/places/clusters")
                                .param("swLat", String.valueOf(SW_LAT))
                                .param("swLng", String.valueOf(SW_LNG))
                                .param("neLat", String.valueOf(NE_LAT))
                                .param("neLng", String.valueOf(NE_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places.length()").value(4))
                .andExpect(jsonPath("$.data.levels.length()").value(11))
                .andExpect(jsonPath("$.data.levels[0].maxLngSpan").doesNotExist())
                .andExpect(jsonPath("$.data.levels[10].minLngSpan").value(0.0));
    }

    @Test
    @DisplayName("API: 범위 파라미터가 빠지면 400")
    void API_파라미터_누락() throws Exception {
        mockMvc.perform(
                        get("/api/v1/places/clusters")
                                .param("swLat", "37.70")
                                .param("swLng", "128.80")
                                .param("neLat", "37.85"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @ParameterizedTest(name = "[{index}] sw=({0},{1}) ne=({2},{3})")
    @CsvSource({
        "37.85, 128.80, 37.70, 129.00", // 남서 위도 ≥ 북동 위도
        "37.70, 129.00, 37.85, 128.80", // 남서 경도 ≥ 북동 경도
        "37.70, 128.80, 37.70, 129.00", // 넓이 0
        "-86.0, 128.80, 37.85, 129.00", // Web Mercator 범위 밖
        "37.70, -181.0, 37.85, 129.00" // 경도 범위 밖
    })
    @DisplayName("요청 검증: 위도 ±85·경도 ±180 밖이거나 남서가 북동 이상이면 400")
    void 요청_검증(double swLat, double swLng, double neLat, double neLng) throws Exception {
        assertThatThrownBy(() -> new MarkerClusterRequest(swLat, swLng, neLat, neLng))
                .isInstanceOf(BusinessException.class);

        mockMvc.perform(
                        get("/api/v1/places/clusters")
                                .param("swLat", String.valueOf(swLat))
                                .param("swLng", String.valueOf(swLng))
                                .param("neLat", String.valueOf(neLat))
                                .param("neLng", String.valueOf(neLng)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("Swagger: 클러스터 API가 문서에 있고, 좌표 API는 deprecated로 표시된다")
    void Swagger_문서() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/places/clusters'].get").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/places/coordinates'].get.deprecated")
                                .value(true));
    }
}
