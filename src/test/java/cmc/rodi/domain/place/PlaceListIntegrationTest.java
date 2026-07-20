package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * U4: 현위치 목록(#2) — 뷰포트 bbox 필터·거리순·커서 2페이지·totalCount·코스/주차장 폴리모픽을 실제 DB로 검증. 다른 테스트(서울 좌표)와 겹치지 않게
 * 부산 좌표로 격리한다. {@code @Transactional}로 각 테스트를 롤백해 seed()가 누적되지 않게 한다(totalCount가 실행 순서에 무관).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
                        .address("부산광역시 강서구")
                        .location(point(35.151, 129.051))
                        .distanceMeters(1500)
                        .build();
        near.addTag(PracticeType.STRAIGHT);
        near.addTag(PracticeType.LANE_CHANGE);
        courseRepository.save(near);

        parkingRepository.save(
                Parking.builder()
                        .name("부산-중간주차장")
                        .address("부산광역시 남구")
                        .location(point(35.16, 129.06))
                        .capacity(100)
                        .weekdayHours("09:00-18:00")
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
        assertThat(near.address()).isEqualTo("부산광역시 강서구");
        assertThat(near.practiceTypes())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
        assertThat(near.distanceMeters()).isEqualTo(1500);
        assertThat(near.capacity()).isNull(); // 코스엔 주차 필드 없음
        assertThat(near.openTime()).isNull();

        PlaceListItem parking = page1.items().get(1);
        assertThat(parking.type()).isEqualTo(PlaceType.PARKING);
        assertThat(parking.address()).isEqualTo("부산광역시 남구");
        assertThat(parking.practiceTypes()).containsExactly(PracticeType.PARKING); // 항상 주차
        assertThat(parking.capacity()).isEqualTo(100);
        assertThat(parking.openTime()).isEqualTo("09:00"); // "09:00-18:00" → 시작시각
        assertThat(parking.distanceMeters()).isNull(); // 주차장엔 코스 필드 없음
        assertThat(parking.description()).isNull();

        CursorPage<PlaceListItem> page2 =
                placeQueryService.getPlaces(request(2, page1.nextCursor()));
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("부산-먼코스");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull(); // 다음 페이지엔 totalCount 생략
    }

    @Test
    @DisplayName("잘못된 입력(size·좌표 범위·sw>ne)은 400(BusinessException)")
    void 잘못된_입력() {
        // size 범위 밖
        assertThatThrownBy(() -> request(0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> request(101, null)).isInstanceOf(BusinessException.class);
        // 위도 범위 밖
        assertThatThrownBy(
                        () -> new PlaceListRequest(35.1, 129.0, 35.2, 129.1, 200, 129.05, 20, null))
                .isInstanceOf(BusinessException.class);
        // 남서 > 북동
        assertThatThrownBy(
                        () ->
                                new PlaceListRequest(
                                        35.3, 129.0, 35.2, 129.1, 35.15, 129.05, 20, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("변조 커서(거리값 비정상)는 INVALID_INPUT_VALUE")
    void 변조_커서() {
        // 디코드는 되지만 sortValue가 숫자가 아닌 커서 → 400(파싱 예외가 500으로 새지 않음)
        String tampered = CursorCodec.encode("abc", 1L);
        assertThatThrownBy(() -> placeQueryService.getPlaces(request(2, tampered)))
                .isInstanceOf(BusinessException.class);
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
