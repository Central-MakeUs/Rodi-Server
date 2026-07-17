package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.dto.CourseDetailResponse;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
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

/** U5: 코스 상세(#3) — 태그·경로점(순서)·북마크수·북마크 여부·타입검증(404)·미인증(401). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceCourseDetailIntegrationTest {

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
        course.addCaution("야간 조명 부족");
        course.addCaution("일반통행골목");
        course.addTag(PracticeType.STRAIGHT);
        course.addTag(PracticeType.LANE_CHANGE);
        course.addWaypoint(WaypointType.START, (short) 0, point(37.51, 127.03), null);
        course.addWaypoint(WaypointType.VIA, (short) 1, point(37.52, 127.04), "경유1");
        course.addWaypoint(WaypointType.DESTINATION, (short) 2, point(37.53, 127.05), null);
        return courseRepository.save(course);
    }

    @Test
    @DisplayName("코스 상세: 태그·경로점(순서)·북마크수·북마크 여부")
    void 코스_상세() throws Exception {
        Course course = seedCourse();
        Member me = memberRepository.save(Member.createBySocial("me@kakao.com"));
        Member other = memberRepository.save(Member.createBySocial("other@kakao.com"));
        bookmarkRepository.save(Bookmark.builder().member(me).place(course).build());

        CourseDetailResponse res = placeQueryService.getCourseDetail(course.getId(), me.getId());

        assertThat(res.name()).isEqualTo("한강 코스");
        assertThat(res.address()).isEqualTo("서울특별시 영등포구");
        assertThat(res.description()).isEqualTo("초보 연습용");
        assertThat(res.cautions()).containsExactly("야간 조명 부족", "일반통행골목"); // 순서 유지
        assertThat(res.distanceMeters()).isEqualTo(2100);
        assertThat(res.practiceTypes())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
        assertThat(res.waypoints())
                .extracting(CourseDetailResponse.WaypointItem::type)
                .containsExactly(
                        WaypointType.START, WaypointType.VIA, WaypointType.DESTINATION); // 순서
        assertThat(res.waypoints().get(1).name()).isEqualTo("경유1");
        assertThat(res.bookmarkCount()).isEqualTo(1);
        assertThat(res.bookmarked()).isTrue();

        // JSON 직렬화 키는 isBookmarked (ApiResponse.isSuccess와 동일 규칙)
        String json = objectMapper.writeValueAsString(res);
        assertThat(json).contains("\"isBookmarked\":true").doesNotContain("\"bookmarked\"");

        // 다른 회원은 북마크 안 함 → bookmarked=false, count는 동일(1)
        CourseDetailResponse asOther =
                placeQueryService.getCourseDetail(course.getId(), other.getId());
        assertThat(asOther.bookmarked()).isFalse();
        assertThat(asOther.bookmarkCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타입 검증: 주차장 id로 코스 상세 조회 시 404")
    void 주차장_id_404() {
        Parking parking =
                parkingRepository.save(
                        Parking.builder().name("주차장").location(point(37.5, 127.0)).build());
        Member me = memberRepository.save(Member.createBySocial("p@kakao.com"));

        assertThatThrownBy(() -> placeQueryService.getCourseDetail(parking.getId(), me.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("미인증: 토큰 없이 코스 상세 조회 시 401")
    void 미인증_401() throws Exception {
        Course course = seedCourse();

        mockMvc.perform(get("/api/v1/places/courses/{placeId}", course.getId()))
                .andExpect(status().isUnauthorized());
    }
}
