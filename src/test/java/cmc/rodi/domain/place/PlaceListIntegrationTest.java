package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
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

/**
 * U4: 현위치 목록(#2) — 뷰포트 bbox 필터·거리순·커서 2페이지·totalCount·코스/주차장 폴리모픽을 실제 DB로 검증. 다른 테스트(서울 좌표)와 겹치지 않게
 * 부산 좌표로 격리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceListIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // 부산 인근 뷰포트 + 현위치
    private static final double SW_LAT = 35.10, SW_LNG = 129.00, NE_LAT = 35.25, NE_LNG = 129.15;
    private static final double ME_LAT = 35.15, ME_LNG = 129.05;

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private PlaceListRequest request(int size, String cursor) {
        return new PlaceListRequest(SW_LAT, SW_LNG, NE_LAT, NE_LNG, ME_LAT, ME_LNG, size, cursor);
    }

    private void seed() {
        Course near =
                Course.builder()
                        .name("부산-가까운코스")
                        .location(point(35.151, 129.051))
                        .distanceMeters(1500)
                        .build();
        near.addTag(PracticeType.STRAIGHT);
        near.addTag(PracticeType.LANE_CHANGE);
        courseRepository.save(near);

        parkingRepository.save(
                Parking.builder()
                        .name("부산-중간주차장")
                        .location(point(35.16, 129.06))
                        .capacity(100)
                        .build());
        courseRepository.save(
                Course.builder()
                        .name("부산-먼코스")
                        .location(point(35.20, 129.10))
                        .distanceMeters(3000)
                        .build());
        // 뷰포트 밖(neLat 초과) → 목록·totalCount에서 제외돼야 함
        courseRepository.save(
                Course.builder().name("부산-밖코스").location(point(35.30, 129.30)).build());
    }

    @Test
    @DisplayName("거리순 커서: 2페이지 연속성·totalCount·bbox 제외·폴리모픽 아이템")
    void 거리순_커서() {
        seed();

        CursorPage<PlaceListItem> page1 = placeQueryService.getPlaces(request(2, null));

        assertThat(page1.totalCount()).isEqualTo(3L); // 첫 페이지에만 채워짐, 밖코스 제외
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("부산-가까운코스", "부산-중간주차장");
        assertThat(page1.nextCursor()).isNotNull();
        assertThat(page1.items().get(0).distanceFromMe())
                .isLessThanOrEqualTo(page1.items().get(1).distanceFromMe());

        PlaceListItem near = page1.items().get(0);
        assertThat(near.type()).isEqualTo(PlaceType.COURSE);
        assertThat(near.tags())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
        assertThat(near.distanceMeters()).isEqualTo(1500);

        PlaceListItem parking = page1.items().get(1);
        assertThat(parking.type()).isEqualTo(PlaceType.PARKING);
        assertThat(parking.tags()).isNull();
        assertThat(parking.distanceMeters()).isNull();

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(request(2, page1.nextCursor()));
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("부산-먼코스");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull(); // 다음 페이지엔 totalCount 생략
    }

    @Test
    @DisplayName("공개: 인증 없이 GET /places 200")
    void 공개_접근() throws Exception {
        seed();

        mockMvc.perform(
                        get("/api/v1/places")
                                .param("swLat", String.valueOf(SW_LAT))
                                .param("swLng", String.valueOf(SW_LNG))
                                .param("neLat", String.valueOf(NE_LAT))
                                .param("neLng", String.valueOf(NE_LNG))
                                .param("lat", String.valueOf(ME_LAT))
                                .param("lng", String.valueOf(ME_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
