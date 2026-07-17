package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseDetail;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.entity.WaypointType;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 장소 상세 통합(#3·#4) — placeId로 조회 후 타입별 블록(course/parking) 응답·북마크·404·401·경로 충돌 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceDetailIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired PlaceQueryService placeQueryService;
    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    private Course seedCourse() {
        Course course =
                Course.builder()
                        .name("한강 코스")
                        .address("서울특별시 영등포구")
                        .description("초보 연습용")
                        .location(point(37.51, 127.03))
                        .distanceMeters(2100)
                        .build();
        course.addCaution("일반통행골목");
        course.addCaution("비보호좌회전");
        course.addTag(PracticeType.STRAIGHT);
        course.addTag(PracticeType.LANE_CHANGE);
        course.addWaypoint(WaypointType.START, (short) 0, point(37.51, 127.03), null);
        course.addWaypoint(WaypointType.VIA, (short) 1, point(37.52, 127.04), "경유1");
        course.addWaypoint(WaypointType.DESTINATION, (short) 2, point(37.53, 127.05), null);
        return courseRepository.save(course);
    }

    @Test
    @DisplayName("코스 상세: course 블록 채워지고 parking은 null")
    void 코스_상세() throws Exception {
        Course course = seedCourse();
        Member me = memberRepository.save(Member.createBySocial("me@kakao.com"));
        bookmarkRepository.save(Bookmark.builder().member(me).place(course).build());

        PlaceDetailResponse res = placeQueryService.getPlaceDetail(course.getId(), me.getId());

        // 공통
        assertThat(res.type()).isEqualTo(PlaceType.COURSE);
        assertThat(res.name()).isEqualTo("한강 코스");
        assertThat(res.address()).isEqualTo("서울특별시 영등포구");
        assertThat(res.lat()).isEqualTo(37.51);
        assertThat(res.lng()).isEqualTo(127.03);
        assertThat(res.bookmarkCount()).isEqualTo(1);
        assertThat(res.bookmarked()).isTrue();

        // 코스 블록
        assertThat(res.parking()).isNull();
        CourseDetail c = res.course();
        assertThat(c.description()).isEqualTo("초보 연습용");
        assertThat(c.cautions()).containsExactly("일반통행골목", "비보호좌회전"); // 순서 유지
        assertThat(c.practiceTypes())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
        assertThat(c.distanceMeters()).isEqualTo(2100);
        assertThat(c.waypoints())
                .extracting(CourseDetail.WaypointItem::type)
                .containsExactly(WaypointType.START, WaypointType.VIA, WaypointType.DESTINATION);
        assertThat(c.waypoints().get(1).name()).isEqualTo("경유1");

        // JSON 키
        String json = objectMapper.writeValueAsString(res);
        assertThat(json).contains("\"isBookmarked\":true").doesNotContain("\"bookmarked\"");
    }

    @Test
    @DisplayName("주차장 상세: parking 블록(feeInfo·operatingHours 묶음) 채워지고 course는 null")
    void 주차장_상세() {
        Parking parking =
                parkingRepository.save(
                        Parking.builder()
                                .name("세종로 공영")
                                .address("서울특별시 종로구")
                                .location(point(37.5734, 126.9759))
                                .lotAddress("서울특별시 종로구 세종로 80-1(지하)")
                                .parkingType("노외")
                                .capacity(1260)
                                .isFree(false)
                                .hasAccessibleSpace(true)
                                .baseMinutes(5)
                                .baseFee(430)
                                .monthlyFee(176000)
                                .weekdayHours("00:00-23:59")
                                .saturdayHours("00:00-23:59")
                                .holidayHours("00:00-23:59")
                                .build());
        Member me = memberRepository.save(Member.createBySocial("p@kakao.com"));

        PlaceDetailResponse res = placeQueryService.getPlaceDetail(parking.getId(), me.getId());

        assertThat(res.type()).isEqualTo(PlaceType.PARKING);
        assertThat(res.address()).isEqualTo("서울특별시 종로구");
        assertThat(res.bookmarkCount()).isZero();
        assertThat(res.bookmarked()).isFalse();

        assertThat(res.course()).isNull();
        assertThat(res.parking().capacity()).isEqualTo(1260);
        assertThat(res.parking().parkingType()).isEqualTo("노외");
        assertThat(res.parking().free()).isFalse();
        assertThat(res.parking().hasAccessibleSpace()).isTrue();
        assertThat(res.parking().feeInfo().baseFee()).isEqualTo(430);
        assertThat(res.parking().feeInfo().monthlyFee()).isEqualTo(176000);
        assertThat(res.parking().operatingHours().weekday()).isEqualTo("00:00-23:59");
    }

    @Test
    @DisplayName("isFree: 요금 혼합(null)이면 false")
    void 요금_혼합은_무료아님() {
        Parking mixed =
                parkingRepository.save(
                        Parking.builder()
                                .name("혼합요금")
                                .location(point(37.4, 127.1))
                                .isFree(null) // 무료·유료 혼합
                                .build());
        Member me = memberRepository.save(Member.createBySocial("mix@kakao.com"));

        PlaceDetailResponse res = placeQueryService.getPlaceDetail(mixed.getId(), me.getId());

        assertThat(res.parking().free()).isFalse(); // 무료만 true
    }

    @Test
    @DisplayName("없는 id면 404")
    void 없는_id_404() {
        Member me = memberRepository.save(Member.createBySocial("none@kakao.com"));

        assertThatThrownBy(() -> placeQueryService.getPlaceDetail(999_999L, me.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("미인증: 토큰 없이 장소 상세 조회 시 401")
    void 미인증_401() throws Exception {
        Course course = seedCourse();

        mockMvc.perform(get("/api/v1/places/{placeId}", course.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("경로 충돌 없음: /places/coordinates는 여전히 공개 200")
    void 좌표_경로_충돌없음() throws Exception {
        mockMvc.perform(get("/api/v1/places/coordinates")).andExpect(status().isOk());
    }
}
