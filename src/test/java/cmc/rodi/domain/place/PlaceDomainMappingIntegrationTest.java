package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Bookmark;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.PlaceType;
import cmc.rodi.domain.place.entity.WaypointType;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.place.repository.ParkingRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** U1: place 도메인 매핑(JOINED 상속·Point 4326·태그·waypoint·bookmark)이 실제 DB에 정상 저장/조회되는지 검증. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlaceDomainMappingIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired CourseRepository courseRepository;
    @Autowired ParkingRepository parkingRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired MemberRepository memberRepository;

    @PersistenceContext EntityManager em;

    /** lat/lng → Point(SRID 4326). PostGIS는 (x=경도, y=위도) 순서. */
    private static Point point(double lat, double lng) {
        return GEO.createPoint(new Coordinate(lng, lat));
    }

    @Test
    @Transactional
    @DisplayName("코스: JOINED 상속·Point·태그·waypoint(순서) 저장/조회")
    void 코스_매핑() {
        Course course =
                Course.builder()
                        .name("한강 코스")
                        .description("초보 연습용")
                        .location(point(37.51, 127.03))
                        .distanceMeters(2100)
                        .build();
        course.addCaution("야간 조명 부족");
        course.addTag(PracticeType.STRAIGHT);
        course.addTag(PracticeType.LANE_CHANGE);
        course.addWaypoint(WaypointType.START, (short) 0, point(37.51, 127.03), null);
        course.addWaypoint(WaypointType.VIA, (short) 1, point(37.52, 127.04), "경유1");
        course.addWaypoint(WaypointType.DESTINATION, (short) 2, point(37.53, 127.05), null);

        Long id = courseRepository.save(course).getId();
        em.flush();
        em.clear(); // 영속성 컨텍스트 비워 실제 DB에서 다시 로드

        Course found = courseRepository.findById(id).orElseThrow();
        assertThat(found.getPlaceType()).isEqualTo(PlaceType.COURSE);
        assertThat(found.getLocation().getY()).isCloseTo(37.51, within(1e-6)); // 위도
        assertThat(found.getLocation().getX()).isCloseTo(127.03, within(1e-6)); // 경도
        assertThat(found.getDistanceMeters()).isEqualTo(2100);
        assertThat(found.getTags())
                .containsExactlyInAnyOrder(PracticeType.STRAIGHT, PracticeType.LANE_CHANGE);
        assertThat(found.getWaypoints())
                .extracting(w -> w.getWaypointType())
                .containsExactly(WaypointType.START, WaypointType.VIA, WaypointType.DESTINATION);
    }

    @Test
    @DisplayName("주차장: 개별 컬럼 저장/조회")
    void 주차장_매핑() {
        Parking parking =
                Parking.builder()
                        .name("세종로 공영")
                        .location(point(37.5734, 126.9759))
                        .lotAddress("서울특별시 종로구 세종로 80-1(지하)")
                        .parkingType("노외")
                        .capacity(1260)
                        .isFree(false)
                        .paymentMethods("카드")
                        .baseMinutes(5)
                        .baseFee(430)
                        .monthlyFee(176000)
                        .weekdayHours("00:00-23:59")
                        .saturdayHours("00:00-23:59")
                        .holidayHours("00:00-23:59")
                        .build();

        Long id = parkingRepository.save(parking).getId();

        Parking found = parkingRepository.findById(id).orElseThrow();
        assertThat(found.getPlaceType()).isEqualTo(PlaceType.PARKING);
        assertThat(found.getCapacity()).isEqualTo(1260);
        assertThat(found.getBaseFee()).isEqualTo(430);
        assertThat(found.getWeekdayHours()).isEqualTo("00:00-23:59");
        assertThat(found.getIsFree()).isFalse();
    }

    @Test
    @DisplayName("북마크: 회원↔place 저장/조회")
    void 북마크_매핑() {
        Member member = memberRepository.save(Member.createBySocial("bookmark@kakao.com"));
        Course course =
                courseRepository.save(
                        Course.builder().name("코스").location(point(37.5, 127.0)).build());

        Bookmark saved =
                bookmarkRepository.save(Bookmark.builder().member(member).place(course).build());

        Bookmark found = bookmarkRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPlace().getId()).isEqualTo(course.getId());
        assertThat(found.getMember().getId()).isEqualTo(member.getId());
    }
}
