package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
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
 * 검색(#스펙 007) — 주소 부분 일치·거리순 커서 2페이지·totalCount·와일드카드 이스케이프·검증을 실제 DB로 검증. 다른 테스트(서울·부산 좌표)와 겹치지 않게
 * 대구 주소("대구광역시 …")로 격리한다. {@code @Transactional}로 각 테스트를 롤백해 seed()가 누적되지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class PlaceSearchIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    // 대구 인근 현위치(거리 정렬 기준)
    private static final double ME_LAT = 35.87, ME_LNG = 128.60;

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired MockMvc mockMvc;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private PlaceSearchRequest request(String keyword, int size, String cursor) {
        return new PlaceSearchRequest(keyword, ME_LAT, ME_LNG, size, cursor);
    }

    private void seed() {
        Course near =
                Course.builder()
                        .name("대구-가까운코스")
                        .address("대구광역시 수성구")
                        .location(point(35.871, 128.601))
                        .distanceMeters(1200)
                        .build();
        near.addTag(PracticeType.STRAIGHT);
        courseRepository.save(near);

        parkingRepository.save(
                Parking.builder()
                        .name("대구-중간주차장")
                        .address("대구광역시 달서구")
                        .location(point(35.88, 128.61))
                        .capacity(80)
                        .weekdayHours("08:00-20:00")
                        .build());
        courseRepository.save(
                Course.builder()
                        .name("대구-먼코스")
                        .address("대구광역시 동구")
                        .location(point(35.93, 128.66))
                        .distanceMeters(2500)
                        .build());
        // 다른 지역 → 검색 결과에서 제외돼야 함
        courseRepository.save(
                Course.builder()
                        .name("광주-코스")
                        .address("광주광역시 서구")
                        .location(point(35.15, 126.85))
                        .build());
        // 주소 없는 place → 매칭 안 됨
        courseRepository.save(
                Course.builder().name("무주소-코스").location(point(35.872, 128.602)).build());
    }

    @Test
    @DisplayName("주소 부분 일치: '대구' 포함 place만, 거리순 커서 2페이지·totalCount·폴리모픽 아이템")
    void 주소_부분일치_거리순_커서() {
        seed();

        CursorPage<PlaceListItem> page1 = placeQueryService.searchPlaces(request("대구", 2, null));

        assertThat(page1.totalCount()).isEqualTo(3L); // 광주·무주소 제외
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.items())
                .extracting(PlaceListItem::name)
                .containsExactly("대구-가까운코스", "대구-중간주차장");
        assertThat(page1.items().get(0).distanceFromMe())
                .isLessThanOrEqualTo(page1.items().get(1).distanceFromMe());

        PlaceListItem course = page1.items().get(0);
        assertThat(course.type()).isEqualTo(PlaceType.COURSE);
        assertThat(course.address()).isEqualTo("대구광역시 수성구");
        assertThat(course.practiceTypes()).containsExactly(PracticeType.STRAIGHT);
        assertThat(course.distanceMeters()).isEqualTo(1200);

        PlaceListItem parking = page1.items().get(1);
        assertThat(parking.type()).isEqualTo(PlaceType.PARKING);
        assertThat(parking.practiceTypes()).containsExactly(PracticeType.PARKING);
        assertThat(parking.capacity()).isEqualTo(80);
        assertThat(parking.openTime()).isEqualTo("08:00");

        CursorPage<PlaceListItem> page2 =
                placeQueryService.searchPlaces(request("대구", 2, page1.nextCursor()));
        assertThat(page2.items()).extracting(PlaceListItem::name).containsExactly("대구-먼코스");
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(page2.totalCount()).isNull(); // 다음 페이지엔 totalCount 생략
    }

    @Test
    @DisplayName("시군구 단위 검색: '수성구'는 해당 구만 매칭")
    void 시군구_검색() {
        seed();

        CursorPage<PlaceListItem> page = placeQueryService.searchPlaces(request("수성구", 20, null));

        assertThat(page.items()).extracting(PlaceListItem::name).containsExactly("대구-가까운코스");
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("결과 없으면 빈 목록(200)·totalCount 0")
    void 결과_없음() {
        seed();

        CursorPage<PlaceListItem> page = placeQueryService.searchPlaces(request("제주", 20, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    @DisplayName("와일드카드 이스케이프: '%'·'_' 키워드가 전체 매칭으로 새지 않는다")
    void 와일드카드_이스케이프() {
        seed();

        assertThat(placeQueryService.searchPlaces(request("%", 20, null)).items()).isEmpty();
        assertThat(placeQueryService.searchPlaces(request("_", 20, null)).items()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 입력(키워드 공백·51자·좌표 범위·size)은 400(BusinessException)")
    void 잘못된_입력() {
        // 키워드 없음·공백만·길이 초과
        assertThatThrownBy(() -> request(null, 20, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> request("   ", 20, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> request("가".repeat(51), 20, null))
                .isInstanceOf(BusinessException.class);
        // 좌표 범위 밖
        assertThatThrownBy(() -> new PlaceSearchRequest("대구", 200, 128.60, 20, null))
                .isInstanceOf(BusinessException.class);
        // size 범위 밖
        assertThatThrownBy(() -> request("대구", 0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> request("대구", 101, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("트림: 키워드 앞뒤 공백은 제거 후 매칭·50자 경계 허용")
    void 키워드_트림() {
        seed();

        CursorPage<PlaceListItem> page = placeQueryService.searchPlaces(request(" 대구 ", 20, null));
        assertThat(page.totalCount()).isEqualTo(3L);

        // 트림 후 50자는 허용(경계값)
        assertThat(request("가".repeat(50), 20, null)).isNotNull();
    }

    @Test
    @DisplayName("공개: 인증 없이 GET /places/search 200")
    void 공개_접근() throws Exception {
        seed();

        mockMvc.perform(
                        get("/api/v1/places/search")
                                .param("keyword", "대구")
                                .param("lat", String.valueOf(ME_LAT))
                                .param("lng", String.valueOf(ME_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalCount").value(3));
    }
}
